import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import {
  Deposit,
  DepositProduct,
  Loan,
  LoanApplication,
  LoanCalculation,
  LoanPayment,
  LoanProduct,
} from '../models';

@Injectable({ providedIn: 'root' })
export class ProductsApi {
  private readonly http = inject(HttpClient);

  // ---- кредиты ----
  loanProducts(): Observable<LoanProduct[]> {
    return this.http.get<LoanProduct[]>('/api/loans/products');
  }

  calculate(productId: string, amount: number, termMonths: number): Observable<LoanCalculation> {
    return this.http.post<LoanCalculation>('/api/loans/calculate', { productId, amount, termMonths });
  }

  apply(body: {
    productId: string;
    accountId: string;
    amount: number;
    termMonths: number;
    monthlyIncome?: number | null;
    purpose?: string;
  }): Observable<LoanApplication> {
    return this.http.post<LoanApplication>('/api/loans/applications', body);
  }

  applications(): Observable<LoanApplication[]> {
    return this.http.get<LoanApplication[]>('/api/loans/applications');
  }

  cancelApplication(id: string): Observable<LoanApplication> {
    return this.http.post<LoanApplication>(`/api/loans/applications/${id}/cancel`, {});
  }

  loans(): Observable<Loan[]> {
    return this.http.get<Loan[]>('/api/loans');
  }

  loan(id: string): Observable<Loan> {
    return this.http.get<Loan>(`/api/loans/${id}`);
  }

  repay(loanId: string, accountId: string, mode: 'NEXT' | 'FULL'): Observable<LoanPayment> {
    return this.http.post<LoanPayment>(`/api/loans/${loanId}/payments`, { accountId, mode });
  }

  // ---- вклады ----
  depositProducts(): Observable<DepositProduct[]> {
    return this.http.get<DepositProduct[]>('/api/deposits/products');
  }

  deposits(): Observable<Deposit[]> {
    return this.http.get<Deposit[]>('/api/deposits');
  }

  deposit(id: string): Observable<Deposit> {
    return this.http.get<Deposit>(`/api/deposits/${id}`);
  }

  openDeposit(body: { productId: string; accountId: string; amount: number; termMonths: number }): Observable<Deposit> {
    return this.http.post<Deposit>('/api/deposits', body);
  }

  closeDeposit(id: string): Observable<Deposit> {
    return this.http.post<Deposit>(`/api/deposits/${id}/close`, {});
  }
}
