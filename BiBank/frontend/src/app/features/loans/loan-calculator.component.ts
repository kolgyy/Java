import { ChangeDetectionStrategy, Component, computed, effect, inject, input, output, signal, untracked } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { catchError, debounceTime, of, switchMap } from 'rxjs';
import { ProductsApi } from '../../core/api/products.api';
import { errorMessage } from '../../core/errors';
import { ToastService } from '../../core/toast.service';
import { Account, LoanApplication, LoanCalculation, LoanProduct } from '../../core/models';
import { AccountNumberPipe, DateTimePipe, MoneyPipe } from '../../shared/format.pipes';
import { IconComponent } from '../../shared/icon.component';
import { months } from '../../shared/labels';

@Component({
  selector: 'app-loan-calculator',
  imports: [FormsModule, IconComponent, MoneyPipe, AccountNumberPipe, DateTimePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @let p = product();
    <div class="grid grid-2" style="align-items: start">
      <div class="card stack stack--lg">
        <div class="row row--between">
          <h3>{{ p.name }}</h3>
          <span class="chip">{{ p.annualRate }}% · {{ p.currency }}</span>
        </div>
        <div class="field">
          <label for="c-amount">Сумма: {{ amount() | money: p.currency }}</label>
          <input id="c-amount" type="range" [min]="p.minAmount" [max]="p.maxAmount" [step]="step()"
                 [ngModel]="amount()" (ngModelChange)="amount.set(+$event)" style="width: 100%" />
          <div class="row row--between subtle"><span>{{ p.minAmount | money }}</span><span>{{ p.maxAmount | money }}</span></div>
        </div>
        <div class="field">
          <label for="c-term">Срок: {{ termLabel() }}</label>
          <input id="c-term" type="range" [min]="p.minTermMonths" [max]="p.maxTermMonths"
                 [ngModel]="term()" (ngModelChange)="term.set(+$event)" style="width: 100%" />
          <div class="row row--between subtle"><span>{{ p.minTermMonths }} мес.</span><span>{{ p.maxTermMonths }} мес.</span></div>
        </div>

        <div class="summary">
          @if (calc(); as c) {
            <div class="summary__row"><span class="muted">Ежемесячный платёж</span><b style="font-size: 20px">{{ c.monthlyPayment | money: c.currency }}</b></div>
            <div class="summary__row"><span class="muted">Переплата</span><span>{{ c.totalInterest | money: c.currency }}</span></div>
            <div class="summary__row summary__row--total"><span>Всего выплат</span><span>{{ c.totalPayment | money: c.currency }}</span></div>
          } @else {
            <div class="center subtle">Расчёт…</div>
          }
        </div>
        @if (amount() <= p.autoApproveLimit) {
          <div class="alert alert--success"><app-icon name="check" [size]="18" /> До {{ p.autoApproveLimit | money: p.currency }} решение принимается автоматически за секунды</div>
        } @else {
          <div class="alert alert--info"><app-icon name="info" [size]="18" /> Заявку на эту сумму рассмотрит сотрудник банка</div>
        }
        <button class="btn btn--ghost btn--sm" (click)="showSchedule.set(!showSchedule())">
          {{ showSchedule() ? 'Скрыть график' : 'Показать график платежей' }}
        </button>
      </div>

      <form class="card stack" (ngSubmit)="submit()">
        <h3>Заявка на кредит</h3>
        <div class="field">
          <label for="a-acc">Счёт для зачисления</label>
          @if (suitable().length) {
            <select id="a-acc" class="input" name="acc" [ngModel]="accountId()" (ngModelChange)="accountId.set($event)">
              @for (a of suitable(); track a.id) {
                <option [value]="a.id">{{ a.name }} · {{ a.number | accountNumber: true }}</option>
              }
            </select>
          } @else {
            <div class="alert alert--info">Нужен активный счёт в {{ p.currency }}.</div>
          }
        </div>
        <div class="field">
          <label for="a-income">Ежемесячный доход, {{ p.currency }}</label>
          <input id="a-income" class="input" type="number" name="income" min="0" [ngModel]="income()" (ngModelChange)="income.set($event)"
                 placeholder="Необязательно" />
          <span class="field__hint">Платёж не должен превышать 50% дохода</span>
        </div>
        <div class="field">
          <label for="a-purpose">Цель кредита</label>
          <input id="a-purpose" class="input" name="purpose" maxlength="255" [(ngModel)]="purpose" placeholder="Например, ремонт" />
        </div>
        @if (error()) { <div class="alert alert--error">{{ error() }}</div> }
        <button class="btn btn--lg btn--block" type="submit" [disabled]="busy() || !accountId()">
          @if (busy()) { <span class="spinner"></span> } Отправить заявку
        </button>
        <p class="subtle">Деньги виртуальные, кредит учебный. Погашать его можно в любой момент без комиссии.</p>
      </form>
    </div>

    @if (showSchedule() && calc(); as c) {
      <div class="card card--pad-0" style="margin-top: 16px">
        <div class="table-wrap" style="max-height: 420px; overflow-y: auto">
          <table class="table">
            <thead><tr><th>№</th><th>Дата</th><th class="right">Платёж</th><th class="right">Основной долг</th><th class="right">Проценты</th><th class="right">Остаток</th></tr></thead>
            <tbody>
              @for (s of c.schedule; track s.number) {
                <tr>
                  <td>{{ s.number }}</td><td>{{ s.dueDate | dt: 'date' }}</td>
                  <td class="right">{{ s.payment | money }}</td><td class="right">{{ s.principal | money }}</td>
                  <td class="right">{{ s.interest | money }}</td><td class="right">{{ s.remaining | money }}</td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      </div>
    }
  `,
})
export class LoanCalculatorComponent {
  private readonly api = inject(ProductsApi);
  private readonly toast = inject(ToastService);

  readonly product = input.required<LoanProduct>();
  readonly accounts = input.required<Account[]>();
  readonly applied = output<LoanApplication>();

  protected readonly amount = signal(100000);
  protected readonly term = signal(12);
  protected readonly calc = signal<LoanCalculation | null>(null);
  protected readonly showSchedule = signal(false);
  protected readonly accountId = signal('');
  protected readonly income = signal<number | null>(null);
  protected purpose = '';
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly termLabel = computed(() => months(this.term()));
  protected readonly step = computed(() => (this.product().maxAmount > 1_000_000 ? 10000 : 1000));
  protected readonly suitable = computed(() =>
    this.accounts().filter((a) => a.status === 'ACTIVE' && a.currency === this.product().currency),
  );

  constructor() {
    effect(() => {
      const p = this.product();
      const list = this.suitable();
      untracked(() => {
        const mid = Math.min(p.maxAmount, Math.max(p.minAmount, p.autoApproveLimit > 0 ? p.autoApproveLimit / 2 : p.minAmount));
        this.amount.set(Math.round(mid / this.step()) * this.step());
        this.term.set(Math.min(p.maxTermMonths, Math.max(p.minTermMonths, 12)));
        this.accountId.set(list[0]?.id ?? '');
        this.error.set(null);
      });
    });

    const params = computed(() => ({ id: this.product().id, amount: this.amount(), term: this.term() }));
    toObservable(params)
      .pipe(
        debounceTime(200),
        switchMap((p) => this.api.calculate(p.id, p.amount, p.term).pipe(catchError(() => of(null)))),
        takeUntilDestroyed(),
      )
      .subscribe((c) => this.calc.set(c));
  }

  protected submit(): void {
    this.busy.set(true);
    this.error.set(null);
    this.api
      .apply({
        productId: this.product().id,
        accountId: this.accountId(),
        amount: this.amount(),
        termMonths: this.term(),
        monthlyIncome: this.income() ? Number(this.income()) : null,
        purpose: this.purpose.trim() || undefined,
      })
      .subscribe({
        next: (a) => {
          this.busy.set(false);
          const text: Record<string, string> = {
            APPROVED: 'Кредит одобрен! Деньги поступят на счёт в течение нескольких секунд',
            REJECTED: `Заявка отклонена: ${a.decisionReason ?? ''}`,
            SUBMITTED: 'Заявка отправлена на рассмотрение',
          };
          if (a.status === 'REJECTED') this.toast.error(text[a.status]);
          else this.toast.success(text[a.status] ?? 'Заявка создана');
          this.applied.emit(a);
        },
        error: (e) => {
          this.busy.set(false);
          this.error.set(errorMessage(e));
        },
      });
  }
}
