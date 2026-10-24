import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter, interval, startWith, switchMap } from 'rxjs';
import { NotificationsApi } from '../core/api/notifications.api';
import { AuthService } from '../core/auth/auth.service';
import { IconComponent } from '../shared/icon.component';

interface NavItem {
  path: string;
  label: string;
  icon: string;
}

@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, IconComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './shell.component.html',
  styleUrl: './shell.component.scss',
})
export class ShellComponent implements OnInit {
  protected readonly auth = inject(AuthService);
  protected readonly notifications = inject(NotificationsApi);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly menuOpen = signal(false);
  protected readonly initials = computed(() => {
    const name = this.auth.user()?.fullName ?? '';
    return name.split(/\s+/).filter(Boolean).slice(0, 2).map((p) => p[0]).join('').toUpperCase() || 'B';
  });

  protected readonly nav: NavItem[] = [
    { path: '/dashboard', label: 'Главная', icon: 'home' },
    { path: '/accounts', label: 'Счета', icon: 'wallet' },
    { path: '/transfer', label: 'Переводы', icon: 'send' },
    { path: '/history', label: 'История', icon: 'history' },
    { path: '/deposits', label: 'Вклады', icon: 'piggy' },
    { path: '/loans', label: 'Кредиты', icon: 'credit' },
    { path: '/notifications', label: 'Уведомления', icon: 'bell' },
  ];

  ngOnInit(): void {
    // Обновление счётчика непрочитанных уведомлений каждые 20 секунд
    interval(20000)
      .pipe(
        startWith(0),
        switchMap(() => this.notifications.refreshUnread()),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({ error: () => {} });

    this.router.events
      .pipe(filter((e) => e instanceof NavigationEnd), takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.menuOpen.set(false));
  }

  protected logout(): void {
    this.auth.logout();
  }
}
