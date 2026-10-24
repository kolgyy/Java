import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Operation } from '../core/models';
import { AccountNumberPipe, DateTimePipe, MoneyPipe, RatePipe } from './format.pipes';
import { IconComponent } from './icon.component';
import { OPERATION_TYPES } from './labels';
import { ModalComponent, StatusBadgeComponent } from './ui.components';

const ICONS: Record<string, string> = {
  TRANSFER: 'send',
  TOP_UP: 'plus',
  LOAN_DISBURSEMENT: 'credit',
  LOAN_REPAYMENT: 'credit',
  DEPOSIT_OPEN: 'piggy',
  DEPOSIT_CLOSE: 'piggy',
};

/** Заголовок операции глазами владельца счёта. */
export function operationTitle(op: Operation): string {
  if (op.type === 'TRANSFER') {
    if (op.direction === 'INTERNAL') return 'Между своими счетами';
    const who = op.counterpartyName ? ` · ${op.counterpartyName}` : '';
    return (op.direction === 'IN' ? 'Входящий перевод' : 'Перевод') + who;
  }
  return OPERATION_TYPES[op.type] ?? op.type;
}

/** Сумма со знаком: списание в валюте списания, зачисление в валюте зачисления. */
export function operationAmount(op: Operation): { value: number; currency: string } {
  if (op.direction === 'IN') return { value: op.creditedAmount, currency: op.creditedCurrency };
  if (op.direction === 'OUT') return { value: -op.amount, currency: op.currency };
  return { value: op.amount, currency: op.currency };
}

@Component({
  selector: 'app-operation-item',
  imports: [IconComponent, MoneyPipe, DateTimePipe, StatusBadgeComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @let op = operation();
    <div class="op" (click)="opened.emit(op)" role="button" tabindex="0" (keydown.enter)="opened.emit(op)">
      <div class="op__icon" [class.op__icon--in]="op.direction === 'IN'" [class.op__icon--failed]="op.status === 'FAILED'">
        <app-icon [name]="op.status === 'FAILED' ? 'x' : op.direction === 'IN' ? 'arrowDown' : op.direction === 'INTERNAL' ? 'swap' : icon()" [size]="18" />
      </div>
      <div class="grow">
        <div class="op__title">{{ title() }}</div>
        <div class="op__meta">
          {{ op.createdAt | dt }}
          @if (op.description) { · {{ op.description }} }
        </div>
      </div>
      <div class="right">
        <div class="op__amount" [class.positive]="amount().value > 0 && op.direction === 'IN'"
             [class.subtle]="op.status === 'FAILED'">
          {{ amount().value | money: amount().currency : op.direction === 'IN' }}
        </div>
        @if (op.status !== 'COMPLETED') {
          <app-status [status]="op.status" />
        }
      </div>
    </div>
  `,
})
export class OperationItemComponent {
  readonly operation = input.required<Operation>();
  readonly opened = output<Operation>();
  protected readonly title = computed(() => operationTitle(this.operation()));
  protected readonly amount = computed(() => operationAmount(this.operation()));
  protected readonly icon = computed(() => ICONS[this.operation().type] ?? 'activity');
}

@Component({
  selector: 'app-operation-details',
  imports: [ModalComponent, MoneyPipe, RatePipe, DateTimePipe, AccountNumberPipe, StatusBadgeComponent, RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @let op = operation();
    <app-modal [title]="title()" (closed)="closed.emit()">
      <div class="stack">
        <div class="center stack--sm stack">
          <div class="hero__value" style="font-size: 30px">
            {{ amount().value | money: amount().currency : op.direction === 'IN' }}
          </div>
          <div><app-status [status]="op.status" /></div>
        </div>
        @if (op.status === 'FAILED' && op.failureReason) {
          <div class="alert alert--error">{{ op.failureReason }}</div>
        }
        <dl class="dl">
          <dt>Тип</dt><dd>{{ typeLabel() }}</dd>
          @if (op.fromAccountNumber) {
            <dt>Счёт списания</dt><dd class="mono">{{ op.fromAccountNumber | accountNumber }}</dd>
          }
          @if (op.toAccountNumber) {
            <dt>Счёт зачисления</dt><dd class="mono">{{ op.toAccountNumber | accountNumber }}</dd>
          }
          @if (op.counterpartyName) {
            <dt>Контрагент</dt><dd>{{ op.counterpartyName }}</dd>
          }
          <dt>Сумма списания</dt><dd>{{ op.amount | money: op.currency }}</dd>
          @if (op.creditedCurrency !== op.currency) {
            <dt>Зачислено</dt><dd>{{ op.creditedAmount | money: op.creditedCurrency }}</dd>
            <dt>Курс</dt><dd>{{ op.exchangeRate | rate: op.currency : op.creditedCurrency }}</dd>
          }
          @if (op.description) {
            <dt>Назначение</dt><dd>{{ op.description }}</dd>
          }
          <dt>Создана</dt><dd>{{ op.createdAt | dt }}</dd>
          @if (op.completedAt) {
            <dt>Проведена</dt><dd>{{ op.completedAt | dt }}</dd>
          }
          <dt>Номер операции</dt><dd class="mono subtle" style="font-size: 12px">{{ op.id }}</dd>
        </dl>
        @if (op.type === 'TRANSFER' && op.direction !== 'IN' && op.toAccountNumber) {
          <a class="btn btn--ghost" routerLink="/transfer" [queryParams]="{ to: op.toAccountNumber, from: op.fromAccountId }"
             (click)="closed.emit()">Повторить перевод</a>
        }
      </div>
    </app-modal>
  `,
})
export class OperationDetailsComponent {
  readonly operation = input.required<Operation>();
  readonly closed = output<void>();
  protected readonly title = computed(() => operationTitle(this.operation()));
  protected readonly amount = computed(() => operationAmount(this.operation()));
  protected readonly typeLabel = computed(() => OPERATION_TYPES[this.operation().type] ?? this.operation().type);
}
