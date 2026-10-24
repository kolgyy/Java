import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';
import { AppNotification, Page } from '../models';
import { toParams } from './params';

@Injectable({ providedIn: 'root' })
export class NotificationsApi {
  private readonly http = inject(HttpClient);

  /** Счётчик непрочитанных для колокольчика в шапке. */
  readonly unread = signal(0);

  list(page = 0, size = 20, unreadOnly = false): Observable<Page<AppNotification>> {
    return this.http.get<Page<AppNotification>>('/api/notifications', {
      params: toParams({ page, size, unreadOnly }),
    });
  }

  refreshUnread(): Observable<{ count: number }> {
    return this.http
      .get<{ count: number }>('/api/notifications/unread-count')
      .pipe(tap((r) => this.unread.set(r.count)));
  }

  markRead(id: string): Observable<AppNotification> {
    return this.http
      .post<AppNotification>(`/api/notifications/${id}/read`, {})
      .pipe(tap(() => this.unread.update((n) => Math.max(0, n - 1))));
  }

  markAllRead(): Observable<{ updated: number }> {
    return this.http
      .post<{ updated: number }>('/api/notifications/read-all', {})
      .pipe(tap(() => this.unread.set(0)));
  }
}
