import { Routes } from '@angular/router';
import { adminGuard, authGuard, guestGuard } from './core/auth/guards';
import { ShellComponent } from './layout/shell.component';

export const routes: Routes = [
  {
    path: 'login',
    canActivate: [guestGuard],
    title: 'Вход — BiBank',
    loadComponent: () => import('./features/auth/login.page').then((m) => m.LoginPage),
  },
  {
    path: 'register',
    canActivate: [guestGuard],
    title: 'Регистрация — BiBank',
    loadComponent: () => import('./features/auth/register.page').then((m) => m.RegisterPage),
  },
  {
    path: '',
    component: ShellComponent,
    canActivate: [authGuard],
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
      {
        path: 'dashboard',
        title: 'Главная — BiBank',
        loadComponent: () => import('./features/dashboard/dashboard.page').then((m) => m.DashboardPage),
      },
      {
        path: 'accounts',
        title: 'Счета — BiBank',
        loadComponent: () => import('./features/accounts/accounts.page').then((m) => m.AccountsPage),
      },
      {
        path: 'accounts/:id',
        title: 'Счёт — BiBank',
        loadComponent: () => import('./features/accounts/account-detail.page').then((m) => m.AccountDetailPage),
      },
      {
        path: 'transfer',
        title: 'Перевод — BiBank',
        loadComponent: () => import('./features/transfer/transfer.page').then((m) => m.TransferPage),
      },
      {
        path: 'history',
        title: 'История операций — BiBank',
        loadComponent: () => import('./features/history/history.page').then((m) => m.HistoryPage),
      },
      {
        path: 'deposits',
        title: 'Вклады — BiBank',
        loadComponent: () => import('./features/deposits/deposits.page').then((m) => m.DepositsPage),
      },
      {
        path: 'deposits/:id',
        title: 'Вклад — BiBank',
        loadComponent: () => import('./features/deposits/deposit-detail.page').then((m) => m.DepositDetailPage),
      },
      {
        path: 'loans',
        title: 'Кредиты — BiBank',
        loadComponent: () => import('./features/loans/loans.page').then((m) => m.LoansPage),
      },
      {
        path: 'loans/:id',
        title: 'Кредит — BiBank',
        loadComponent: () => import('./features/loans/loan-detail.page').then((m) => m.LoanDetailPage),
      },
      {
        path: 'notifications',
        title: 'Уведомления — BiBank',
        loadComponent: () => import('./features/notifications/notifications.page').then((m) => m.NotificationsPage),
      },
      {
        path: 'profile',
        title: 'Профиль — BiBank',
        loadComponent: () => import('./features/profile/profile.page').then((m) => m.ProfilePage),
      },
      {
        path: 'admin',
        title: 'Администрирование — BiBank',
        canActivate: [adminGuard],
        loadComponent: () => import('./features/admin/admin.page').then((m) => m.AdminPage),
      },
    ],
  },
  { path: '**', redirectTo: '' },
];
