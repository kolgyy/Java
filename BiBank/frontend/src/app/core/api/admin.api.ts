import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import {
  Account,
  AccountStats,
  ApplicationStatus,
  DepositProduct,
  HistoryFilter,
  LoanApplication,
  LoanProduct,
  LoanStats,
  Operation,
  Page,
  PaymentStats,
  SystemHealth,
  User,
  UserStats,
} from '../models';
import { toParams } from './params';

@Injectable({ providedIn: 'root' })
export class AdminApi {
  private readonly http = inject(HttpClient);

  health(): Observable<SystemHealth> {
    return this.http.get<SystemHealth>('/api/admin/system/health');
  }

  userStats(): Observable<UserStats> {
    return this.http.get<UserStats>('/api/admin/users/stats');
  }

  accountStats(): Observable<AccountStats> {
    return this.http.get<AccountStats>('/api/admin/accounts/stats');
  }

  paymentStats(): Observable<PaymentStats> {
    return this.http.get<PaymentStats>('/api/admin/transactions/stats');
  }

  loanStats(): Observable<LoanStats> {
    return this.http.get<LoanStats>('/api/admin/loans/stats');
  }

  users(search: string, status: string, page: number, size = 15): Observable<Page<User>> {
    return this.http.get<Page<User>>('/api/admin/users', { params: toParams({ search, status, page, size }) });
  }

  user(id: number): Observable<User> {
    return this.http.get<User>(`/api/admin/users/${id}`);
  }

  setUserStatus(id: number, status: 'ACTIVE' | 'BLOCKED'): Observable<User> {
    return this.http.patch<User>(`/api/admin/users/${id}/status`, { status });
  }

  userAccounts(ownerId: number): Observable<Page<Account>> {
    return this.http.get<Page<Account>>('/api/admin/accounts', { params: toParams({ ownerId, size: 50 }) });
  }

  operations(filter: HistoryFilter): Observable<Page<Operation>> {
    return this.http.get<Page<Operation>>('/api/admin/transactions', { params: toParams(filter) });
  }

  applications(status: ApplicationStatus | '', page: number): Observable<Page<LoanApplication>> {
    return this.http.get<Page<LoanApplication>>('/api/admin/loans/applications', {
      params: toParams({ status, page, size: 15 }),
    });
  }

  approve(id: string, reason?: string): Observable<LoanApplication> {
    return this.http.post<LoanApplication>(`/api/admin/loans/applications/${id}/approve`, { reason });
  }

  reject(id: string, reason?: string): Observable<LoanApplication> {
    return this.http.post<LoanApplication>(`/api/admin/loans/applications/${id}/reject`, { reason });
  }

  updateLoanProduct(id: string, body: { annualRate?: number; active?: boolean }): Observable<LoanProduct> {
    return this.http.patch<LoanProduct>(`/api/admin/loans/products/${id}`, body);
  }

  updateDepositProduct(id: string, body: { annualRate?: number; active?: boolean }): Observable<DepositProduct> {
    return this.http.patch<DepositProduct>(`/api/admin/deposits/products/${id}`, body);
  }

  simulateDays(days: number): Observable<{ deposits: number }> {
    return this.http.post<{ deposits: number }>('/api/admin/deposits/simulate-days', {}, {
      params: toParams({ days }),
    });
  }
}
