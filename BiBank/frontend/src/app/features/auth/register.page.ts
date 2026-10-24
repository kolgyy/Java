import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { errorMessage, fieldErrors } from '../../core/errors';
import { IconComponent } from '../../shared/icon.component';
import { AuthPromoComponent } from './auth-promo.component';

@Component({
  selector: 'app-register-page',
  imports: [FormsModule, RouterLink, IconComponent, AuthPromoComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="auth">
      <app-auth-promo />
      <main class="auth__panel">
        <form class="auth__form stack stack--lg" (ngSubmit)="submit()" #f="ngForm" novalidate>
          <div>
            <h1>Регистрация</h1>
            <p class="muted">Уже есть аккаунт? <a class="link" routerLink="/login">Войти</a></p>
          </div>

          @if (error()) {
            <div class="alert alert--error"><app-icon name="alert" [size]="18" /> {{ error() }}</div>
          }

          <div class="stack">
            <div class="field">
              <label for="fullName">Имя и фамилия</label>
              <input id="fullName" class="input input--lg" name="fullName" autocomplete="name"
                     [(ngModel)]="fullName" required minlength="2" maxlength="100" />
              @if (errors()['fullName']) { <span class="field__error">{{ errors()['fullName'] }}</span> }
            </div>
            <div class="field">
              <label for="email">Email</label>
              <input id="email" class="input input--lg" type="email" name="email" autocomplete="email"
                     [(ngModel)]="email" required email />
              @if (errors()['email']) { <span class="field__error">{{ errors()['email'] }}</span> }
            </div>
            <div class="field">
              <label for="password">Пароль</label>
              <input id="password" class="input input--lg" type="password" name="password" autocomplete="new-password"
                     [ngModel]="password()" (ngModelChange)="password.set($event)" required minlength="8" />
              <span class="field__hint" [class.positive]="passwordOk()">
                Не короче 8 символов, буквы и цифры
              </span>
              @if (errors()['password']) { <span class="field__error">{{ errors()['password'] }}</span> }
            </div>
            <div class="field">
              <label for="password2">Повторите пароль</label>
              <input id="password2" class="input input--lg" type="password" name="password2" autocomplete="new-password"
                     [ngModel]="password2()" (ngModelChange)="password2.set($event)" required />
              @if (password2() && password2() !== password()) {
                <span class="field__error">Пароли не совпадают</span>
              }
            </div>
          </div>

          <button class="btn btn--lg btn--block" type="submit"
                  [disabled]="loading() || f.invalid || !passwordOk() || password() !== password2()">
            @if (loading()) { <span class="spinner"></span> } Создать аккаунт
          </button>
          <p class="subtle center">Все средства в BiBank виртуальные. Это учебный проект.</p>
        </form>
      </main>
    </div>
  `,
})
export class RegisterPage {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected fullName = '';
  protected email = '';
  protected readonly password = signal('');
  protected readonly password2 = signal('');
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly errors = signal<Record<string, string>>({});
  protected readonly passwordOk = computed(() => {
    const p = this.password();
    return p.length >= 8 && /[A-Za-zА-Яа-я]/.test(p) && /\d/.test(p);
  });

  protected submit(): void {
    if (this.loading()) return;
    this.loading.set(true);
    this.error.set(null);
    this.errors.set({});
    this.auth.register(this.email.trim(), this.password(), this.fullName.trim()).subscribe({
      next: () => this.router.navigate(['/login'], { queryParams: { registered: 1 } }),
      error: (e) => {
        this.loading.set(false);
        this.errors.set(fieldErrors(e));
        this.error.set(errorMessage(e));
      },
    });
  }
}
