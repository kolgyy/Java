import { ChangeDetectionStrategy, Component } from '@angular/core';
import { IconComponent } from '../../shared/icon.component';

@Component({
  selector: 'app-auth-promo',
  imports: [IconComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styles: ':host { display: contents; }',
  template: `
    <aside class="auth__promo">
      <div class="auth__brand"><span>Bi</span><span>BiBank</span></div>
      <div style="position: relative; z-index: 1">
        <h2>Банк, в котором можно всё попробовать</h2>
        <p style="opacity: .85; max-width: 420px">
          Учебная банковская система на микросервисах. Все деньги виртуальные, рисковать нечем.
        </p>
        <ul>
          <li><app-icon name="wallet" /> Счета в рублях, долларах и евро</li>
          <li><app-icon name="send" /> Мгновенные переводы с конвертацией</li>
          <li><app-icon name="piggy" /> Вклады с ежедневным начислением процентов</li>
          <li><app-icon name="credit" /> Кредиты с графиком платежей</li>
        </ul>
      </div>
      <div style="opacity: .6; font-size: 13px">Spring Boot · Kafka · Redis · PostgreSQL · Angular</div>
    </aside>
  `,
})
export class AuthPromoComponent {}
