import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import {
  Account,
  AccountLookup,
  AccountStatus,
  AccountType,
  Currency,
  ExchangeRates,
  HistoryFilter,
  Operation,
  Page,
  TransferPreview,
  TransferRequest,
  User,
} from '../models';
import { toParams } from './params';

@Injectable({ providedIn: 'root' })
export class BankingApi {
  private readonly http = inject(HttpClient);

  // ---- профиль ----
  me(): Observable<User> {
    return this.http.get<User>('/api/users/me');
  }

  updateProfile(fullName: string): Observable<User> {
    return this.http.patch<User>('/api/users/me', { fullName });
  }

  // ---- счета ----
  accounts(): Observable<Account[]> {
    return this.http.get<Account[]>('/api/accounts');
  }

  account(id: string): Observable<Account> {
    return this.http.get<Account>(`/api/accounts/${id}`);
  }

  openAccount(currency: Currency, type: AccountType, name?: string): Observable<Account> {
    return this.http.post<Account>('/api/accounts', { currency, type, name: name || undefined });
  }

  changeAccountStatus(id: string, status: AccountStatus): Observable<Account> {
    return this.http.patch<Account>(`/api/accounts/${id}/status`, { status });
  }

  lookup(number: string): Observable<AccountLookup> {
    return this.http.get<AccountLookup>('/api/accounts/lookup', { params: toParams({ number }) });
  }

  // ---- переводы и операции ----
  previewTransfer(request: TransferRequest): Observable<TransferPreview> {
    return this.http.post<TransferPreview>('/api/transfers/preview', request);
  }

  /** Idempotency-Key защищает от повторного списания при повторной отправке. */
  transfer(request: TransferRequest, idempotencyKey: string): Observable<Operation> {
    return this.http.post<Operation>('/api/transfers', request, {
      headers: new HttpHeaders({ 'Idempotency-Key': idempotencyKey }),
    });
  }

  topUp(accountId: string, amount: number, idempotencyKey: string): Observable<Operation> {
    return this.http.post<Operation>('/api/top-ups', { accountId, amount }, {
      headers: new HttpHeaders({ 'Idempotency-Key': idempotencyKey }),
    });
  }

  history(filter: HistoryFilter): Observable<Page<Operation>> {
    return this.http.get<Page<Operation>>('/api/transactions', { params: toParams(filter) });
  }

  operation(id: string): Observable<Operation> {
    return this.http.get<Operation>(`/api/transactions/${id}`);
  }

  rates(): Observable<ExchangeRates> {
    return this.http.get<ExchangeRates>('/api/exchange-rates');
  }
}
