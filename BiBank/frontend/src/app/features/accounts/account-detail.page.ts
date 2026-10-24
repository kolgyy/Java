import { ChangeDetectionStrategy, Component, effect, inject, input, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { BankingApi } from '../../core/api/banking.api';
import { errorMessage } from '../../core/errors';
import { ToastService } from '../../core/toast.service';
import { Account, AccountStatus, Operation, Page } from '../../core/models';
import { AccountNumberPipe, DateTimePipe, MoneyPipe } from '../../shared/format.pipes';
import { IconComponent } from '../../shared/icon.component';
import { ACCOUNT_TYPES, CURRENCY_NAMES } from '../../shared/labels';
import { OperationDetailsComponent, OperationItemComponent } from '../../shared/operation.components';
import { ModalComponent, PagerComponent, StateComponent, StatusBadgeComponent } from '../../shared/ui.components';

@Component({
  selector: 'app-account-detail-page',
  imports: [
    RouterLink, IconComponent, MoneyPipe, AccountNumberPipe, DateTimePipe, StateComponent, StatusBadgeComponent,
    PagerComponent, ModalComponent, OperationItemComponent, OperationDetailsComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <a class="link row" routerLink="/accounts" style="margin-bottom: 16px; gap: 4px">
      <app-icon name="chevronLeft" [size]="16" /> Все счета
    </a>

    <app-state [loading]="loading()" [error]="error()" (retry)="load()" />

    @if (account(); as a) {
      <div class="grid grid-3" style="align-items: start">
        <div class="stack">
          <div [class]="'acc-tile acc-tile--' + a.currency" [class.acc-tile--muted]="a.status !== 'ACTIVE'" style="min-height: 190px">
            <div class="row row--between">
              <span class="acc-tile__name">{{ a.name }}</span>
              <app-status [status]="a.status" />
            </div>
            <div>
              <div class="acc-tile__balance" style="font-size: 30px">{{ a.balance | money: a.currency }}</div>
              <div class="acc-tile__number">{{ a.number | accountNumber }}</div>
            </div>
          </div>

          @if (a.status === 'ACTIVE') {
            <div class="grid grid-2">
              <a class="btn" routerLink="/transfer" [queryParams]="{ from: a.id }"><app-icon name="send" [size]="18" /> Перевести</a>
              <a class="btn btn--ghost" routerLink="/transfer" [queryParams]="{ mode: 'topup', to: a.id }">
                <app-icon name="plus" [size]="18" /> Пополнить
              </a>
            </div>
          }

          <section class="card stack">
            <h3>Реквизиты</h3>
            <dl class="dl">
              <dt>Номер</dt>
              <dd class="row" style="gap: 6px">
                <span class="mono">{{ a.number | accountNumber }}</span>
                <button class="icon-btn" (click)="copy(a.number)" aria-label="Скопировать номер"><app-icon name="copy" [size]="16" /></button>
              </dd>
              <dt>Валюта</dt><dd>{{ currencyNames[a.currency] }}</dd>
              <dt>Тип</dt><dd>{{ types[a.type] }}</dd>
              <dt>Открыт</dt><dd>{{ a.createdAt | dt: 'date' }}</dd>
            </dl>
          </section>

          @if (a.status !== 'CLOSED') {
            <section class="card stack stack--sm">
              <h3>Управление</h3>
              @if (a.status === 'ACTIVE') {
                <button class="btn btn--ghost" [disabled]="busy()" (click)="setStatus('FROZEN')">
                  <app-icon name="snow" [size]="18" /> Заморозить
                </button>
              } @else {
                <button class="btn btn--ghost" [disabled]="busy()" (click)="setStatus('ACTIVE')">
                  <app-icon name="unlock" [size]="18" /> Разморозить
                </button>
              }
              <button class="btn btn--ghost danger-text" [disabled]="busy()" (click)="confirmClose.set(true)">
                <app-icon name="x" [size]="18" /> Закрыть счёт
              </button>
              <p class="subtle">Замороженный счёт не участвует в операциях. Закрыть можно только счёт с нулевым остатком.</p>
            </section>
          }
        </div>

        <section class="card span-2">
          <h3 style="margin-bottom: 8px">Операции по счёту</h3>
          <app-state [loading]="historyLoading()" [empty]="!historyLoading() && history()?.content?.length === 0"
                     emptyTitle="Операций по счёту ещё не было" emptyIcon="history" />
          @if (!historyLoading()) {
            <div class="op-list">
              @for (op of history()?.content ?? []; track op.id) {
                <app-operation-item [operation]="op" (opened)="selected.set($event)" />
              }
            </div>
            <app-pager [page]="page()" [totalPages]="history()?.totalPages ?? 0" (change)="page.set($event)" />
          }
        </section>
      </div>
    }

    @if (selected(); as op) {
      <app-operation-details [operation]="op" (closed)="selected.set(null)" />
    }

    @if (confirmClose()) {
      <app-modal title="Закрыть счёт?" (closed)="confirmClose.set(false)">
        <div class="stack">
          @if (account()!.balance > 0) {
            <div class="alert alert--warning">
              На счёте {{ account()!.balance | money: account()!.currency }}. Сначала переведите остаток на другой счёт.
            </div>
          } @else {
            <p>Счёт будет закрыт без возможности восстановления. История операций сохранится.</p>
          }
          <div class="row" style="justify-content: flex-end">
            <button class="btn btn--ghost" (click)="confirmClose.set(false)">Отмена</button>
            <button class="btn btn--danger" [disabled]="busy() || account()!.balance > 0" (click)="setStatus('CLOSED')">Закрыть</button>
          </div>
        </div>
      </app-modal>
    }
  `,
})
export class AccountDetailPage {
  private readonly api = inject(BankingApi);
  private readonly toast = inject(ToastService);
  private readonly router = inject(Router);

  readonly id = input.required<string>();

  protected readonly types = ACCOUNT_TYPES;
  protected readonly currencyNames = CURRENCY_NAMES;
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly account = signal<Account | null>(null);
  protected readonly history = signal<Page<Operation> | null>(null);
  protected readonly historyLoading = signal(true);
  protected readonly page = signal(0);
  protected readonly selected = signal<Operation | null>(null);
  protected readonly busy = signal(false);
  protected readonly confirmClose = signal(false);

  constructor() {
    effect(() => {
      this.id();
      this.load();
    });
    effect(() => this.loadHistory(this.id(), this.page()));
  }

  protected load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.api.account(this.id()).subscribe({
      next: (a) => {
        this.account.set(a);
        this.loading.set(false);
      },
      error: (e) => {
        this.error.set(errorMessage(e));
        this.loading.set(false);
      },
    });
  }

  private loadHistory(accountId: string, page: number): void {
    this.historyLoading.set(true);
    this.api.history({ accountId, page, size: 10 }).subscribe({
      next: (h) => {
        this.history.set(h);
        this.historyLoading.set(false);
      },
      error: () => this.historyLoading.set(false),
    });
  }

  protected setStatus(status: AccountStatus): void {
    this.busy.set(true);
    this.api.changeAccountStatus(this.id(), status).subscribe({
      next: (a) => {
        this.busy.set(false);
        this.confirmClose.set(false);
        this.account.set(a);
        const text = { ACTIVE: 'Счёт разморожен', FROZEN: 'Счёт заморожен', CLOSED: 'Счёт закрыт' }[status];
        this.toast.success(text);
        if (status === 'CLOSED') this.router.navigate(['/accounts']);
      },
      error: (e) => {
        this.busy.set(false);
        this.toast.error(errorMessage(e));
      },
    });
  }

  protected copy(text: string): void {
    navigator.clipboard?.writeText(text).then(() => this.toast.info('Номер счёта скопирован'));
  }
}
