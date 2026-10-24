import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { BankingApi } from '../../core/api/banking.api';
import { ProductsApi } from '../../core/api/products.api';
import { errorMessage } from '../../core/errors';
import { Account, Deposit, DepositProduct } from '../../core/models';
import { DateTimePipe, MoneyPipe } from '../../shared/format.pipes';
import { IconComponent } from '../../shared/icon.component';
import { PageHeaderComponent, StateComponent, StatusBadgeComponent } from '../../shared/ui.components';
import { OpenDepositComponent } from './open-deposit.component';

export function depositProgress(d: Deposit): number {
  const start = new Date(d.openDate).getTime();
  const end = new Date(d.maturityDate).getTime();
  const now = new Date(d.accruedThrough).getTime();
  if (end <= start) return 100;
  return Math.max(0, Math.min(100, ((now - start) / (end - start)) * 100));
}

@Component({
  selector: 'app-deposits-page',
  imports: [RouterLink, IconComponent, MoneyPipe, DateTimePipe, PageHeaderComponent, StateComponent, StatusBadgeComponent, OpenDepositComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-page-header title="Вклады" subtitle="Проценты начисляются каждый день и видны сразу" />

    <app-state [loading]="loading()" [error]="error()" (retry)="load()" />

    @if (!loading() && !error()) {
      <div class="stack stack--lg">
        @if (deposits().length) {
          <section class="stack">
            <h2>Мои вклады</h2>
            <div class="grid grid-auto">
              @for (d of deposits(); track d.id) {
                <a class="card card--link stack stack--sm" [routerLink]="['/deposits', d.id]" style="color: inherit">
                  <div class="row row--between">
                    <b>{{ d.productName }}</b>
                    <app-status [status]="d.status" />
                  </div>
                  <div class="stat">
                    <span class="stat__label">{{ d.status === 'CLOSED' ? 'Выплачено' : 'Сейчас на вкладе' }}</span>
                    <span class="stat__value">{{ (d.status === 'CLOSED' ? d.payoutAmount : d.currentValue) | money: d.currency }}</span>
                  </div>
                  <div class="row row--between subtle">
                    <span>{{ d.annualRate }}% · до {{ d.maturityDate | dt: 'date' }}</span>
                    <span class="positive">{{ d.accruedInterest | money: d.currency : true }}</span>
                  </div>
                  @if (d.status === 'ACTIVE') {
                    <div class="progress"><div class="progress__bar" [style.width.%]="progress(d)"></div></div>
                  }
                </a>
              }
            </div>
          </section>
        }

        <section class="stack">
          <h2>Открыть вклад</h2>
          <div class="grid grid-3">
            @for (p of products(); track p.id) {
              <div class="card stack">
                <div class="row row--between">
                  <h3>{{ p.name }}</h3>
                  <span class="chip">{{ p.currency }}</span>
                </div>
                <div class="stat">
                  <span class="stat__value" style="color: var(--primary)">{{ p.annualRate }}%</span>
                  <span class="stat__label">годовых</span>
                </div>
                <p class="muted grow">{{ p.description }}</p>
                <div class="stack stack--sm subtle">
                  <span><app-icon name="calendar" [size]="14" /> от {{ p.minTermMonths }} до {{ p.maxTermMonths }} мес.</span>
                  <span><app-icon name="coins" [size]="14" /> от {{ p.minAmount | money: p.currency }}</span>
                  <span><app-icon name="percent" [size]="14" /> {{ p.capitalization ? 'Капитализация процентов' : 'Проценты в конце срока' }}</span>
                </div>
                <button class="btn" (click)="selected.set(p)">Открыть</button>
              </div>
            }
          </div>
        </section>
      </div>
    }

    @if (selected(); as p) {
      <app-open-deposit [product]="p" [accounts]="accounts()" (closed)="selected.set(null)" (opened)="selected.set(null); load()" />
    }
  `,
})
export class DepositsPage {
  private readonly products$ = inject(ProductsApi);
  private readonly banking = inject(BankingApi);

  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly productList = signal<DepositProduct[]>([]);
  protected readonly deposits = signal<Deposit[]>([]);
  protected readonly accounts = signal<Account[]>([]);
  protected readonly selected = signal<DepositProduct | null>(null);
  protected readonly products = computed(() => this.productList().filter((p) => p.active));
  protected readonly progress = depositProgress;

  constructor() {
    this.load();
  }

  protected load(): void {
    this.loading.set(true);
    this.error.set(null);
    forkJoin({
      products: this.products$.depositProducts(),
      deposits: this.products$.deposits(),
      accounts: this.banking.accounts(),
    }).subscribe({
      next: (r) => {
        this.productList.set(r.products);
        this.deposits.set(r.deposits);
        this.accounts.set(r.accounts);
        this.loading.set(false);
      },
      error: (e) => {
        this.error.set(errorMessage(e));
        this.loading.set(false);
      },
    });
  }
}
