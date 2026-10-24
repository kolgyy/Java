import { ChangeDetectionStrategy, Component, effect, inject, signal, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AdminApi } from '../../core/api/admin.api';
import { errorMessage } from '../../core/errors';
import { ToastService } from '../../core/toast.service';
import { ApplicationStatus, LoanApplication, Page } from '../../core/models';
import { AccountNumberPipe, DateTimePipe, MoneyPipe } from '../../shared/format.pipes';
import { ModalComponent, PagerComponent, StateComponent, StatusBadgeComponent } from '../../shared/ui.components';

@Component({
  selector: 'app-admin-applications',
  imports: [FormsModule, DateTimePipe, MoneyPipe, AccountNumberPipe, StateComponent, StatusBadgeComponent, PagerComponent, ModalComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="card card--pad-0">
      <div class="card__header--pad" style="padding-bottom: 16px">
        <div class="segmented">
          @for (s of statuses; track s[0]) {
            <button [class.active]="status() === s[0]" (click)="status.set(s[0]); page.set(0)">{{ s[1] }}</button>
          }
        </div>
      </div>
      <app-state [loading]="loading()" [error]="error()" (retry)="reload()" [empty]="!loading() && !error() && !data()?.content?.length"
                 emptyTitle="Заявок нет" emptyIcon="credit" />
      @if (!loading() && data()?.content?.length) {
        <div class="table-wrap">
          <table class="table">
            <thead><tr><th>Дата</th><th>Клиент</th><th>Продукт</th><th class="right">Сумма</th><th>Срок</th><th class="right">Доход</th><th>Статус</th><th></th></tr></thead>
            <tbody>
              @for (a of data()!.content; track a.id) {
                <tr>
                  <td class="nowrap">{{ a.createdAt | dt }}</td>
                  <td class="mono">#{{ a.userId }}</td>
                  <td>{{ a.productName }}@if (a.purpose) { <div class="subtle">{{ a.purpose }}</div> }</td>
                  <td class="right nowrap">{{ a.amount | money: a.currency }}</td>
                  <td class="nowrap">{{ a.termMonths }} мес.</td>
                  <td class="right nowrap">{{ a.monthlyIncome !== null ? (a.monthlyIncome | money: a.currency) : '—' }}</td>
                  <td><app-status [status]="a.status" />@if (a.decisionReason) { <div class="subtle">{{ a.decisionReason }}</div> }</td>
                  <td class="right nowrap">
                    @if (a.status === 'SUBMITTED') {
                      <button class="btn btn--success btn--sm" (click)="open(a, 'approve')">Одобрить</button>
                      <button class="btn btn--ghost btn--sm danger-text" (click)="open(a, 'reject')">Отклонить</button>
                    }
                  </td>
                </tr>
              }
            </tbody>
          </table>
        </div>
        <div style="padding: 0 16px 12px"><app-pager [page]="page()" [totalPages]="data()!.totalPages" (change)="page.set($event)" /></div>
      }
    </div>

    @if (decision(); as d) {
      <app-modal [title]="d.action === 'approve' ? 'Одобрить заявку' : 'Отклонить заявку'" (closed)="decision.set(null)">
        <div class="stack">
          <div class="summary">
            <div class="summary__row"><span class="muted">Продукт</span><span>{{ d.app.productName }}</span></div>
            <div class="summary__row"><span class="muted">Сумма</span><b>{{ d.app.amount | money: d.app.currency }}</b></div>
            <div class="summary__row"><span class="muted">Срок</span><span>{{ d.app.termMonths }} мес. под {{ d.app.annualRate }}%</span></div>
            <div class="summary__row"><span class="muted">Счёт зачисления</span><span class="mono">{{ d.app.accountNumber | accountNumber: true }}</span></div>
          </div>
          <div class="field">
            <label for="reason">Комментарий для клиента</label>
            <input id="reason" class="input" maxlength="255" [(ngModel)]="reason"
                   [placeholder]="d.action === 'approve' ? 'Необязательно' : 'Причина отказа'" />
          </div>
          <div class="row" style="justify-content: flex-end">
            <button class="btn btn--ghost" (click)="decision.set(null)">Отмена</button>
            <button class="btn" [class.btn--success]="d.action === 'approve'" [class.btn--danger]="d.action === 'reject'"
                    [disabled]="busy()" (click)="decide()">
              @if (busy()) { <span class="spinner"></span> } {{ d.action === 'approve' ? 'Одобрить и выдать' : 'Отклонить' }}
            </button>
          </div>
        </div>
      </app-modal>
    }
  `,
})
export class AdminApplicationsComponent {
  private readonly api = inject(AdminApi);
  private readonly toast = inject(ToastService);

  protected readonly statuses: [ApplicationStatus | '', string][] = [
    ['SUBMITTED', 'На рассмотрении'], ['APPROVED', 'Одобренные'], ['REJECTED', 'Отклонённые'], ['', 'Все'],
  ];
  protected readonly status = signal<ApplicationStatus | ''>('SUBMITTED');
  protected readonly page = signal(0);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly data = signal<Page<LoanApplication> | null>(null);
  protected readonly decision = signal<{ app: LoanApplication; action: 'approve' | 'reject' } | null>(null);
  protected readonly busy = signal(false);
  protected reason = '';
  private readonly tick = signal(0);

  constructor() {
    effect(() => {
      this.tick();
      const status = this.status();
      const page = this.page();
      untracked(() => this.fetch(status, page));
    });
  }

  protected reload(): void {
    this.tick.update((n) => n + 1);
  }

  private fetch(status: ApplicationStatus | '', page: number): void {
    this.loading.set(true);
    this.error.set(null);
    this.api.applications(status, page).subscribe({
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

  protected open(app: LoanApplication, action: 'approve' | 'reject'): void {
    this.reason = '';
    this.decision.set({ app, action });
  }

  protected decide(): void {
    const d = this.decision();
    if (!d) return;
    this.busy.set(true);
    const call = d.action === 'approve' ? this.api.approve(d.app.id, this.reason.trim() || undefined) : this.api.reject(d.app.id, this.reason.trim() || undefined);
    call.subscribe({
      next: () => {
        this.busy.set(false);
        this.decision.set(null);
        this.toast.success(d.action === 'approve' ? 'Заявка одобрена, кредит выдаётся' : 'Заявка отклонена');
        this.reload();
      },
      error: (e) => {
        this.busy.set(false);
        this.toast.error(errorMessage(e));
      },
    });
  }
}
