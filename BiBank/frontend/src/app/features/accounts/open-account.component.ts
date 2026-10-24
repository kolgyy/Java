import { ChangeDetectionStrategy, Component, inject, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { BankingApi } from '../../core/api/banking.api';
import { errorMessage } from '../../core/errors';
import { Account, AccountType, Currency } from '../../core/models';
import { CURRENCY_NAMES } from '../../shared/labels';
import { ModalComponent } from '../../shared/ui.components';
import { ToastService } from '../../core/toast.service';

@Component({
  selector: 'app-open-account',
  imports: [FormsModule, ModalComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-modal title="Открыть новый счёт" (closed)="closed.emit()">
      <form class="stack" (ngSubmit)="submit()">
        <div class="field">
          <span class="field__label">Валюта</span>
          <div class="choice-list">
            @for (c of currencies; track c) {
              <label class="choice" [class.selected]="currency() === c">
                <input type="radio" name="currency" [value]="c" [checked]="currency() === c" (change)="currency.set(c)" />
                <span class="chip">{{ c }}</span>
                <span>{{ names[c] }}</span>
              </label>
            }
          </div>
        </div>
        <div class="field">
          <span class="field__label">Тип счёта</span>
          <div class="segmented">
            <button type="button" [class.active]="type() === 'MAIN'" (click)="type.set('MAIN')">Текущий</button>
            <button type="button" [class.active]="type() === 'SAVINGS'" (click)="type.set('SAVINGS')">Накопительный</button>
          </div>
        </div>
        <div class="field">
          <label for="acc-name">Название (необязательно)</label>
          <input id="acc-name" class="input" name="name" maxlength="60" [(ngModel)]="name" placeholder="Например, «На отпуск»" />
        </div>
        @if (error()) { <div class="alert alert--error">{{ error() }}</div> }
        <button class="btn btn--block" type="submit" [disabled]="loading()">
          @if (loading()) { <span class="spinner"></span> } Открыть счёт
        </button>
      </form>
    </app-modal>
  `,
})
export class OpenAccountComponent {
  private readonly api = inject(BankingApi);
  private readonly toast = inject(ToastService);
  readonly closed = output<void>();
  readonly created = output<Account>();

  protected readonly currencies: Currency[] = ['RUB', 'USD', 'EUR'];
  protected readonly names = CURRENCY_NAMES;
  protected readonly currency = signal<Currency>('RUB');
  protected readonly type = signal<AccountType>('MAIN');
  protected name = '';
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);

  protected submit(): void {
    this.loading.set(true);
    this.error.set(null);
    this.api.openAccount(this.currency(), this.type(), this.name.trim()).subscribe({
      next: (a) => {
        this.toast.success(`Счёт «${a.name}» открыт`);
        this.created.emit(a);
      },
      error: (e) => {
        this.loading.set(false);
        this.error.set(errorMessage(e));
      },
    });
  }
}
