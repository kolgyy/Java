import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { beforeEach, describe, expect, it } from 'vitest';
import { authInterceptor } from './auth.interceptor';
import { AuthService } from './auth.service';

@Component({ template: '' })
class BlankComponent {}

const user = { id: 1, email: 'a@b.c', fullName: 'Анна', role: 'ROLE_USER', status: 'ACTIVE', createdAt: '', lastLoginAt: null };
const tokens = (n: number) => ({ accessToken: `access-${n}`, refreshToken: `refresh-${n}`, tokenType: 'Bearer', expiresIn: 900, user });

describe('authInterceptor', () => {
  let http: HttpClient;
  let backend: HttpTestingController;
  let auth: AuthService;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideRouter([{ path: 'login', component: BlankComponent }]), provideHttpClient(withInterceptors([authInterceptor])), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpClient);
    backend = TestBed.inject(HttpTestingController);
    auth = TestBed.inject(AuthService);
    auth.login('a@b.c', 'x').subscribe();
    backend.expectOne('/api/auth/login').flush(tokens(1));
  });

  it('добавляет access token к запросам API', () => {
    http.get('/api/accounts').subscribe();
    const req = backend.expectOne('/api/accounts');
    expect(req.request.headers.get('Authorization')).toBe('Bearer access-1');
    req.flush([]);
  });

  it('при параллельных 401 обновляет токен один раз и повторяет оба запроса', () => {
    const results: unknown[] = [];
    http.get('/api/accounts').subscribe((r) => results.push(r));
    http.get('/api/transactions').subscribe((r) => results.push(r));

    backend.expectOne('/api/accounts').flush(null, { status: 401, statusText: 'Unauthorized' });
    backend.expectOne('/api/transactions').flush(null, { status: 401, statusText: 'Unauthorized' });

    const refresh = backend.match('/api/auth/refresh');
    expect(refresh.length).toBe(1);
    expect(refresh[0].request.body).toEqual({ refreshToken: 'refresh-1' });
    refresh[0].flush(tokens(2));

    const retried = [backend.expectOne('/api/accounts'), backend.expectOne('/api/transactions')];
    for (const r of retried) {
      expect(r.request.headers.get('Authorization')).toBe('Bearer access-2');
      r.flush({ ok: true });
    }
    expect(results.length).toBe(2);
    backend.verify();
  });

  it('если refresh не удался, завершает сессию', () => {
    let failed = false;
    http.get('/api/accounts').subscribe({ error: () => (failed = true) });
    backend.expectOne('/api/accounts').flush(null, { status: 401, statusText: 'Unauthorized' });
    backend.expectOne('/api/auth/refresh').flush(null, { status: 401, statusText: 'Unauthorized' });
    expect(failed).toBe(true);
    expect(auth.isAuthenticated()).toBe(false);
  });
});
