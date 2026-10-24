import { ChangeDetectionStrategy, Component, signal } from '@angular/core';
import { PageHeaderComponent } from '../../shared/ui.components';
import { AdminApplicationsComponent } from './admin-applications.component';
import { AdminOperationsComponent } from './admin-operations.component';
import { AdminOverviewComponent } from './admin-overview.component';
import { AdminProductsComponent } from './admin-products.component';
import { AdminUsersComponent } from './admin-users.component';

type Tab = 'overview' | 'users' | 'applications' | 'operations' | 'products';

@Component({
  selector: 'app-admin-page',
  imports: [PageHeaderComponent, AdminOverviewComponent, AdminUsersComponent, AdminApplicationsComponent, AdminOperationsComponent, AdminProductsComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-page-header title="Администрирование" subtitle="Состояние сервисов, клиенты, заявки и продукты" />
    <div class="tabs">
      @for (t of tabs; track t[0]) {
        <button [class.active]="tab() === t[0]" (click)="tab.set(t[0])">{{ t[1] }}</button>
      }
    </div>
    @switch (tab()) {
      @case ('overview') { <app-admin-overview /> }
      @case ('users') { <app-admin-users /> }
      @case ('applications') { <app-admin-applications /> }
      @case ('operations') { <app-admin-operations /> }
      @case ('products') { <app-admin-products /> }
    }
  `,
})
export class AdminPage {
  protected readonly tabs: [Tab, string][] = [
    ['overview', 'Обзор'], ['users', 'Пользователи'], ['applications', 'Заявки на кредит'], ['operations', 'Операции'], ['products', 'Продукты и ставки'],
  ];
  protected readonly tab = signal<Tab>('overview');
}
