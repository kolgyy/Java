import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { BankingApi } from '../../core/api/banking.api';
import { errorMessage } from '../../core/errors';
import { Account } from '../../core/models';
import { AccountTileComponent } from '../../shared/account-tile.component';
import { IconComponent } from '../../shared/icon.component';
import { PageHeaderComponent, StateComponent } from '../../shared/ui.components';
import { OpenAccountComponent } from './open-account.component';

@Component({
  selector: 'app-accounts-page',
  imports: [PageHeaderComponent, StateComponent, IconComponent, AccountTileComponent, OpenAccountComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-page-header title="Счета" subtitle="Текущие и накопительные счета в рублях, долларах и евро">
      <button class="btn" (click)="open.set(true)"><app-icon name="plus" [size]="18" /> Открыть счёт</button>
    </app-page-header>

    <app-state [loading]="loading()" [error]="error()" (retry)="load()"
               [empty]="!loading() && !error() && accounts().length === 0" emptyTitle="Счетов пока нет" emptyIcon="wallet">
      <button class="btn" (click)="open.set(true)">Открыть первый счёт</button>
    </app-state>

    @if (!loading() && !error()) {
      <div class="stack stack--lg">
        @if (active().length) {
          <div class="grid grid-auto">
            @for (a of active(); track a.id) { <app-account-tile [account]="a" /> }
          </div>
        }
        @if (closed().length) {
          <div class="stack">
            <h3 class="muted">Закрытые счета</h3>
            <div class="grid grid-auto">
              @for (a of closed(); track a.id) { <app-account-tile [account]="a" /> }
            </div>
          </div>
        }
      </div>
    }

    @if (open()) {
      <app-open-account (closed)="open.set(false)" (created)="open.set(false); load()" />
    }
  `,
})
export class AccountsPage {
  private readonly api = inject(BankingApi);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly accounts = signal<Account[]>([]);
  protected readonly open = signal(false);
  protected readonly active = computed(() => this.accounts().filter((a) => a.status !== 'CLOSED'));
  protected readonly closed = computed(() => this.accounts().filter((a) => a.status === 'CLOSED'));

  constructor() {
    this.load();
  }

  protected load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.api.accounts().subscribe({
      next: (a) => {
        this.accounts.set(a);
        this.loading.set(false);
      },
      error: (e) => {
        this.error.set(errorMessage(e));
        this.loading.set(false);
      },
    });
  }
}
