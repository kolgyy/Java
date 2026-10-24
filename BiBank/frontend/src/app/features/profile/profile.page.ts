import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { BankingApi } from '../../core/api/banking.api';
import { AuthService } from '../../core/auth/auth.service';
import { errorMessage } from '../../core/errors';
import { ToastService } from '../../core/toast.service';
import { User } from '../../core/models';
import { DateTimePipe } from '../../shared/format.pipes';
import { IconComponent } from '../../shared/icon.component';
import { PageHeaderComponent, StateComponent, StatusBadgeComponent } from '../../shared/ui.components';

@Component({
  selector: 'app-profile-page',
  imports: [FormsModule, IconComponent, DateTimePipe, PageHeaderComponent, StateComponent, StatusBadgeComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-page-header title="Профиль" />
    <app-state [loading]="loading()" [error]="error()" (retry)="load()" />

    @if (user(); as u) {
      <div class="grid grid-2" style="align-items: start; max-width: 960px">
        <section class="card stack">
          <div class="row">
            <div class="op__icon" style="width: 56px; height: 56px; font-size: 20px; font-weight: 700">{{ initials() }}</div>
            <div>
              <h2>{{ u.fullName }}</h2>
              <div class="muted">{{ u.email }}</div>
            </div>
          </div>
          <dl class="dl">
            <dt>Роль</dt><dd>{{ u.role === 'ROLE_ADMIN' ? 'Администратор' : 'Клиент' }}</dd>
            <dt>Статус</dt><dd><app-status [status]="u.status" /></dd>
            <dt>Клиент с</dt><dd>{{ u.createdAt | dt: 'date' }}</dd>
            <dt>Последний вход</dt><dd>{{ u.lastLoginAt | dt }}</dd>
            <dt>ID клиента</dt><dd class="mono">{{ u.id }}</dd>
          </dl>
        </section>

        <div class="stack">
          <form class="card stack" (ngSubmit)="save()">
            <h3>Личные данные</h3>
            <div class="field">
              <label for="p-name">Имя и фамилия</label>
              <input id="p-name" class="input" name="fullName" [(ngModel)]="fullName" required minlength="2" maxlength="255" />
              <span class="field__hint">Получатели переводов видят имя в сокращённом виде, например «Иван П.»</span>
            </div>
            <button class="btn" type="submit" [disabled]="saving() || fullName.trim().length < 2 || fullName.trim() === u.fullName">
              @if (saving()) { <span class="spinner"></span> } Сохранить
            </button>
          </form>

          <section class="card stack">
            <h3>Безопасность</h3>
            <p class="muted">
              Вход защищён JWT с коротким сроком жизни и одноразовым refresh-токеном.
              Выход завершает текущую сессию на сервере.
            </p>
            <button class="btn btn--ghost danger-text" (click)="auth.logout()"><app-icon name="logout" [size]="18" /> Выйти из аккаунта</button>
          </section>
        </div>
      </div>
    }
  `,
})
export class ProfilePage {
  private readonly api = inject(BankingApi);
  private readonly toast = inject(ToastService);
  protected readonly auth = inject(AuthService);

  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly user = signal<User | null>(null);
  protected readonly saving = signal(false);
  protected fullName = '';

  constructor() {
    this.load();
  }

  protected initials(): string {
    return (this.user()?.fullName ?? '')
      .split(/\s+/)
      .filter(Boolean)
      .slice(0, 2)
      .map((s) => s[0].toUpperCase())
      .join('');
  }

  protected load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.api.me().subscribe({
      next: (u) => {
        this.user.set(u);
        this.fullName = u.fullName;
        this.loading.set(false);
      },
      error: (e) => {
        this.error.set(errorMessage(e));
        this.loading.set(false);
      },
    });
  }

  protected save(): void {
    this.saving.set(true);
    this.api.updateProfile(this.fullName.trim()).subscribe({
      next: (u) => {
        this.saving.set(false);
        this.user.set(u);
        this.auth.updateUser(u);
        this.toast.success('Профиль обновлён');
      },
      error: (e) => {
        this.saving.set(false);
        this.toast.error(errorMessage(e));
      },
    });
  }
}
