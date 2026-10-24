import { ChangeDetectionStrategy, Component, effect, inject, signal, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AdminApi } from '../../core/api/admin.api';
import { errorMessage } from '../../core/errors';
import { Operation, OperationStatus, OperationType, Page } from '../../core/models';
import { AccountNumberPipe, DateTimePipe, MoneyPipe } from '../../shared/format.pipes';
import { OPERATION_TYPES } from '../../shared/labels';
import { OperationDetailsComponent } from '../../shared/operation.components';
import { PagerComponent, StateComponent, StatusBadgeComponent } from '../../shared/ui.components';

@Component({
  selector: 'app-admin-operations',
  imports: [FormsModule, DateTimePipe, MoneyPipe, AccountNumberPipe, StateComponent, StatusBadgeComponent, PagerComponent, OperationDetailsComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="card card--pad-0">
      <div class="row row--wrap card__header--pad" style="padding-bottom: 16px">
        <select class="input" style="max-width: 220px" [ngModel]="type()" (ngModelChange)="type.set($event); page.set(0)">
          <option value="">Все типы</option>
          @for (t of types; track t[0]) { <option [value]="t[0]">{{ t[1] }}</option> }
        </select>
        <select class="input" style="max-width: 200px" [ngModel]="status()" (ngModelChange)="status.set($event); page.set(0)">
          <option value="">Все статусы</option>
          <option value="COMPLETED">Выполнено</option>
          <option value="PENDING">В обработке</option>
          <option value="FAILED">Ошибка</option>
        </select>
        <span class="subtle">Всего: {{ data()?.totalElements ?? 0 }}</span>
      </div>
      <app-state [loading]="loading()" [error]="error()" (retry)="reload()" [empty]="!loading() && !error() && !data()?.content?.length"
                 emptyTitle="Операций нет" emptyIcon="history" />
      @if (!loading() && data()?.content?.length) {
        <div class="table-wrap">
          <table class="table">
            <thead><tr><th>Время</th><th>Тип</th><th>Откуда</th><th>Куда</th><th class="right">Сумма</th><th>Статус</th></tr></thead>
            <tbody>
              @for (op of data()!.content; track op.id) {
                <tr style="cursor: pointer" (click)="selected.set(op)">
                  <td class="nowrap">{{ op.createdAt | dt }}</td>
                  <td>{{ typeLabel(op.type) }}</td>
                  <td class="mono nowrap">{{ op.fromAccountNumber | accountNumber: true }}</td>
                  <td class="mono nowrap">{{ op.toAccountNumber | accountNumber: true }}</td>
                  <td class="right nowrap">{{ op.amount | money: op.currency }}</td>
                  <td><app-status [status]="op.status" />@if (op.failureCode) { <div class="subtle">{{ op.failureCode }}</div> }</td>
                </tr>
              }
            </tbody>
          </table>
        </div>
        <div style="padding: 0 16px 12px"><app-pager [page]="page()" [totalPages]="data()!.totalPages" (change)="page.set($event)" /></div>
      }
    </div>
    @if (selected(); as op) {
      <app-operation-details [operation]="op" (closed)="selected.set(null)" />
    }
  `,
})
export class AdminOperationsComponent {
  private readonly api = inject(AdminApi);
  protected readonly types = Object.entries(OPERATION_TYPES);
  protected readonly type = signal<OperationType | ''>('');
  protected readonly status = signal<OperationStatus | ''>('');
  protected readonly page = signal(0);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly data = signal<Page<Operation> | null>(null);
  protected readonly selected = signal<Operation | null>(null);
  private readonly tick = signal(0);

  constructor() {
    effect(() => {
      this.tick();
      const filter = { type: this.type(), status: this.status(), page: this.page(), size: 20 };
      untracked(() => this.fetch(filter));
    });
  }

  protected typeLabel(t: string): string {
    return OPERATION_TYPES[t] ?? t;
  }

  protected reload(): void {
    this.tick.update((n) => n + 1);
  }

  private fetch(filter: { type: OperationType | ''; status: OperationStatus | ''; page: number; size: number }): void {
    this.loading.set(true);
    this.error.set(null);
    this.api.operations(filter).subscribe({
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
}
