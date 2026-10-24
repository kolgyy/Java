import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { Subject, debounceTime, distinctUntilChanged, switchMap, of, catchError } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { BankingApi } from '../../core/api/banking.api';
import { NotificationsApi } from '../../core/api/notifications.api';
import { errorMessage } from '../../core/errors';
import { Account, AccountLookup, Operation, TransferPreview, TransferRequest } from '../../core/models';
import { AccountNumberPipe, MoneyPipe, RatePipe } from '../../shared/format.pipes';
import { IconComponent } from '../../shared/icon.component';
import { uuid } from '../../shared/labels';
import { PageHeaderComponent, StateComponent } from '../../shared/ui.components';

type Mode = 'transfer' | 'topup';
type Step = 'form' | 'confirm' | 'result';

@Component({
  selector: 'app-transfer-page',
  imports: [FormsModule, RouterLink, IconComponent, MoneyPipe, RatePipe, AccountNumberPipe, PageHeaderComponent, StateComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-page-header [title]="activeMode() === 'transfer' ? 'Перевод' : 'Пополнение счёта'"
                     [subtitle]="activeMode() === 'transfer' ? 'Между своими счетами или клиенту BiBank по номеру счёта' : 'Зачисление виртуальных средств на ваш счёт'">
      <div class="segmented">
        <button [class.active]="activeMode() === 'transfer'" (click)="switchMode('transfer')">Перевод</button>
        <button [class.active]="activeMode() === 'topup'" (click)="switchMode('topup')">Пополнение</button>
      </div>
    </app-page-header>

    <div style="max-width: 620px">
      <app-state [loading]="loading()" [error]="loadError()" (retry)="loadAccounts()"
                 [empty]="!loading() && !loadError() && usable().length === 0" emptyTitle="Нет активных счетов"
                 emptyText="Откройте счёт, чтобы выполнять операции" emptyIcon="wallet">
        <a class="btn" routerLink="/accounts">К счетам</a>
      </app-state>

      @if (!loading() && usable().length) {
        <div class="card">
          @if (activeMode() === 'transfer') {
            <div class="steps">
              <div class="steps__item done"></div>
              <div class="steps__item" [class.done]="step() !== 'form'"></div>
              <div class="steps__item" [class.done]="step() === 'result'"></div>
            </div>
          }

          @switch (step()) {
            @case ('form') {
              @if (activeMode() === 'transfer') {
                <form class="stack stack--lg" (ngSubmit)="preview()">
                  <div class="field">
                    <label for="from">Откуда</label>
                    <select id="from" class="input input--lg" name="from" [ngModel]="fromId()" (ngModelChange)="fromId.set($event)">
                      @for (a of usable(); track a.id) {
                        <option [value]="a.id">{{ a.name }} · {{ a.number | accountNumber: true }} · {{ a.balance | money: a.currency }}</option>
                      }
                    </select>
                  </div>

                  <div class="field">
                    <span class="field__label">Куда</span>
                    <div class="segmented" style="align-self: flex-start">
                      <button type="button" [class.active]="target() === 'own'" (click)="target.set('own')">На свой счёт</button>
                      <button type="button" [class.active]="target() === 'other'" (click)="target.set('other')">По номеру счёта</button>
                    </div>
                  </div>

                  @if (target() === 'own') {
                    <div class="field">
                      <label for="to-own">Счёт зачисления</label>
                      @if (ownTargets().length) {
                        <select id="to-own" class="input input--lg" name="toOwn" [ngModel]="toOwnNumber()" (ngModelChange)="toOwnNumber.set($event)">
                          @for (a of ownTargets(); track a.id) {
                            <option [value]="a.number">{{ a.name }} · {{ a.number | accountNumber: true }} · {{ a.currency }}</option>
                          }
                        </select>
                      } @else {
                        <div class="alert alert--info">У вас нет второго активного счёта. <a class="link" routerLink="/accounts">Открыть счёт</a></div>
                      }
                    </div>
                  } @else {
                    <div class="field">
                      <label for="to-number">Номер счёта получателя</label>
                      <input id="to-number" class="input input--lg mono" name="toNumber" inputmode="numeric" maxlength="24"
                             placeholder="20 цифр" [ngModel]="toNumber()" (ngModelChange)="onNumber($event)" />
                      @if (lookup(); as l) {
                        <span class="field__hint" [class.positive]="l.active" [class.danger-text]="!l.active">
                          @if (l.active) { <app-icon name="check" [size]="14" /> {{ l.ownerName }} · счёт в {{ l.currency }} }
                          @else { Счёт получателя не активен }
                        </span>
                      } @else if (lookupError()) {
                        <span class="field__error">{{ lookupError() }}</span>
                      } @else {
                        <span class="field__hint">Получатель определится автоматически</span>
                      }
                    </div>
                  }

                  <div class="field">
                    <label for="amount">Сумма</label>
                    <div class="input-group">
                      <input id="amount" class="input input--lg" name="amount" type="number" min="0.01" step="0.01"
                             [ngModel]="amount()" (ngModelChange)="amount.set($event)" placeholder="0,00" />
                      <span class="input-group__suffix">{{ from()?.currency }}</span>
                    </div>
                    @if (from(); as f) {
                      <span class="field__hint">Доступно {{ f.balance | money: f.currency }}</span>
                    }
                  </div>

                  <div class="field">
                    <label for="description">Комментарий</label>
                    <input id="description" class="input" name="description" maxlength="255" [(ngModel)]="description"
                           placeholder="Необязательно" />
                  </div>

                  @if (error()) { <div class="alert alert--error"><app-icon name="alert" [size]="18" /> {{ error() }}</div> }

                  <button class="btn btn--lg btn--block" type="submit" [disabled]="busy() || !canPreview()">
                    @if (busy()) { <span class="spinner"></span> } Продолжить
                  </button>
                </form>
              } @else {
                <form class="stack stack--lg" (ngSubmit)="topUp()">
                  <div class="field">
                    <label for="topup-to">Счёт</label>
                    <select id="topup-to" class="input input--lg" name="to" [ngModel]="topUpId()" (ngModelChange)="topUpId.set($event)">
                      @for (a of usable(); track a.id) {
                        <option [value]="a.id">{{ a.name }} · {{ a.number | accountNumber: true }} · {{ a.balance | money: a.currency }}</option>
                      }
                    </select>
                  </div>
                  <div class="field">
                    <label for="topup-amount">Сумма</label>
                    <div class="input-group">
                      <input id="topup-amount" class="input input--lg" name="amount" type="number" min="0.01" step="0.01"
                             [ngModel]="amount()" (ngModelChange)="amount.set($event)" placeholder="0,00" />
                      <span class="input-group__suffix">{{ topUpAccount()?.currency }}</span>
                    </div>
                    <div class="row row--wrap" style="gap: 8px; margin-top: 4px">
                      @for (v of quick; track v) {
                        <button type="button" class="chip" style="cursor: pointer" (click)="amount.set(v)">{{ v | money }}</button>
                      }
                    </div>
                    <span class="field__hint">Не более 1 000 000 за операцию</span>
                  </div>
                  @if (error()) { <div class="alert alert--error"><app-icon name="alert" [size]="18" /> {{ error() }}</div> }
                  <button class="btn btn--lg btn--block btn--success" type="submit" [disabled]="busy() || !(amount()! > 0)">
                    @if (busy()) { <span class="spinner"></span> } Пополнить
                  </button>
                </form>
              }
            }

            @case ('confirm') {
              @if (quote(); as q) {
                <div class="stack stack--lg">
                  <h2>Проверьте перевод</h2>
                  <div class="summary">
                    <div class="summary__row"><span class="muted">Со счёта</span><span class="mono">{{ q.fromAccountNumber | accountNumber: true }}</span></div>
                    <div class="summary__row"><span class="muted">Получатель</span><b>{{ q.recipientName }}</b></div>
                    <div class="summary__row"><span class="muted">Счёт получателя</span><span class="mono">{{ q.toAccountNumber | accountNumber }}</span></div>
                    <div class="summary__row"><span class="muted">Сумма</span><span>{{ q.amount | money: q.currency }}</span></div>
                    @if (q.creditedCurrency !== q.currency) {
                      <div class="summary__row"><span class="muted">Курс</span><span>{{ q.exchangeRate | rate: q.currency : q.creditedCurrency }}</span></div>
                      <div class="summary__row"><span class="muted">Будет зачислено</span><span>{{ q.creditedAmount | money: q.creditedCurrency }}</span></div>
                    }
                    <div class="summary__row"><span class="muted">Комиссия</span><span>{{ q.fee | money: q.currency }}</span></div>
                    <div class="summary__row summary__row--total"><span>Итого к списанию</span><span>{{ q.amount + q.fee | money: q.currency }}</span></div>
                  </div>
                  @if (!q.sufficientFunds) {
                    <div class="alert alert--warning">
                      <app-icon name="alert" [size]="18" /> Недостаточно средств: доступно {{ q.availableBalance | money: q.currency }}
                    </div>
                  }
                  @if (error()) { <div class="alert alert--error"><app-icon name="alert" [size]="18" /> {{ error() }}</div> }
                  <div class="grid grid-2">
                    <button class="btn btn--ghost btn--lg" [disabled]="busy()" (click)="back()">Изменить</button>
                    <button class="btn btn--lg" [disabled]="busy() || !q.sufficientFunds" (click)="confirm()">
                      @if (busy()) { <span class="spinner"></span> } {{ error() ? 'Повторить' : 'Перевести' }}
                    </button>
                  </div>
                  <p class="subtle center">
                    Повторное нажатие безопасно: у операции есть ключ идемпотентности, деньги не спишутся дважды.
                  </p>
                </div>
              }
            }

            @case ('result') {
              @if (result(); as r) {
                <div class="stack stack--lg center">
                  <div class="big-check" [class.big-check--ok]="r.status === 'COMPLETED'"
                       [class.big-check--fail]="r.status === 'FAILED'" [class.big-check--wait]="r.status === 'PENDING'">
                    <app-icon [name]="r.status === 'COMPLETED' ? 'check' : r.status === 'FAILED' ? 'x' : 'refresh'" [size]="32" />
                  </div>
                  <div>
                    <h2>{{ resultTitle() }}</h2>
                    <div class="hero__value" style="font-size: 30px">{{ r.amount | money: r.currency }}</div>
                    @if (r.creditedCurrency !== r.currency && r.status === 'COMPLETED') {
                      <p class="muted">Зачислено {{ r.creditedAmount | money: r.creditedCurrency }} по курсу {{ r.exchangeRate | rate: r.currency : r.creditedCurrency }}</p>
                    }
                  </div>
                  @if (r.status === 'FAILED') { <div class="alert alert--error" style="text-align: left">{{ r.failureReason }}</div> }
                  @if (r.status === 'PENDING') {
                    <div class="alert alert--warning" style="text-align: left">
                      Сервис счетов отвечает с задержкой. Операция будет завершена автоматически, статус появится в истории.
                    </div>
                  }
                  <div class="grid grid-2">
                    <a class="btn btn--ghost" routerLink="/history">В историю</a>
                    <button class="btn" (click)="reset()">{{ activeMode() === 'transfer' ? 'Новый перевод' : 'Пополнить ещё' }}</button>
                  </div>
                </div>
              }
            }
          }
        </div>
      }
    </div>
  `,
})
export class TransferPage {
  private readonly api = inject(BankingApi);
  private readonly notifications = inject(NotificationsApi);
  private readonly router = inject(Router);

  // query-параметры: ?mode=topup&from=<id>&to=<номер или id>
  readonly mode = input<string>();
  readonly from_ = input<string | undefined>(undefined, { alias: 'from' });
  readonly to = input<string>();

  protected readonly quick = [1000, 5000, 10000, 50000];
  protected readonly activeMode = signal<Mode>('transfer');
  protected readonly step = signal<Step>('form');
  protected readonly loading = signal(true);
  protected readonly loadError = signal<string | null>(null);
  protected readonly accounts = signal<Account[]>([]);
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly fromId = signal('');
  protected readonly target = signal<'own' | 'other'>('own');
  protected readonly toOwnNumber = signal('');
  protected readonly toNumber = signal('');
  protected readonly amount = signal<number | null>(null);
  protected description = '';
  protected readonly topUpId = signal('');

  protected readonly lookup = signal<AccountLookup | null>(null);
  protected readonly lookupError = signal<string | null>(null);
  protected readonly quote = signal<TransferPreview | null>(null);
  protected readonly result = signal<Operation | null>(null);

  /** Ключ идемпотентности живёт, пока пользователь не изменит данные операции. */
  private idempotencyKey = uuid();
  private pendingRequest: TransferRequest | null = null;
  private readonly numbers = new Subject<string>();

  protected readonly usable = computed(() => this.accounts().filter((a) => a.status === 'ACTIVE'));
  protected readonly from = computed(() => this.usable().find((a) => a.id === this.fromId()) ?? null);
  protected readonly ownTargets = computed(() => this.usable().filter((a) => a.id !== this.fromId()));
  protected readonly topUpAccount = computed(() => this.usable().find((a) => a.id === this.topUpId()) ?? null);
  protected readonly destination = computed(() =>
    this.target() === 'own' ? this.toOwnNumber() : this.toNumber().replace(/\D/g, ''),
  );
  protected readonly canPreview = computed(
    () => !!this.from() && /^\d{20}$/.test(this.destination()) && (this.amount() ?? 0) > 0,
  );
  protected readonly resultTitle = computed(() => {
    const r = this.result();
    if (!r) return '';
    if (r.status === 'PENDING') return 'Операция в обработке';
    if (r.status === 'FAILED') return 'Операция не выполнена';
    return r.type === 'TOP_UP' ? 'Счёт пополнен' : 'Перевод выполнен';
  });

  constructor() {
    this.loadAccounts();

    effect(() => {
      const mode = this.mode() === 'topup' ? 'topup' : 'transfer';
      untracked(() => {
        this.activeMode.set(mode);
        this.reset();
      });
    });

    // Выбор счёта списания из query-параметров и корректный счёт зачисления.
    effect(() => {
      const list = this.usable();
      if (!list.length) return;
      untracked(() => {
        const from = this.from_();
        const to = this.to();
        if (!this.fromId() || !list.some((a) => a.id === this.fromId())) {
          this.fromId.set(list.find((a) => a.id === from)?.id ?? list[0].id);
        }
        if (!this.topUpId()) {
          this.topUpId.set(list.find((a) => a.id === to)?.id ?? list[0].id);
        }
        if (to && /^\d{20}$/.test(to) && !this.toNumber()) {
          const own = list.find((a) => a.number === to);
          if (own) {
            this.target.set('own');
            this.toOwnNumber.set(to);
          } else {
            this.target.set('other');
            this.onNumber(to);
          }
        }
      });
    });

    effect(() => {
      const targets = this.ownTargets();
      untracked(() => {
        if (!targets.some((a) => a.number === this.toOwnNumber())) {
          this.toOwnNumber.set(targets[0]?.number ?? '');
        }
      });
    });

    this.numbers
      .pipe(
        debounceTime(300),
        distinctUntilChanged(),
        switchMap((n) =>
          /^\d{20}$/.test(n)
            ? this.api.lookup(n).pipe(catchError((e) => of(errorMessage(e))))
            : of(null),
        ),
        takeUntilDestroyed(),
      )
      .subscribe((r) => {
        this.lookup.set(r && typeof r === 'object' ? r : null);
        this.lookupError.set(typeof r === 'string' ? r : null);
      });
  }

  protected loadAccounts(): void {
    this.loading.set(true);
    this.loadError.set(null);
    this.api.accounts().subscribe({
      next: (a) => {
        this.accounts.set(a);
        this.loading.set(false);
      },
      error: (e) => {
        this.loadError.set(errorMessage(e));
        this.loading.set(false);
      },
    });
  }

  protected switchMode(mode: Mode): void {
    this.router.navigate([], { queryParams: { mode: mode === 'topup' ? 'topup' : null }, queryParamsHandling: 'merge' });
  }

  protected onNumber(value: string): void {
    this.toNumber.set(value);
    this.lookup.set(null);
    this.lookupError.set(null);
    this.numbers.next(value.replace(/\D/g, ''));
  }

  protected preview(): void {
    if (!this.canPreview()) return;
    const request: TransferRequest = {
      fromAccountId: this.fromId(),
      toAccountNumber: this.destination(),
      amount: Number(this.amount()),
      description: this.description.trim() || undefined,
    };
    if (JSON.stringify(request) !== JSON.stringify(this.pendingRequest)) {
      // Новые данные — новая операция и новый ключ.
      this.idempotencyKey = uuid();
      this.pendingRequest = request;
    }
    this.busy.set(true);
    this.error.set(null);
    this.api.previewTransfer(request).subscribe({
      next: (q) => {
        this.quote.set(q);
        this.busy.set(false);
        this.step.set('confirm');
      },
      error: (e) => {
        this.busy.set(false);
        this.error.set(errorMessage(e));
      },
    });
  }

  protected confirm(): void {
    if (!this.pendingRequest) return;
    this.busy.set(true);
    this.error.set(null);
    this.api.transfer(this.pendingRequest, this.idempotencyKey).subscribe({
      next: (op) => this.finish(op),
      error: (e) => {
        // Ключ сохраняется: повторная отправка вернёт ту же операцию, а не создаст новую.
        this.busy.set(false);
        this.error.set(errorMessage(e));
      },
    });
  }

  protected topUp(): void {
    const amount = Number(this.amount());
    const key = `${this.topUpId()}:${amount}`;
    if (key !== this.topUpFingerprint) {
      this.idempotencyKey = uuid();
      this.topUpFingerprint = key;
    }
    this.busy.set(true);
    this.error.set(null);
    this.api.topUp(this.topUpId(), amount, this.idempotencyKey).subscribe({
      next: (op) => this.finish(op),
      error: (e) => {
        this.busy.set(false);
        this.error.set(errorMessage(e));
      },
    });
  }
  private topUpFingerprint = '';

  private finish(op: Operation): void {
    this.busy.set(false);
    this.result.set(op);
    this.step.set('result');
    this.loadAccounts();
    setTimeout(() => this.notifications.refreshUnread().subscribe({ error: () => {} }), 1500);
  }

  protected back(): void {
    this.error.set(null);
    this.step.set('form');
  }

  protected reset(): void {
    this.step.set('form');
    this.error.set(null);
    this.amount.set(null);
    this.description = '';
    this.quote.set(null);
    this.result.set(null);
    this.pendingRequest = null;
    this.topUpFingerprint = '';
    this.idempotencyKey = uuid();
  }
}
