import { ChangeDetectionStrategy, Component, effect, inject, signal, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AdminApi } from '../../core/api/admin.api';
import { AuthService } from '../../core/auth/auth.service';
import { errorMessage } from '../../core/errors';
import { ToastService } from '../../core/toast.service';
import { Account, Page, User } from '../../core/models';
import { AccountNumberPipe, DateTimePipe, MoneyPipe } from '../../shared/format.pipes';
import { IconComponent } from '../../shared/icon.component';
import { ModalComponent, PagerComponent, StateComponent, StatusBadgeComponent } from '../../shared/ui.components';

@Component({
  selector: 'app-admin-users',
  imports: [FormsModule, IconComponent, DateTimePipe, MoneyPipe, AccountNumberPipe, StateComponent, StatusBadgeComponent, PagerComponent, ModalComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="card card--pad-0">
      <div class="row row--wrap card__header--pad" style="padding-bottom: 16px">
        <input class="input grow" style="max-width: 360px" placeholder="Поиск по email или имени"
               [ngModel]="search()" (ngModelChange)="search.set($event); page.set(0)" />
        <div class="segmented">
          @for (s of statuses; track s[0]) {
            <button [class.active]="status() === s[0]" (click)="status.set(s[0]); page.set(0)">{{ s[1] }}</button>
          }
        </div>
      </div>
      <app-state [loading]="loading()" [error]="error()" (retry)="reload()" [empty]="!loading() && !error() && !data()?.content?.length"
                 emptyTitle="Пользователи не найдены" emptyIcon="users" />
      @if (!loading() && data()?.content?.length) {
        <div class="table-wrap">
          <table class="table">
            <thead><tr><th>ID</th><th>Клиент</th><th>Роль</th><th>Статус</th><th>Регистрация</th><th>Последний вход</th><th></th></tr></thead>
            <tbody>
              @for (u of data()!.content; track u.id) {
                <tr>
                  <td class="mono">{{ u.id }}</td>
                  <td><b>{{ u.fullName }}</b><div class="subtle">{{ u.email }}</div></td>
                  <td>{{ u.role === 'ROLE_ADMIN' ? 'Админ' : 'Клиент' }}</td>
                  <td><app-status [status]="u.status" /></td>
                  <td class="nowrap">{{ u.createdAt | dt: 'date' }}</td>
                  <td class="nowrap">{{ u.lastLoginAt | dt }}</td>
                  <td class="right nowrap">
                    <button class="btn btn--ghost btn--sm" (click)="showAccounts(u)">Счета</button>
                    @if (u.id !== me()) {
                      @if (u.status === 'ACTIVE') {
                        <button class="btn btn--ghost btn--sm danger-text" (click)="setStatus(u, 'BLOCKED')"><app-icon name="lock" [size]="14" /> Заблокировать</button>
                      } @else {
                        <button class="btn btn--ghost btn--sm" (click)="setStatus(u, 'ACTIVE')"><app-icon name="unlock" [size]="14" /> Разблокировать</button>
                      }
                    }
                  </td>
                </tr>
              }
            </tbody>
          </table>
        </div>
        <div style="padding: 0 16px 12px"><app-pager [page]="page()" [totalPages]="data()!.totalPages" (change)="page.set($event)" /></div>
      }
    </div>

    @if (accountsOf(); as u) {
      <app-modal [title]="'Счета: ' + u.fullName" (closed)="accountsOf.set(null)">
        <app-state [loading]="accountsLoading()" [empty]="!accountsLoading() && accounts().length === 0" emptyTitle="Счетов нет" emptyIcon="wallet" />
        @for (a of accounts(); track a.id) {
          <div class="row row--between" style="padding: 8px 0; border-bottom: 1px solid var(--border)">
            <div><b>{{ a.name }}</b><div class="subtle mono">{{ a.number | accountNumber }}</div></div>
            <div class="right"><b>{{ a.balance | money: a.currency }}</b><div><app-status [status]="a.status" /></div></div>
          </div>
        }
      </app-modal>
    }
  `,
})
export class AdminUsersComponent {
  private readonly api = inject(AdminApi);
  private readonly toast = inject(ToastService);
  private readonly auth = inject(AuthService);

  protected readonly statuses: [string, string][] = [['', 'Все'], ['ACTIVE', 'Активные'], ['BLOCKED', 'Заблокированные']];
  protected readonly search = signal('');
  protected readonly status = signal('');
  protected readonly page = signal(0);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly data = signal<Page<User> | null>(null);
  protected readonly accountsOf = signal<User | null>(null);
  protected readonly accounts = signal<Account[]>([]);
  protected readonly accountsLoading = signal(false);
  private readonly tick = signal(0);
  private timer: ReturnType<typeof setTimeout> | undefined;

  protected me(): number | undefined {
    return this.auth.user()?.id;
  }

  constructor() {
    effect(() => {
      this.tick();
      const args = [this.search().trim(), this.status(), this.page()] as const;
      untracked(() => {
        clearTimeout(this.timer);
        this.timer = setTimeout(() => this.fetch(...args), 250);
      });
    });
  }

  protected reload(): void {
    this.tick.update((n) => n + 1);
  }

  private fetch(search: string, status: string, page: number): void {
    this.loading.set(!this.data());
    this.error.set(null);
    this.api.users(search, status, page).subscribe({
      next: (d) => {
        this.data.set(d);
        this.loading.set(false);
      },
      error: (e) => {
        this.error.set(errorMessage(e));
        this.loading.set(false);
      },
    });
  }

  protected setStatus(u: User, status: 'ACTIVE' | 'BLOCKED'): void {
    this.api.setUserStatus(u.id, status).subscribe({
      next: (updated) => {
        this.toast.success(status === 'BLOCKED' ? `${u.fullName} заблокирован, сессии завершены` : `${u.fullName} разблокирован`);
        this.data.update((d) => d && { ...d, content: d.content.map((x) => (x.id === updated.id ? updated : x)) });
      },
      error: (e) => this.toast.error(errorMessage(e)),
    });
  }

  protected showAccounts(u: User): void {
    this.accountsOf.set(u);
    this.accounts.set([]);
    this.accountsLoading.set(true);
    this.api.userAccounts(u.id).subscribe({
      next: (p) => {
        this.accounts.set(p.content);
        this.accountsLoading.set(false);
      },
      error: (e) => {
        this.accountsLoading.set(false);
        this.toast.error(errorMessage(e));
      },
    });
  }
}
