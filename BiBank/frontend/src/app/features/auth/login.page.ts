import { ChangeDetectionStrategy, Component, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { errorMessage } from '../../core/errors';
import { IconComponent } from '../../shared/icon.component';
import { AuthPromoComponent } from './auth-promo.component';

@Component({
  selector: 'app-login-page',
  imports: [FormsModule, RouterLink, IconComponent, AuthPromoComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="auth">
      <app-auth-promo />
      <main class="auth__panel">
        <form class="auth__form stack stack--lg" (ngSubmit)="submit()" #f="ngForm" novalidate>
          <div>
            <h1>Вход в BiBank</h1>
            <p class="muted">Нет аккаунта? <a class="link" routerLink="/register">Зарегистрируйтесь</a></p>
          </div>

          @if (expired()) {
            <div class="alert alert--warning"><app-icon name="lock" [size]="18" /> Сессия истекла, войдите снова.</div>
          }
          @if (registered()) {
            <div class="alert alert--success"><app-icon name="check" [size]="18" /> Аккаунт создан. Теперь можно войти.</div>
          }
          @if (error()) {
            <div class="alert alert--error"><app-icon name="alert" [size]="18" /> {{ error() }}</div>
          }

          <div class="stack">
            <div class="field">
              <label for="email">Email</label>
              <input id="email" class="input input--lg" type="email" name="email" autocomplete="username"
                     [(ngModel)]="email" required email autofocus />
            </div>
            <div class="field">
              <label for="password">Пароль</label>
              <input id="password" class="input input--lg" type="password" name="password" autocomplete="current-password"
                     [(ngModel)]="password" required />
            </div>
          </div>

          <button class="btn btn--lg btn--block" type="submit" [disabled]="loading() || f.invalid">
            @if (loading()) { <span class="spinner"></span> } Войти
          </button>

          <div class="demo-box">
            Администратор по умолчанию: <b>admin&#64;bibank.local</b> / <b>Admin12345</b>
            <button type="button" class="link" style="margin-left: 6px" (click)="fillAdmin()">подставить</button>
          </div>
        </form>
      </main>
    </div>
  `,
})
export class LoginPage {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  /** query-параметры маршрута */
  readonly expired = input<string>();
  readonly registered = input<string>();
  readonly returnUrl = input<string>();

  protected email = '';
  protected password = '';
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);

  protected fillAdmin(): void {
    this.email = 'admin@bibank.local';
    this.password = 'Admin12345';
  }

  protected submit(): void {
    if (this.loading()) return;
    this.loading.set(true);
    this.error.set(null);
    this.auth.login(this.email.trim(), this.password).subscribe({
      next: () => this.router.navigateByUrl(this.returnUrl() || '/dashboard'),
      error: (e) => {
        this.loading.set(false);
        this.error.set(errorMessage(e));
      },
    });
  }
}
