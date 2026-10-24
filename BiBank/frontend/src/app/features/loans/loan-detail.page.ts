import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { BankingApi } from '../../core/api/banking.api';
import { ProductsApi } from '../../core/api/products.api';
import { errorMessage } from '../../core/errors';
import { ToastService } from '../../core/toast.service';
import { Account, Loan } from '../../core/models';
import { AccountNumberPipe, DateTimePipe, MoneyPipe } from '../../shared/format.pipes';
import { IconComponent } from '../../shared/icon.component';
import { months } from '../../shared/labels';
import { ModalComponent, StateComponent, StatusBadgeComponent } from '../../shared/ui.components';

@Component({
  selector: 'app-loan-detail-page',
  imports: [FormsModule, RouterLink, IconComponent, MoneyPipe, DateTimePipe, AccountNumberPipe, StateComponent, StatusBadgeComponent, ModalComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <a class="link row" routerLink="/loans" style="margin-bottom: 16px; gap: 4px">
      <app-icon name="chevronLeft" [size]="16" /> Все кредиты
    </a>
    <app-state [loading]="loading()" [error]="error()" (retry)="load()" />

    @if (loan(); as l) {
      <div class="stack stack--lg">
        <section class="hero">
          <div class="row row--between row--wrap" style="position: relative; z-index: 1; align-items: flex-end">
            <div>
              <div class="hero__label">{{ l.productName }} · {{ l.annualRate }}% · {{ term() }}</div>
              <div class="hero__value">{{ l.outstandingPrincipal | money: l.currency }}</div>
              <div class="hero__sub">
                <span>Остаток основного долга</span>
                <span>Выдано {{ l.principal | money: l.currency }}</span>
                <span>Оплачено {{ l.paidInstallments }} из {{ l.termMonths }}</span>
              </div>
            </div>
            @if (l.status === 'ACTIVE') {
              <div class="row row--wrap">
                <button class="btn btn--light" (click)="openRepay('NEXT')">Внести платёж</button>
                <button class="btn btn--light" (click)="openRepay('FULL')">Погасить досрочно</button>
              </div>
            }
          </div>
        </section>

        @if (l.status === 'PENDING_DISBURSEMENT') {
          <div class="alert alert--warning"><app-icon name="refresh" [size]="18" /> Кредит выдаётся: деньги поступят на счёт в ближайшие секунды.</div>
        }
        @if (l.status === 'CLOSED') {
          <div class="alert alert--success"><app-icon name="check" [size]="18" /> Кредит полностью погашен {{ l.closedAt | dt: 'date' }}.</div>
        }

        <div class="grid grid-3" style="align-items: start">
          <div class="stack">
            <section class="card stack">
              <div class="row row--between"><h3>Параметры</h3><app-status [status]="l.status" /></div>
              <dl class="dl">
                <dt>Ежемесячно</dt><dd>{{ l.monthlyPayment | money: l.currency }}</dd>
                @if (l.nextPaymentDate) {
                  <dt>Ближайший</dt><dd>{{ l.nextPaymentAmount | money: l.currency }} · {{ l.nextPaymentDate | dt: 'date' }}</dd>
                }
                <dt>Осталось выплатить</dt><dd>{{ l.totalRemainingPayments | money: l.currency }}</dd>
                <dt>Выдан</dt><dd>{{ l.issuedAt | dt: 'date' }}</dd>
              </dl>
              <div class="progress"><div class="progress__bar" [style.width.%]="(l.paidInstallments / l.termMonths) * 100"></div></div>
            </section>

            @if (l.payments?.length) {
              <section class="card stack stack--sm">
                <h3>Платежи</h3>
                @for (p of l.payments; track p.id) {
                  <div class="row row--between" style="padding: 6px 0; border-bottom: 1px solid var(--border)">
                    <div>
                      <div>{{ p.mode === 'FULL' ? 'Досрочное погашение' : 'Платёж по графику' }}</div>
                      <div class="subtle">{{ p.createdAt | dt }}</div>
                      @if (p.failureReason) { <div class="field__error">{{ p.failureReason }}</div> }
                    </div>
                    <div class="right">
                      <b>{{ p.amount | money: l.currency }}</b>
                      @if (p.status !== 'COMPLETED') { <div><app-status [status]="p.status" /></div> }
                    </div>
                  </div>
                }
              </section>
            }
          </div>

          <section class="card card--pad-0 span-2">
            <div class="card__header card__header--pad"><h3>График платежей</h3></div>
            <div class="table-wrap" style="max-height: 560px; overflow-y: auto">
              <table class="table">
                <thead><tr><th>№</th><th>Дата</th><th class="right">Платёж</th><th class="right">Долг</th><th class="right">Проценты</th><th class="right">Остаток</th><th></th></tr></thead>
                <tbody>
                  @for (s of l.schedule ?? []; track s.number) {
                    <tr [style.opacity]="s.status === 'PAID' ? 0.6 : 1">
                      <td>{{ s.number }}</td>
                      <td class="nowrap">{{ s.dueDate | dt: 'date' }}</td>
                      <td class="right nowrap">{{ s.payment | money }}</td>
                      <td class="right nowrap">{{ s.principal | money }}</td>
                      <td class="right nowrap">{{ s.interest | money }}</td>
                      <td class="right nowrap">{{ s.remaining | money }}</td>
                      <td><app-status [status]="s.status" /></td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
          </section>
        </div>
      </div>
    }

    @if (repayMode(); as mode) {
      <app-modal [title]="mode === 'FULL' ? 'Досрочное погашение' : 'Платёж по кредиту'" (closed)="repayMode.set(null)">
        <div class="stack">
          <div class="summary">
            <div class="summary__row summary__row--total" style="border: none; margin: 0; padding: 0">
              <span>К оплате</span>
              <span>{{ (mode === 'FULL' ? loan()!.outstandingPrincipal : loan()!.nextPaymentAmount) | money: loan()!.currency }}</span>
            </div>
          </div>
          @if (mode === 'FULL') {
            <p class="muted">Списывается остаток основного долга, проценты за будущие периоды не начисляются.</p>
          }
          <div class="field">
            <label for="r-acc">Счёт списания</label>
            @if (suitable().length) {
              <select id="r-acc" class="input" [ngModel]="accountId()" (ngModelChange)="accountId.set($event)">
                @for (a of suitable(); track a.id) {
                  <option [value]="a.id">{{ a.name }} · {{ a.number | accountNumber: true }} · {{ a.balance | money: a.currency }}</option>
                }
              </select>
            } @else {
              <div class="alert alert--info">Нет активного счёта в {{ loan()!.currency }}.</div>
            }
          </div>
          <div class="row" style="justify-content: flex-end">
            <button class="btn btn--ghost" (click)="repayMode.set(null)">Отмена</button>
            <button class="btn" [disabled]="busy() || !accountId()" (click)="repay(mode)">
              @if (busy()) { <span class="spinner"></span> } Оплатить
            </button>
          </div>
        </div>
      </app-modal>
    }
  `,
})
export class LoanDetailPage {
  private readonly api = inject(ProductsApi);
  private readonly banking = inject(BankingApi);
  private readonly toast = inject(ToastService);
  readonly id = input.required<string>();

  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly loan = signal<Loan | null>(null);
  protected readonly accounts = signal<Account[]>([]);
  protected readonly repayMode = signal<'NEXT' | 'FULL' | null>(null);
  protected readonly accountId = signal('');
  protected readonly busy = signal(false);

  protected readonly term = computed(() => months(this.loan()?.termMonths ?? 0));
  protected readonly suitable = computed(() =>
    this.accounts().filter((a) => a.status === 'ACTIVE' && a.currency === this.loan()?.currency),
  );

  constructor() {
    effect(() => {
      this.id();
      this.load();
    });
  }

  protected load(): void {
    this.loading.set(!this.loan());
    this.error.set(null);
    this.api.loan(this.id()).subscribe({
      next: (l) => {
        this.loan.set(l);
        this.loading.set(false);
        if (l.status === 'PENDING_DISBURSEMENT') setTimeout(() => this.load(), 2000);
      },
      error: (e) => {
        this.error.set(errorMessage(e));
        this.loading.set(false);
      },
    });
  }

  protected openRepay(mode: 'NEXT' | 'FULL'): void {
    this.repayMode.set(mode);
    this.banking.accounts().subscribe((a) => {
      this.accounts.set(a);
      const own = this.suitable();
      this.accountId.set(own.find((x) => x.id === this.loan()?.accountId)?.id ?? own[0]?.id ?? '');
    });
  }

  protected repay(mode: 'NEXT' | 'FULL'): void {
    this.busy.set(true);
    this.api.repay(this.id(), this.accountId(), mode).subscribe({
      next: (p) => {
        this.busy.set(false);
        this.repayMode.set(null);
        if (p.status === 'COMPLETED') this.toast.success(mode === 'FULL' ? 'Кредит погашен' : 'Платёж внесён');
        else if (p.status === 'FAILED') this.toast.error(p.failureReason ?? 'Платёж не прошёл');
        else this.toast.info('Платёж обрабатывается');
        this.load();
      },
      error: (e) => {
        this.busy.set(false);
        this.toast.error(errorMessage(e));
      },
    });
  }
}
