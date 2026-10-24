import { DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ProductsApi } from '../../core/api/products.api';
import { errorMessage } from '../../core/errors';
import { ToastService } from '../../core/toast.service';
import { Deposit } from '../../core/models';
import { DateTimePipe, MoneyPipe } from '../../shared/format.pipes';
import { IconComponent } from '../../shared/icon.component';
import { months } from '../../shared/labels';
import { ModalComponent, StateComponent, StatusBadgeComponent } from '../../shared/ui.components';
import { depositProgress } from './deposits.page';

@Component({
  selector: 'app-deposit-detail-page',
  imports: [RouterLink, DecimalPipe, IconComponent, MoneyPipe, DateTimePipe, StateComponent, StatusBadgeComponent, ModalComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <a class="link row" routerLink="/deposits" style="margin-bottom: 16px; gap: 4px">
      <app-icon name="chevronLeft" [size]="16" /> Все вклады
    </a>
    <app-state [loading]="loading()" [error]="error()" (retry)="load()" />

    @if (deposit(); as d) {
      <div class="stack stack--lg">
        <section class="hero">
          <div class="row row--between row--wrap" style="position: relative; z-index: 1; align-items: flex-end">
            <div>
              <div class="hero__label">{{ d.productName }} · {{ d.annualRate }}% годовых</div>
              <div class="hero__value">{{ (d.status === 'CLOSED' ? d.payoutAmount : d.currentValue) | money: d.currency }}</div>
              <div class="hero__sub">
                <span>Вложено {{ d.principal | money: d.currency }}</span>
                <span>Начислено {{ (d.status === 'CLOSED' ? d.payoutInterest : d.accruedInterest) | money: d.currency : true }}</span>
              </div>
            </div>
            @if (d.status === 'ACTIVE') {
              <button class="btn btn--light" (click)="confirm.set(true)">Закрыть вклад</button>
            }
          </div>
        </section>

        @if (d.status === 'FAILED' && d.failureReason) {
          <div class="alert alert--error">{{ d.failureReason }}</div>
        }

        <div class="grid grid-3" style="align-items: start">
          <section class="card stack">
            <div class="row row--between"><h3>Условия</h3><app-status [status]="d.status" /></div>
            <dl class="dl">
              <dt>Срок</dt><dd>{{ term() }}</dd>
              <dt>Открыт</dt><dd>{{ d.openDate | dt: 'date' }}</dd>
              <dt>Окончание</dt><dd>{{ d.maturityDate | dt: 'date' }}</dd>
              <dt>Начислено по</dt><dd>{{ d.accruedThrough | dt: 'date' }}</dd>
              <dt>Капитализация</dt><dd>{{ d.capitalization ? 'Да' : 'Нет' }}</dd>
              <dt>Досрочно</dt><dd>{{ d.earlyCloseRate }}% годовых</dd>
              @if (d.status === 'ACTIVE') {
                <dt>Прогноз дохода</dt><dd class="positive">{{ d.projectedInterest | money: d.currency : true }}</dd>
              }
              @if (d.closedAt) {
                <dt>Закрыт</dt><dd>{{ d.closedAt | dt }} {{ d.earlyClosed ? '(досрочно)' : '' }}</dd>
              }
            </dl>
            @if (d.status === 'ACTIVE') {
              <div>
                <div class="row row--between subtle" style="margin-bottom: 6px">
                  <span>Прошло срока</span><span>{{ progress() | number: '1.0-0' }}%</span>
                </div>
                <div class="progress"><div class="progress__bar" [style.width.%]="progress()"></div></div>
              </div>
            }
          </section>

          <section class="card span-2">
            <h3 style="margin-bottom: 8px">Начисления процентов <span class="subtle">(последние 100 дней)</span></h3>
            <app-state [empty]="!d.accruals?.length" emptyTitle="Начислений пока нет"
                       emptyText="Проценты начисляются ежедневно, первое начисление появится завтра" emptyIcon="percent" />
            @if (d.accruals?.length) {
              <div class="table-wrap" style="max-height: 420px; overflow-y: auto">
                <table class="table">
                  <thead><tr><th>Дата</th><th class="right">База</th><th class="right">Начислено</th></tr></thead>
                  <tbody>
                    @for (a of accrualsDesc(); track a.date) {
                      <tr>
                        <td>{{ a.date | dt: 'date' }}</td>
                        <td class="right">{{ a.base | money: d.currency }}</td>
                        <td class="right positive">{{ a.amount | money: d.currency : true }}</td>
                      </tr>
                    }
                  </tbody>
                </table>
              </div>
            }
          </section>
        </div>
      </div>
    }

    @if (confirm() && deposit(); as d) {
      <app-modal title="Закрыть вклад?" (closed)="confirm.set(false)">
        <div class="stack">
          @if (early()) {
            <div class="alert alert--warning">
              Срок вклада ещё не закончился. Проценты будут пересчитаны по ставке {{ d.earlyCloseRate }}% годовых.
            </div>
          } @else {
            <p>Срок вклада закончился, проценты будут выплачены полностью.</p>
          }
          <p class="muted">Деньги вернутся на счёт, с которого вклад был открыт.</p>
          <div class="row" style="justify-content: flex-end">
            <button class="btn btn--ghost" (click)="confirm.set(false)">Отмена</button>
            <button class="btn btn--danger" [disabled]="busy()" (click)="close()">
              @if (busy()) { <span class="spinner"></span> } Закрыть вклад
            </button>
          </div>
        </div>
      </app-modal>
    }
  `,
})
export class DepositDetailPage {
  private readonly api = inject(ProductsApi);
  private readonly toast = inject(ToastService);
  readonly id = input.required<string>();

  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly deposit = signal<Deposit | null>(null);
  protected readonly confirm = signal(false);
  protected readonly busy = signal(false);

  protected readonly term = computed(() => months(this.deposit()?.termMonths ?? 0));
  protected readonly progress = computed(() => (this.deposit() ? depositProgress(this.deposit()!) : 0));
  protected readonly early = computed(() => {
    const d = this.deposit();
    return !!d && d.accruedThrough < d.maturityDate;
  });
  protected readonly accrualsDesc = computed(() => this.deposit()?.accruals ?? []);

  constructor() {
    effect(() => {
      this.id();
      this.load();
    });
  }

  protected load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.api.deposit(this.id()).subscribe({
      next: (d) => {
        this.deposit.set(d);
        this.loading.set(false);
      },
      error: (e) => {
        this.error.set(errorMessage(e));
        this.loading.set(false);
      },
    });
  }

  protected close(): void {
    this.busy.set(true);
    this.api.closeDeposit(this.id()).subscribe({
      next: (d) => {
        this.busy.set(false);
        this.confirm.set(false);
        this.toast.success(d.status === 'CLOSED' ? `Вклад закрыт, выплачено ${d.payoutAmount}` : 'Вклад закрывается');
        this.load();
      },
      error: (e) => {
        this.busy.set(false);
        this.toast.error(errorMessage(e));
      },
    });
  }
}
