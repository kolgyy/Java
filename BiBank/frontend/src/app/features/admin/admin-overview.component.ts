import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { catchError, forkJoin, of } from 'rxjs';
import { AdminApi } from '../../core/api/admin.api';
import { errorMessage } from '../../core/errors';
import { AccountStats, LoanStats, PaymentStats, SystemHealth, UserStats } from '../../core/models';
import { DateTimePipe, MoneyPipe } from '../../shared/format.pipes';
import { IconComponent } from '../../shared/icon.component';
import { OPERATION_TYPES } from '../../shared/labels';
import { StateComponent, StatusBadgeComponent } from '../../shared/ui.components';

const SERVICE_NAMES: Record<string, string> = {
  'auth-service': 'Аутентификация',
  'account-service': 'Счета',
  'payment-service': 'Платежи',
  'loan-service': 'Кредиты и вклады',
  'notification-service': 'Уведомления',
};

@Component({
  selector: 'app-admin-overview',
  imports: [IconComponent, MoneyPipe, DateTimePipe, StateComponent, StatusBadgeComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="stack stack--lg">
      <section class="card stack">
        <div class="row row--between">
          <div class="row">
            <h3>Состояние системы</h3>
            @if (health(); as h) { <app-status [status]="h.status" /> }
          </div>
          <div class="row">
            @if (health(); as h) { <span class="subtle">Проверено {{ h.checkedAt | dt }}</span> }
            <button class="icon-btn" (click)="load()" aria-label="Обновить"><app-icon name="refresh" [size]="18" /></button>
          </div>
        </div>
        @if (health(); as h) {
          <div class="grid grid-3">
            <div class="row" style="padding: 10px 12px; border: 1px solid var(--border); border-radius: 12px">
              <app-icon name="shield" [size]="18" />
              <div class="grow"><b>API Gateway</b><div class="subtle">api-gateway</div></div>
              <app-status [status]="h.gateway" />
            </div>
            @for (s of h.services; track s.name) {
              <div class="row" style="padding: 10px 12px; border: 1px solid var(--border); border-radius: 12px">
                <app-icon name="activity" [size]="18" />
                <div class="grow"><b>{{ serviceName(s.name) }}</b><div class="subtle">{{ s.name }} · {{ s.latencyMs }} мс</div></div>
                <app-status [status]="s.status" />
              </div>
            }
          </div>
        } @else {
          <app-state [loading]="loading()" [error]="healthError()" (retry)="load()" />
        }
      </section>

      <div class="grid grid-4">
        <div class="card stat">
          <span class="stat__label">Клиенты</span>
          <span class="stat__value">{{ users()?.totalUsers ?? '—' }}</span>
          <span class="subtle">+{{ users()?.registeredLast24h ?? 0 }} за сутки · заблокировано {{ users()?.blockedUsers ?? 0 }}</span>
        </div>
        <div class="card stat">
          <span class="stat__label">Счета</span>
          <span class="stat__value">{{ accounts()?.totalAccounts ?? '—' }}</span>
          <span class="subtle">
            @for (c of accounts()?.byCurrency ?? []; track c.currency) { {{ c.currency }}: {{ c.accounts }} &nbsp; }
          </span>
        </div>
        <div class="card stat">
          <span class="stat__label">Операции</span>
          <span class="stat__value">{{ payments()?.totalOperations ?? '—' }}</span>
          <span class="subtle">ошибок {{ payments()?.failed ?? 0 }} · в обработке {{ payments()?.pending ?? 0 }}</span>
        </div>
        <div class="card stat">
          <span class="stat__label">Кредиты / вклады</span>
          <span class="stat__value">{{ loans()?.activeLoans ?? '—' }} / {{ loans()?.activeDeposits ?? '—' }}</span>
          <span class="subtle">заявок на рассмотрении {{ loans()?.applicationsSubmitted ?? 0 }}</span>
        </div>
      </div>

      <div class="grid grid-2" style="align-items: start">
        <section class="card stack">
          <h3>Деньги клиентов по валютам</h3>
          @for (c of accounts()?.byCurrency ?? []; track c.currency) {
            <div class="row row--between"><span>{{ c.currency }}</span><b>{{ c.totalBalance | money: c.currency }}</b></div>
          }
          <h3 style="margin-top: 8px">Объём переводов</h3>
          @for (v of volume(); track v[0]) {
            <div class="row row--between"><span>{{ v[0] }}</span><b>{{ v[1] | money: v[0] }}</b></div>
          } @empty {
            <span class="subtle">Переводов ещё не было</span>
          }
        </section>
        <section class="card stack">
          <h3>Операции по типам</h3>
          @for (t of byType(); track t.type) {
            <div>
              <div class="row row--between" style="margin-bottom: 4px">
                <span>{{ t.label }}</span><span class="subtle">{{ t.total }} · ошибок {{ t.failed }}</span>
              </div>
              <div class="progress"><div class="progress__bar" [style.width.%]="(t.total / maxType()) * 100"></div></div>
            </div>
          } @empty {
            <span class="subtle">Нет данных</span>
          }
        </section>
      </div>
    </div>
  `,
})
export class AdminOverviewComponent {
  private readonly api = inject(AdminApi);
  protected readonly loading = signal(true);
  protected readonly healthError = signal<string | null>(null);
  protected readonly health = signal<SystemHealth | null>(null);
  protected readonly users = signal<UserStats | null>(null);
  protected readonly accounts = signal<AccountStats | null>(null);
  protected readonly payments = signal<PaymentStats | null>(null);
  protected readonly loans = signal<LoanStats | null>(null);

  protected readonly volume = computed(() => Object.entries(this.payments()?.transferVolumeByCurrency ?? {}));
  protected readonly byType = computed(() =>
    Object.entries(this.payments()?.byTypeAndStatus ?? {}).map(([type, statuses]) => ({
      type,
      label: OPERATION_TYPES[type] ?? type,
      total: Object.values(statuses).reduce((a, b) => a + b, 0),
      failed: statuses['FAILED'] ?? 0,
    })),
  );
  protected readonly maxType = computed(() => Math.max(1, ...this.byType().map((t) => t.total)));

  constructor() {
    this.load();
  }

  protected serviceName(name: string): string {
    return SERVICE_NAMES[name] ?? name;
  }

  protected load(): void {
    this.loading.set(true);
    this.healthError.set(null);
    this.api.health().subscribe({
      next: (h) => {
        this.health.set(h);
        this.loading.set(false);
      },
      error: (e) => {
        this.healthError.set(errorMessage(e));
        this.loading.set(false);
      },
    });
    // Статистика собирается из разных сервисов: падение одного не скрывает остальные.
    const safe = <T>(o: import('rxjs').Observable<T>) => o.pipe(catchError(() => of(null)));
    forkJoin({
      users: safe(this.api.userStats()),
      accounts: safe(this.api.accountStats()),
      payments: safe(this.api.paymentStats()),
      loans: safe(this.api.loanStats()),
    }).subscribe((r) => {
      this.users.set(r.users);
      this.accounts.set(r.accounts);
      this.payments.set(r.payments);
      this.loans.set(r.loans);
    });
  }
}
