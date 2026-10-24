import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { BankingApi } from '../../core/api/banking.api';
import { ProductsApi } from '../../core/api/products.api';
import { errorMessage } from '../../core/errors';
import { ToastService } from '../../core/toast.service';
import { Account, Loan, LoanApplication, LoanProduct } from '../../core/models';
import { DateTimePipe, MoneyPipe } from '../../shared/format.pipes';
import { IconComponent } from '../../shared/icon.component';
import { months } from '../../shared/labels';
import { PageHeaderComponent, StateComponent, StatusBadgeComponent } from '../../shared/ui.components';
import { LoanCalculatorComponent } from './loan-calculator.component';

type Tab = 'loans' | 'new' | 'applications';

@Component({
  selector: 'app-loans-page',
  imports: [RouterLink, IconComponent, MoneyPipe, DateTimePipe, PageHeaderComponent, StateComponent, StatusBadgeComponent, LoanCalculatorComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-page-header title="Кредиты" subtitle="Аннуитетные платежи, досрочное погашение без комиссии" />

    <div class="tabs">
      <button [class.active]="tab() === 'loans'" (click)="tab.set('loans')">Мои кредиты</button>
      <button [class.active]="tab() === 'new'" (click)="tab.set('new')">Взять кредит</button>
      <button [class.active]="tab() === 'applications'" (click)="tab.set('applications')">
        Заявки @if (pendingApps()) { <span class="badge badge--warning">{{ pendingApps() }}</span> }
      </button>
    </div>

    <app-state [loading]="loading()" [error]="error()" (retry)="load()" />

    @if (!loading() && !error()) {
      @switch (tab()) {
        @case ('loans') {
          @if (loans().length === 0) {
            <div class="card">
              <app-state [empty]="true" emptyTitle="У вас нет кредитов" emptyText="Рассчитайте платёж и подайте заявку онлайн" emptyIcon="credit">
                <button class="btn" (click)="tab.set('new')">Рассчитать кредит</button>
              </app-state>
            </div>
          }
          <div class="grid grid-auto">
            @for (l of loans(); track l.id) {
              <a class="card card--link stack stack--sm" [routerLink]="['/loans', l.id]" style="color: inherit">
                <div class="row row--between"><b>{{ l.productName }}</b><app-status [status]="l.status" /></div>
                <div class="stat">
                  <span class="stat__label">Остаток долга</span>
                  <span class="stat__value">{{ l.outstandingPrincipal | money: l.currency }}</span>
                </div>
                <div class="subtle">{{ l.principal | money: l.currency }} · {{ l.annualRate }}% · {{ termLabel(l.termMonths) }}</div>
                @if (l.status === 'ACTIVE') {
                  <div class="progress"><div class="progress__bar" [style.width.%]="(l.paidInstallments / l.termMonths) * 100"></div></div>
                  <div class="row row--between subtle">
                    <span>Платёж {{ l.nextPaymentDate | dt: 'date' }}</span><b>{{ l.nextPaymentAmount | money: l.currency }}</b>
                  </div>
                }
              </a>
            }
          </div>
        }
        @case ('new') {
          <div class="stack stack--lg">
            <div class="grid grid-4">
              @for (p of products(); track p.id) {
                <button class="card card--link stack stack--sm" style="text-align: left; font: inherit"
                        [style.border-color]="selected()?.id === p.id ? 'var(--primary)' : null" (click)="selected.set(p)">
                  <div class="row row--between"><b>{{ p.name }}</b><span class="chip">{{ p.currency }}</span></div>
                  <span class="stat__value" style="color: var(--primary)">{{ p.annualRate }}%</span>
                  <span class="subtle">до {{ p.maxAmount | money: p.currency }} · до {{ p.maxTermMonths }} мес.</span>
                  <span class="muted" style="font-size: 13px">{{ p.description }}</span>
                </button>
              }
            </div>
            @if (selected(); as p) {
              <app-loan-calculator [product]="p" [accounts]="accounts()" (applied)="onApplied($event)" />
            }
          </div>
        }
        @case ('applications') {
          <div class="card card--pad-0">
            <app-state [empty]="applications().length === 0" emptyTitle="Заявок нет" emptyIcon="credit" />
            @if (applications().length) {
              <div class="table-wrap">
                <table class="table">
                  <thead><tr><th>Дата</th><th>Продукт</th><th class="right">Сумма</th><th>Срок</th><th>Статус</th><th>Решение</th><th></th></tr></thead>
                  <tbody>
                    @for (a of applications(); track a.id) {
                      <tr>
                        <td class="nowrap">{{ a.createdAt | dt }}</td>
                        <td>{{ a.productName }}</td>
                        <td class="right nowrap">{{ a.amount | money: a.currency }}</td>
                        <td class="nowrap">{{ a.termMonths }} мес.</td>
                        <td><app-status [status]="a.status" /></td>
                        <td class="muted" style="font-size: 13px">{{ a.decisionReason ?? '—' }}</td>
                        <td class="right">
                          @if (a.status === 'SUBMITTED') {
                            <button class="btn btn--ghost btn--sm" (click)="cancel(a)">Отозвать</button>
                          } @else if (a.loanId) {
                            <a class="link" [routerLink]="['/loans', a.loanId]">Кредит <app-icon name="chevronRight" [size]="14" /></a>
                          }
                        </td>
                      </tr>
                    }
                  </tbody>
                </table>
              </div>
            }
          </div>
        }
      }
    }
  `,
})
export class LoansPage {
  private readonly api = inject(ProductsApi);
  private readonly banking = inject(BankingApi);
  private readonly toast = inject(ToastService);

  protected readonly tab = signal<Tab>('loans');
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly productList = signal<LoanProduct[]>([]);
  protected readonly loans = signal<Loan[]>([]);
  protected readonly applications = signal<LoanApplication[]>([]);
  protected readonly accounts = signal<Account[]>([]);
  protected readonly selected = signal<LoanProduct | null>(null);

  protected readonly products = computed(() => this.productList().filter((p) => p.active));
  protected readonly pendingApps = computed(() => this.applications().filter((a) => a.status === 'SUBMITTED').length);
  protected readonly termLabel = months;

  constructor() {
    this.load(true);
  }

  protected load(initial = false): void {
    this.loading.set(true);
    this.error.set(null);
    forkJoin({
      products: this.api.loanProducts(),
      loans: this.api.loans(),
      applications: this.api.applications(),
      accounts: this.banking.accounts(),
    }).subscribe({
      next: (r) => {
        this.productList.set(r.products);
        this.loans.set(r.loans);
        this.applications.set(r.applications);
        this.accounts.set(r.accounts);
        if (!this.selected()) this.selected.set(this.products().find((p) => p.currency === 'RUB') ?? this.products()[0] ?? null);
        if (initial && r.loans.length === 0) this.tab.set('new');
        this.loading.set(false);
      },
      error: (e) => {
        this.error.set(errorMessage(e));
        this.loading.set(false);
      },
    });
  }

  protected onApplied(a: LoanApplication): void {
    this.tab.set(a.status === 'APPROVED' ? 'loans' : 'applications');
    this.load();
    // Выдача кредита идёт асинхронно (сага), обновим ещё раз, когда деньги дойдут.
    if (a.status === 'APPROVED') setTimeout(() => this.load(), 2500);
  }

  protected cancel(a: LoanApplication): void {
    this.api.cancelApplication(a.id).subscribe({
      next: () => {
        this.toast.success('Заявка отозвана');
        this.load();
      },
      error: (e) => this.toast.error(errorMessage(e)),
    });
  }
}
