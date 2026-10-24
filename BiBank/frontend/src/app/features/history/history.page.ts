import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { BankingApi } from '../../core/api/banking.api';
import { errorMessage } from '../../core/errors';
import { Account, HistoryFilter, Operation, OperationStatus, OperationType, Page } from '../../core/models';
import { AccountNumberPipe, DateTimePipe } from '../../shared/format.pipes';
import { IconComponent } from '../../shared/icon.component';
import { OPERATION_TYPES } from '../../shared/labels';
import { OperationDetailsComponent, OperationItemComponent } from '../../shared/operation.components';
import { PageHeaderComponent, PagerComponent, StateComponent } from '../../shared/ui.components';

interface DayGroup {
  day: string;
  items: Operation[];
}

@Component({
  selector: 'app-history-page',
  imports: [
    FormsModule, IconComponent, AccountNumberPipe, DateTimePipe, PageHeaderComponent, PagerComponent, StateComponent,
    OperationItemComponent, OperationDetailsComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-page-header title="История операций" subtitle="Переводы, пополнения, кредиты и вклады по всем счетам" />

    <div class="card stack" style="margin-bottom: 16px">
      <div class="grid grid-4">
        <div class="field">
          <label for="h-acc">Счёт</label>
          <select id="h-acc" class="input" [ngModel]="accountId()" (ngModelChange)="set(accountId, $event)">
            <option value="">Все счета</option>
            @for (a of accounts(); track a.id) {
              <option [value]="a.id">{{ a.name }} · {{ a.number | accountNumber: true }}</option>
            }
          </select>
        </div>
        <div class="field">
          <label for="h-type">Тип</label>
          <select id="h-type" class="input" [ngModel]="type()" (ngModelChange)="set(type, $event)">
            <option value="">Все типы</option>
            @for (t of types; track t[0]) { <option [value]="t[0]">{{ t[1] }}</option> }
          </select>
        </div>
        <div class="field">
          <label for="h-from">С даты</label>
          <input id="h-from" class="input" type="date" [ngModel]="from()" (ngModelChange)="set(from, $event)" />
        </div>
        <div class="field">
          <label for="h-to">По дату</label>
          <input id="h-to" class="input" type="date" [ngModel]="to()" (ngModelChange)="set(to, $event)" />
        </div>
      </div>
      <div class="row row--between row--wrap">
        <div class="segmented">
          @for (s of statuses; track s[0]) {
            <button [class.active]="status() === s[0]" (click)="set(status, s[0])">{{ s[1] }}</button>
          }
        </div>
        @if (hasFilters()) {
          <button class="btn btn--ghost btn--sm" (click)="clear()"><app-icon name="x" [size]="16" /> Сбросить фильтры</button>
        }
      </div>
    </div>

    <div class="card">
      <app-state [loading]="loading()" [error]="error()" (retry)="reload()"
                 [empty]="!loading() && !error() && groups().length === 0"
                 [emptyTitle]="hasFilters() ? 'По фильтрам ничего не найдено' : 'Операций пока нет'" emptyIcon="history" />
      @if (!loading() && !error()) {
        @for (g of groups(); track g.day) {
          <div class="subtle" style="margin: 12px 4px 4px; font-weight: 600">{{ g.day | dt: 'date' }}</div>
          <div class="op-list">
            @for (op of g.items; track op.id) {
              <app-operation-item [operation]="op" (opened)="selected.set($event)" />
            }
          </div>
        }
        <div class="row row--between" style="margin-top: 8px">
          <span class="subtle">Найдено: {{ data()?.totalElements ?? 0 }}</span>
          <app-pager [page]="page()" [totalPages]="data()?.totalPages ?? 0" (change)="page.set($event)" />
        </div>
      }
    </div>

    @if (selected(); as op) {
      <app-operation-details [operation]="op" (closed)="selected.set(null)" />
    }
  `,
})
export class HistoryPage {
  private readonly api = inject(BankingApi);

  /** ?account=<id> — открыть историю сразу по счёту */
  readonly account = input<string>();

  protected readonly types = Object.entries(OPERATION_TYPES);
  protected readonly statuses: [OperationStatus | '', string][] = [
    ['', 'Все'], ['COMPLETED', 'Выполненные'], ['PENDING', 'В обработке'], ['FAILED', 'Ошибки'],
  ];

  protected readonly accounts = signal<Account[]>([]);
  protected readonly accountId = signal('');
  protected readonly type = signal<OperationType | ''>('');
  protected readonly status = signal<OperationStatus | ''>('');
  protected readonly from = signal('');
  protected readonly to = signal('');
  protected readonly page = signal(0);

  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly data = signal<Page<Operation> | null>(null);
  protected readonly selected = signal<Operation | null>(null);
  private readonly reloadTick = signal(0);

  protected readonly hasFilters = computed(
    () => !!(this.accountId() || this.type() || this.status() || this.from() || this.to()),
  );
  protected readonly groups = computed<DayGroup[]>(() => {
    const result: DayGroup[] = [];
    for (const op of this.data()?.content ?? []) {
      const day = new Intl.DateTimeFormat('sv-SE', { timeZone: 'Europe/Moscow' }).format(new Date(op.createdAt));
      const last = result[result.length - 1];
      if (last?.day === day) last.items.push(op);
      else result.push({ day, items: [op] });
    }
    return result;
  });

  constructor() {
    this.api.accounts().subscribe({ next: (a) => this.accounts.set(a), error: () => {} });
    effect(() => {
      const id = this.account();
      if (id) untracked(() => this.accountId.set(id));
    });
    effect(() => {
      this.reloadTick();
      const filter: HistoryFilter = {
        accountId: this.accountId() || undefined,
        type: this.type(),
        status: this.status(),
        from: this.from() || undefined,
        to: this.to() || undefined,
        page: this.page(),
        size: 20,
      };
      untracked(() => this.fetch(filter));
    });
  }

  protected set<T>(target: { set(v: T): void }, value: T): void {
    target.set(value);
    this.page.set(0);
  }

  protected clear(): void {
    this.accountId.set('');
    this.type.set('');
    this.status.set('');
    this.from.set('');
    this.to.set('');
    this.page.set(0);
  }

  protected reload(): void {
    this.reloadTick.update((n) => n + 1);
  }

  private fetch(filter: HistoryFilter): void {
    this.loading.set(true);
    this.error.set(null);
    this.api.history(filter).subscribe({
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
