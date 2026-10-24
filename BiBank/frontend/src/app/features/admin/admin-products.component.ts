import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { forkJoin, Observable } from 'rxjs';
import { AdminApi } from '../../core/api/admin.api';
import { ProductsApi } from '../../core/api/products.api';
import { errorMessage } from '../../core/errors';
import { ToastService } from '../../core/toast.service';
import { DepositProduct, LoanProduct } from '../../core/models';
import { MoneyPipe } from '../../shared/format.pipes';
import { IconComponent } from '../../shared/icon.component';
import { StateComponent } from '../../shared/ui.components';

type Kind = 'loan' | 'deposit';

@Component({
  selector: 'app-admin-products',
  imports: [FormsModule, IconComponent, MoneyPipe, StateComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="stack stack--lg">
      <app-state [loading]="loading()" [error]="error()" (retry)="load()" />
      @if (!loading() && !error()) {
        <section class="card card--pad-0">
          <div class="card__header card__header--pad"><h3>Кредитные продукты</h3></div>
          <div class="table-wrap">
            <table class="table">
              <thead><tr><th>Продукт</th><th>Валюта</th><th>Сумма</th><th>Автоодобрение до</th><th style="width: 140px">Ставка, %</th><th>Активен</th><th></th></tr></thead>
              <tbody>
                @for (p of loans(); track p.id) {
                  <tr>
                    <td><b>{{ p.name }}</b><div class="subtle">{{ p.code }}</div></td>
                    <td>{{ p.currency }}</td>
                    <td class="nowrap">{{ p.minAmount | money }} – {{ p.maxAmount | money }}</td>
                    <td class="nowrap">{{ p.autoApproveLimit | money: p.currency }}</td>
                    <td><input class="input" type="number" step="0.1" min="0.1" max="100" [(ngModel)]="rates[p.id]" /></td>
                    <td><input type="checkbox" [checked]="p.active" (change)="save('loan', p.id, { active: !p.active })" /></td>
                    <td class="right">
                      <button class="btn btn--ghost btn--sm" [disabled]="+rates[p.id] === p.annualRate" (click)="save('loan', p.id, { annualRate: +rates[p.id] })">Сохранить</button>
                    </td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
        </section>

        <section class="card card--pad-0">
          <div class="card__header card__header--pad"><h3>Вклады</h3></div>
          <div class="table-wrap">
            <table class="table">
              <thead><tr><th>Продукт</th><th>Валюта</th><th>Срок, мес.</th><th>Досрочно</th><th style="width: 140px">Ставка, %</th><th>Активен</th><th></th></tr></thead>
              <tbody>
                @for (p of deposits(); track p.id) {
                  <tr>
                    <td><b>{{ p.name }}</b><div class="subtle">{{ p.code }}{{ p.capitalization ? ' · капитализация' : '' }}</div></td>
                    <td>{{ p.currency }}</td>
                    <td>{{ p.minTermMonths }}–{{ p.maxTermMonths }}</td>
                    <td>{{ p.earlyCloseRate }}%</td>
                    <td><input class="input" type="number" step="0.1" min="0.1" max="100" [(ngModel)]="rates[p.id]" /></td>
                    <td><input type="checkbox" [checked]="p.active" (change)="save('deposit', p.id, { active: !p.active })" /></td>
                    <td class="right">
                      <button class="btn btn--ghost btn--sm" [disabled]="+rates[p.id] === p.annualRate" (click)="save('deposit', p.id, { annualRate: +rates[p.id] })">Сохранить</button>
                    </td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
          <p class="subtle" style="padding: 0 20px 16px">Новая ставка действует для новых вкладов и кредитов. Каталог кэшируется в Redis и сбрасывается при изменении.</p>
        </section>

        <section class="card stack">
          <h3>Машина времени</h3>
          <p class="muted">Начислить проценты по всем активным вкладам за несколько дней вперёд, чтобы увидеть доход и закрытие по сроку без ожидания.</p>
          <div class="row row--wrap">
            <div class="input-group" style="width: 160px">
              <input class="input" type="number" min="1" max="400" [(ngModel)]="days" />
              <span class="input-group__suffix">дн.</span>
            </div>
            <button class="btn" [disabled]="simulating() || days < 1 || days > 400" (click)="simulate()">
              @if (simulating()) { <span class="spinner"></span> } <app-icon name="forward" [size]="18" /> Промотать
            </button>
          </div>
        </section>
      }
    </div>
  `,
})
export class AdminProductsComponent {
  private readonly admin = inject(AdminApi);
  private readonly products = inject(ProductsApi);
  private readonly toast = inject(ToastService);

  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly loans = signal<LoanProduct[]>([]);
  protected readonly deposits = signal<DepositProduct[]>([]);
  protected readonly simulating = signal(false);
  protected rates: Record<string, number> = {};
  protected days = 30;

  constructor() {
    this.load();
  }

  protected load(): void {
    this.loading.set(true);
    this.error.set(null);
    forkJoin({ loans: this.products.loanProducts(), deposits: this.products.depositProducts() }).subscribe({
      next: (r) => {
        this.loans.set(r.loans);
        this.deposits.set(r.deposits);
        this.rates = {};
        for (const p of [...r.loans, ...r.deposits]) this.rates[p.id] = p.annualRate;
        this.loading.set(false);
      },
      error: (e) => {
        this.error.set(errorMessage(e));
        this.loading.set(false);
      },
    });
  }

  protected save(kind: Kind, id: string, body: { annualRate?: number; active?: boolean }): void {
    const call: Observable<LoanProduct | DepositProduct> =
      kind === 'loan' ? this.admin.updateLoanProduct(id, body) : this.admin.updateDepositProduct(id, body);
    call.subscribe({
      next: (p) => {
        this.toast.success(`«${p.name}» обновлён`);
        this.rates[p.id] = p.annualRate;
        if (kind === 'loan') this.loans.update((l) => l.map((x) => (x.id === p.id ? (p as LoanProduct) : x)));
        else this.deposits.update((l) => l.map((x) => (x.id === p.id ? (p as DepositProduct) : x)));
      },
      error: (e) => this.toast.error(errorMessage(e)),
    });
  }

  protected simulate(): void {
    this.simulating.set(true);
    this.admin.simulateDays(this.days).subscribe({
      next: (r) => {
        this.simulating.set(false);
        this.toast.success(`Проценты начислены за ${this.days} дн. по ${r.deposits} вкладам`);
      },
      error: (e) => {
        this.simulating.set(false);
        this.toast.error(errorMessage(e));
      },
    });
  }
}
