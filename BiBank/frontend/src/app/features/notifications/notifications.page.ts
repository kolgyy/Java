import { ChangeDetectionStrategy, Component, effect, inject, signal, untracked } from '@angular/core';
import { NotificationsApi } from '../../core/api/notifications.api';
import { errorMessage } from '../../core/errors';
import { AppNotification, Page } from '../../core/models';
import { DateTimePipe } from '../../shared/format.pipes';
import { IconComponent } from '../../shared/icon.component';
import { PageHeaderComponent, PagerComponent, StateComponent } from '../../shared/ui.components';

const LEVEL_ICON: Record<string, string> = { INFO: 'info', SUCCESS: 'check', WARNING: 'alert', ERROR: 'x' };

@Component({
  selector: 'app-notifications-page',
  imports: [IconComponent, DateTimePipe, PageHeaderComponent, PagerComponent, StateComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styles: `
    .n { display: flex; gap: 14px; padding: 14px 6px; border-bottom: 1px solid var(--border); cursor: pointer; border-radius: 8px; }
    .n:last-child { border-bottom: none; }
    .n:hover { background: var(--surface-2); }
    .n--unread .n__title::after { content: ''; display: inline-block; width: 8px; height: 8px; border-radius: 50%; background: var(--primary); margin-left: 8px; vertical-align: middle; }
    .n:not(.n--unread) { opacity: .72; }
    .n__icon { width: 38px; height: 38px; border-radius: 12px; display: flex; align-items: center; justify-content: center; flex-shrink: 0; }
    .n__icon--INFO { background: var(--primary-50); color: var(--primary); }
    .n__icon--SUCCESS { background: var(--success-50); color: var(--success); }
    .n__icon--WARNING { background: var(--warning-50); color: var(--warning); }
    .n__icon--ERROR { background: var(--danger-50); color: var(--danger); }
    .n__title { font-weight: 600; }
  `,
  template: `
    <app-page-header title="Уведомления" subtitle="События по счетам, переводам, кредитам и вкладам">
      <div class="segmented">
        <button [class.active]="!unreadOnly()" (click)="setFilter(false)">Все</button>
        <button [class.active]="unreadOnly()" (click)="setFilter(true)">Непрочитанные</button>
      </div>
      <button class="btn btn--ghost" [disabled]="api.unread() === 0" (click)="readAll()">
        <app-icon name="check" [size]="18" /> Прочитать все
      </button>
    </app-page-header>

    <div class="card">
      <app-state [loading]="loading()" [error]="error()" (retry)="load()"
                 [empty]="!loading() && !error() && !data()?.content?.length"
                 [emptyTitle]="unreadOnly() ? 'Все уведомления прочитаны' : 'Уведомлений пока нет'" emptyIcon="bell" />
      @if (!loading() && !error()) {
        @for (n of data()?.content ?? []; track n.id) {
          <div class="n" [class.n--unread]="!n.read" (click)="read(n)">
            <div class="n__icon" [class]="'n__icon n__icon--' + n.level"><app-icon [name]="icon(n.level)" [size]="18" /></div>
            <div class="grow">
              <div class="row row--between">
                <span class="n__title">{{ n.title }}</span>
                <span class="subtle nowrap">{{ n.createdAt | dt }}</span>
              </div>
              <div class="muted" style="font-size: 14px; margin-top: 2px">{{ n.message }}</div>
            </div>
          </div>
        }
        <app-pager [page]="page()" [totalPages]="data()?.totalPages ?? 0" (change)="page.set($event)" />
      }
    </div>
  `,
})
export class NotificationsPage {
  protected readonly api = inject(NotificationsApi);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly data = signal<Page<AppNotification> | null>(null);
  protected readonly page = signal(0);
  protected readonly unreadOnly = signal(false);
  private readonly tick = signal(0);

  constructor() {
    effect(() => {
      this.tick();
      const page = this.page();
      const unread = this.unreadOnly();
      untracked(() => this.fetch(page, unread));
    });
  }

  protected icon(level: string): string {
    return LEVEL_ICON[level] ?? 'info';
  }

  protected setFilter(unread: boolean): void {
    this.unreadOnly.set(unread);
    this.page.set(0);
  }

  protected load(): void {
    this.tick.update((n) => n + 1);
  }

  private fetch(page: number, unread: boolean): void {
    this.loading.set(!this.data());
    this.error.set(null);
    this.api.list(page, 20, unread).subscribe({
      next: (d) => {
        this.data.set(d);
        this.loading.set(false);
      },
      error: (e) => {
        this.error.set(errorMessage(e));
        this.loading.set(false);
      },
    });
    this.api.refreshUnread().subscribe({ error: () => {} });
  }

  protected read(n: AppNotification): void {
    if (n.read) return;
    this.api.markRead(n.id).subscribe({
      next: (updated) =>
        this.data.update((d) => d && { ...d, content: d.content.map((x) => (x.id === updated.id ? updated : x)) }),
    });
  }

  protected readAll(): void {
    this.api.markAllRead().subscribe({ next: () => this.load() });
  }
}
