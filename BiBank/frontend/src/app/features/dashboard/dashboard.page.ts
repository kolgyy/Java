import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { BankingApi } from '../../core/api/banking.api';
import { ProductsApi } from '../../core/api/products.api';
import { AuthService } from '../../core/auth/auth.service';
import { errorMessage } from '../../core/errors';
import { Account, Deposit, ExchangeRates, Loan, Operation } from '../../core/models';
import { AccountTileComponent } from '../../shared/account-tile.component';
import { DateTimePipe, MoneyPipe } from '../../shared/format.pipes';
import { IconComponent } from '../../shared/icon.component';
import { OperationDetailsComponent, OperationItemComponent } from '../../shared/operation.components';
import { StateComponent } from '../../shared/ui.components';
import { OpenAccountComponent } from '../accounts/open-account.component';

@Component({
  selector: 'app-dashboard-page',
  imports: [
    RouterLink, IconComponent, MoneyPipe, DateTimePipe, StateComponent, AccountTileComponent,
    OperationItemComponent, OperationDetailsComponent, OpenAccountComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="stack stack--lg">
      <section class="hero">
        <div class="row row--between row--wrap" style="position: relative; z-index: 1; align-items: flex-end">
          <div>
            <div class="hero__label">{{ greeting() }}, {{ firstName() }}. Всего на счетах</div>
            <div class="hero__value">{{ total() | money: 'RUB' }}</div>
            <div class="hero__sub">
              @for (c of byCurrency(); track c.currency) {
                <span>{{ c.sum | money: c.currency }}</span>
              }
              @if (rates()) {
                <span>Курс BiBank: $ {{ rates()!.rates.USD }} · € {{ rates()!.rates.EUR }}</span>
              }
            </div>
          </div>
          <div class="row row--wrap">
            <a class="btn btn--light" routerLink="/transfer"><app-icon name="send" [size]="18" /> Перевести</a>
            <a class="btn btn--light" routerLink="/transfer" [queryParams]="{ mode: 'topup' }">
              <app-icon name="plus" [size]="18" /> Пополнить
            </a>
          </div>
        </div>
      </section>

      @if (loading() || error()) {
        <div class="card"><app-state [loading]="loading()" [error]="error()" (retry)="load()" /></div>
      } @else {
        <section class="stack">
          <div class="row row--between">
            <h2>Мои счета</h2>
            <a class="link" routerLink="/accounts">Все счета</a>
          </div>
          <div class="grid grid-auto">
            @for (a of activeAccounts(); track a.id) {
              <app-account-tile [account]="a" />
            }
            <button class="card card--link center stack stack--sm" style="justify-content: center; min-height: 150px"
                    (click)="openAccount.set(true)">
              <app-icon name="plus" [size]="28" />
              <span>Открыть счёт</span>
            </button>
          </div>
        </section>

        <div class="grid grid-3" style="align-items: start">
          <section class="card span-2">
            <div class="row row--between" style="margin-bottom: 8px">
              <h3>Последние операции</h3>
              <a class="link" routerLink="/history">Вся история</a>
            </div>
            <app-state [empty]="operations().length === 0" emptyTitle="Операций пока нет"
                       emptyText="Пополните счёт или сделайте первый перевод" emptyIcon="history" />
            <div class="op-list">
              @for (op of operations(); track op.id) {
                <app-operation-item [operation]="op" (opened)="selected.set($event)" />
              }
            </div>
          </section>

          <div class="stack">
            <section class="card stack stack--sm">
              <div class="row row--between">
                <h3>Вклады</h3>
                <a class="link" routerLink="/deposits">Открыть</a>
              </div>
              @if (activeDeposits().length) {
                <div class="stat">
                  <span class="stat__label">Активных: {{ activeDeposits().length }} · начислено процентов</span>
                  <span class="stat__value positive">{{ depositInterest() | money: 'RUB' : true }}</span>
                </div>
                @for (d of activeDeposits().slice(0, 3); track d.id) {
                  <a class="row row--between op" [routerLink]="['/deposits', d.id]" style="text-decoration: none; color: inherit">
                    <span>{{ d.productName }}</span>
                    <b>{{ d.currentValue | money: d.currency }}</b>
                  </a>
                }
              } @else {
                <p class="muted">Ставки до 18% годовых с ежедневным начислением.</p>
              }
            </section>

            <section class="card stack stack--sm">
              <div class="row row--between">
                <h3>Кредиты</h3>
                <a class="link" routerLink="/loans">Подробнее</a>
              </div>
              @if (nextLoan(); as l) {
                <div class="stat">
                  <span class="stat__label">Ближайший платёж {{ l.nextPaymentDate | dt: 'date' }}</span>
                  <span class="stat__value">{{ l.nextPaymentAmount | money: l.currency }}</span>
                </div>
                <a class="btn btn--ghost btn--sm" [routerLink]="['/loans', l.id]">Внести платёж</a>
              } @else {
                <p class="muted">Активных кредитов нет. Рассчитайте платёж в калькуляторе.</p>
              }
            </section>
          </div>
        </div>
      }
    </div>

    @if (selected(); as op) {
      <app-operation-details [operation]="op" (closed)="selected.set(null)" />
    }
    @if (openAccount()) {
      <app-open-account (closed)="openAccount.set(false)" (created)="openAccount.set(false); load()" />
    }
  `,
})
export class DashboardPage {
  private readonly banking = inject(BankingApi);
  private readonly products = inject(ProductsApi);
  private readonly auth = inject(AuthService);

  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly accounts = signal<Account[]>([]);
  protected readonly operations = signal<Operation[]>([]);
  protected readonly deposits = signal<Deposit[]>([]);
  protected readonly loans = signal<Loan[]>([]);
  protected readonly rates = signal<ExchangeRates | null>(null);
  protected readonly selected = signal<Operation | null>(null);
  protected readonly openAccount = signal(false);

  protected readonly firstName = computed(() => this.auth.user()?.fullName.split(' ')[0] ?? '');
  protected readonly greeting = computed(() => {
    const h = new Date().getHours();
    return h < 6 ? 'Доброй ночи' : h < 12 ? 'Доброе утро' : h < 18 ? 'Добрый день' : 'Добрый вечер';
  });
  protected readonly activeAccounts = computed(() => this.accounts().filter((a) => a.status !== 'CLOSED'));
  protected readonly byCurrency = computed(() => {
    const map = new Map<string, number>();
    for (const a of this.activeAccounts()) map.set(a.currency, (map.get(a.currency) ?? 0) + a.balance);
    return [...map.entries()].map(([currency, sum]) => ({ currency, sum }));
  });
  protected readonly total = computed(() => {
    const r = this.rates()?.rates;
    return this.activeAccounts().reduce((s, a) => s + a.balance * (r?.[a.currency] ?? (a.currency === 'RUB' ? 1 : 0)), 0);
  });
  protected readonly activeDeposits = computed(() => this.deposits().filter((d) => d.status === 'ACTIVE'));
  protected readonly depositInterest = computed(() => {
    const r = this.rates()?.rates;
    return this.activeDeposits().reduce((s, d) => s + d.accruedInterest * (r?.[d.currency] ?? 1), 0);
  });
  protected readonly nextLoan = computed(() =>
    this.loans()
      .filter((l) => l.status === 'ACTIVE' && l.nextPaymentDate)
      .sort((a, b) => a.nextPaymentDate!.localeCompare(b.nextPaymentDate!))[0] ?? null,
  );

  constructor() {
    this.load();
  }

  protected load(): void {
    this.loading.set(true);
    this.error.set(null);
    forkJoin({
      accounts: this.banking.accounts(),
      history: this.banking.history({ size: 6 }),
      rates: this.banking.rates(),
    }).subscribe({
      next: (r) => {
        this.accounts.set(r.accounts);
        this.operations.set(r.history.content);
        this.rates.set(r.rates);
        this.loading.set(false);
      },
      error: (e) => {
        this.error.set(errorMessage(e));
        this.loading.set(false);
      },
    });
    // Кредиты и вклады — второстепенные блоки: их недоступность не ломает главную.
    this.products.deposits().subscribe({ next: (d) => this.deposits.set(d), error: () => {} });
    this.products.loans().subscribe({ next: (l) => this.loans.set(l), error: () => {} });
  }
}
