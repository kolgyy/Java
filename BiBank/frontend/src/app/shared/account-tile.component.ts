import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Account } from '../core/models';
import { AccountNumberPipe, MoneyPipe } from './format.pipes';
import { ACCOUNT_TYPES } from './labels';
import { StatusBadgeComponent } from './ui.components';

@Component({
  selector: 'app-account-tile',
  imports: [RouterLink, MoneyPipe, AccountNumberPipe, StatusBadgeComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @let a = account();
    <a class="acc-tile" [class]="'acc-tile acc-tile--' + a.currency" [class.acc-tile--muted]="a.status !== 'ACTIVE'"
       [routerLink]="['/accounts', a.id]" style="text-decoration: none">
      <div class="row row--between">
        <span class="acc-tile__name">{{ a.name }}</span>
        @if (a.status !== 'ACTIVE') { <app-status [status]="a.status" /> }
        @else { <span class="badge">{{ types[a.type] }}</span> }
      </div>
      <div>
        <div class="acc-tile__balance">{{ a.balance | money: a.currency }}</div>
        <div class="acc-tile__number">{{ a.number | accountNumber: true }}</div>
      </div>
    </a>
  `,
})
export class AccountTileComponent {
  readonly account = input.required<Account>();
  protected readonly types = ACCOUNT_TYPES;
}
