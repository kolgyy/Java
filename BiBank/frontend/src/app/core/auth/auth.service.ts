import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, finalize, map, shareReplay, tap } from 'rxjs';
import { LoginResponse, User } from '../models';

const STORAGE_KEY = 'bibank.session';

interface Session {
  accessToken: string;
  refreshToken: string;
  /** момент истечения access token, мс */
  expiresAt: number;
  user: User;
}

/**
 * Сессия пользователя: access token (короткоживущий JWT) и одноразовый refresh token.
 * Хранится в localStorage, чтобы пережить перезагрузку страницы.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);

  private readonly session = signal<Session | null>(this.restore());
  private refreshInFlight: Observable<string> | null = null;

  readonly user = computed(() => this.session()?.user ?? null);
  readonly isAuthenticated = computed(() => this.session() !== null);
  readonly isAdmin = computed(() => this.session()?.user.role === 'ROLE_ADMIN');

  get accessToken(): string | null {
    return this.session()?.accessToken ?? null;
  }

  login(email: string, password: string): Observable<User> {
    return this.http
      .post<LoginResponse>('/api/auth/login', { email, password })
      .pipe(tap((r) => this.store(r)), map((r) => r.user));
  }

  register(email: string, password: string, fullName: string): Observable<User> {
    return this.http.post<User>('/api/auth/register', { email, password, fullName });
  }

  /**
   * Обновление токенов. Параллельные запросы, получившие 401, ждут один общий refresh,
   * потому что refresh token одноразовый.
   */
  refresh(): Observable<string> {
    const current = this.session();
    if (!current) {
      throw new Error('No session');
    }
    if (!this.refreshInFlight) {
      this.refreshInFlight = this.http
        .post<LoginResponse>('/api/auth/refresh', { refreshToken: current.refreshToken })
        .pipe(
          tap((r) => this.store(r)),
          map((r) => r.accessToken),
          finalize(() => (this.refreshInFlight = null)),
          shareReplay(1),
        );
    }
    return this.refreshInFlight;
  }

  logout(redirect = true): void {
    const current = this.session();
    if (current) {
      this.http.post('/api/auth/logout', { refreshToken: current.refreshToken }).subscribe({ error: () => {} });
    }
    this.clear();
    if (redirect) {
      this.router.navigate(['/login']);
    }
  }

  /** Сессия истекла: refresh token недействителен. */
  expire(): void {
    this.clear();
    this.router.navigate(['/login'], { queryParams: { expired: 1 } });
  }

  updateUser(user: User): void {
    const current = this.session();
    if (current) {
      this.persist({ ...current, user });
    }
  }

  private store(r: LoginResponse): void {
    this.persist({
      accessToken: r.accessToken,
      refreshToken: r.refreshToken,
      expiresAt: Date.now() + r.expiresIn * 1000,
      user: r.user,
    });
  }

  private persist(s: Session): void {
    this.session.set(s);
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(s));
    } catch {
      /* приватный режим браузера — сессия живёт до перезагрузки */
    }
  }

  private clear(): void {
    this.session.set(null);
    try {
      localStorage.removeItem(STORAGE_KEY);
    } catch {
      /* ignore */
    }
  }

  private restore(): Session | null {
    try {
      const raw = localStorage.getItem(STORAGE_KEY);
      return raw ? (JSON.parse(raw) as Session) : null;
    } catch {
      return null;
    }
  }
}
