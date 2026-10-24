import { ChangeDetectionStrategy, Component, computed, effect, inject, input, output, signal, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ProductsApi } from '../../core/api/products.api';
import { errorMessage } from '../../core/errors';
import { ToastService } from '../../core/toast.service';
import { Account, Deposit, DepositProduct } from '../../core/models';
import { AccountNumberPipe, MoneyPipe } from '../../shared/format.pipes';
import { months } from '../../shared/labels';
import { ModalComponent } from '../../shared/ui.components';

@Component({
  selector: 'app-open-deposit',
  imports: [FormsModule, ModalComponent, MoneyPipe, AccountNumberPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @let p = product();
    <app-modal [title]="'Вклад «' + p.name + '»'" (closed)="closed.emit()">
      <form class="stack" (ngSubmit)="submit()">
        <div class="row row--wrap" style="gap: 8px">
          <span class="chip">{{ p.annualRate }}% годовых</span>
          <span class="chip">{{ p.capitalization ? 'С капитализацией' : 'Выплата в конце срока' }}</span>
          <span class="chip">Досрочно: {{ p.earlyCloseRate }}%</span>
        </div>
        <div class="field">
          <label for="d-acc">Счёт списания</label>
          @if (suitable().length) {
            <select id="d-acc" class="input" name="acc" [ngModel]="accountId()" (ngModelChange)="accountId.set($event)">
              @for (a of suitable(); track a.id) {
                <option [value]="a.id">{{ a.name }} · {{ a.number | accountNumber: true }} · {{ a.balance | money: a.currency }}</option>
              }
            </select>
          } @else {
            <div class="alert alert--info">Нужен активный счёт в {{ p.currency }}. Откройте его в разделе «Счета».</div>
          }
        </div>
        <div class="field">
          <label for="d-amount">Сумма</label>
          <div class="input-group">
            <input id="d-amount" class="input input--lg" name="amount" type="number" [min]="p.minAmount" [max]="p.maxAmount"
                   [ngModel]="amount()" (ngModelChange)="amount.set($event)" />
            <span class="input-group__suffix">{{ p.currency }}</span>
          </div>
          <span class="field__hint">От {{ p.minAmount | money: p.currency }} до {{ p.maxAmount | money: p.currency }}</span>
        </div>
        <div class="field">
          <label for="d-term">Срок: {{ termLabel() }}</label>
          <input id="d-term" type="range" name="term" [min]="p.minTermMonths" [max]="p.maxTermMonths"
                 [ngModel]="term()" (ngModelChange)="term.set(+$event)" style="width: 100%" />
          <div class="row row--between subtle"><span>{{ p.minTermMonths }} мес.</span><span>{{ p.maxTermMonths }} мес.</span></div>
        </div>
        <div class="summary">
          <div class="summary__row"><span class="muted">Доход за срок (оценка)</span><b class="positive">{{ income() | money: p.currency : true }}</b></div>
          <div class="summary__row summary__row--total"><span>В конце срока</span><span>{{ (amount() ?? 0) + income() | money: p.currency }}</span></div>
        </div>
        @if (error()) { <div class="alert alert--error">{{ error() }}</div> }
        <button class="btn btn--block btn--lg" type="submit" [disabled]="loading() || !valid()">
          @if (loading()) { <span class="spinner"></span> } Открыть вклад
        </button>
      </form>
    </app-modal>
  `,
})
export class OpenDepositComponent {
  private readonly api = inject(ProductsApi);
  private readonly toast = inject(ToastService);

  readonly product = input.required<DepositProduct>();
  readonly accounts = input.required<Account[]>();
  readonly closed = output<void>();
  readonly opened = output<Deposit>();

  protected readonly accountId = signal('');
  protected readonly amount = signal<number | null>(null);
  protected readonly term = signal(12);
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly suitable = computed(() =>
    this.accounts().filter((a) => a.status === 'ACTIVE' && a.currency === this.product().currency),
  );
  protected readonly termLabel = computed(() => months(this.term()));
  protected readonly selected = computed(() => this.suitable().find((a) => a.id === this.accountId()));
  protected readonly valid = computed(() => {
    const a = Number(this.amount());
    const p = this.product();
    return !!this.selected() && a >= p.minAmount && a <= p.maxAmount;
  });
  /** Простая оценка: ежедневное начисление, при капитализации — сложный процент. */
  protected readonly income = computed(() => {
    const amount = Number(this.amount()) || 0;
    const p = this.product();
    const days = Math.round(this.term() * 30.4375);
    const daily = p.annualRate / 100 / 365;
    const value = p.capitalization ? amount * (Math.pow(1 + daily, days) - 1) : amount * daily * days;
    return Math.round(value * 100) / 100;
  });

  constructor() {
    effect(() => {
      const p = this.product();
      const list = this.suitable();
      untracked(() => {
        this.term.set(Math.min(p.maxTermMonths, Math.max(p.minTermMonths, 12)));
        this.amount.set(p.minAmount);
        this.accountId.set(list[0]?.id ?? '');
      });
    });
  }

  protected submit(): void {
    if (!this.valid()) return;
    this.loading.set(true);
    this.error.set(null);
    this.api
      .openDeposit({ productId: this.product().id, accountId: this.accountId(), amount: Number(this.amount()), termMonths: this.term() })
      .subscribe({
        next: (d) => {
          this.toast.success(d.status === 'ACTIVE' ? 'Вклад открыт' : 'Вклад открывается, статус обновится автоматически');
          this.opened.emit(d);
        },
        error: (e) => {
          this.loading.set(false);
          this.error.set(errorMessage(e));
        },
      });
  }
}
