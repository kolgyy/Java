// Контракты REST API BiBank (см. Swagger: /swagger-ui.html)

export type Currency = 'RUB' | 'USD' | 'EUR';
export type Role = 'ROLE_USER' | 'ROLE_ADMIN';

export interface ApiError {
  timestamp: string;
  status: number;
  error: string;
  code: string;
  message: string;
  path: string;
  fieldErrors?: Record<string, string>;
}

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

// ---------- auth ----------
export interface User {
  id: number;
  email: string;
  fullName: string;
  role: Role;
  status: 'ACTIVE' | 'BLOCKED';
  createdAt: string;
  lastLoginAt: string | null;
}

export interface LoginResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresIn: number;
  user: User;
}

export interface UserStats {
  totalUsers: number;
  activeUsers: number;
  blockedUsers: number;
  admins: number;
  registeredLast24h: number;
}

// ---------- accounts ----------
export type AccountType = 'MAIN' | 'SAVINGS';
export type AccountStatus = 'ACTIVE' | 'FROZEN' | 'CLOSED';

export interface Account {
  id: string;
  number: string;
  name: string;
  ownerId: number;
  currency: Currency;
  type: AccountType;
  status: AccountStatus;
  balance: number;
  createdAt: string;
  updatedAt: string;
}

export interface AccountLookup {
  number: string;
  currency: Currency;
  ownerName: string;
  active: boolean;
}

export interface AccountStats {
  totalAccounts: number;
  byCurrency: { currency: Currency; accounts: number; totalBalance: number }[];
}

// ---------- payments ----------
export type OperationType =
  | 'TRANSFER'
  | 'TOP_UP'
  | 'LOAN_DISBURSEMENT'
  | 'LOAN_REPAYMENT'
  | 'DEPOSIT_OPEN'
  | 'DEPOSIT_CLOSE';
export type OperationStatus = 'PENDING' | 'COMPLETED' | 'FAILED';
export type Direction = 'IN' | 'OUT' | 'INTERNAL';

export interface Operation {
  id: string;
  type: OperationType;
  status: OperationStatus;
  direction: Direction;
  fromAccountId: string | null;
  fromAccountNumber: string | null;
  toAccountId: string | null;
  toAccountNumber: string | null;
  counterpartyName: string | null;
  amount: number;
  currency: Currency;
  creditedAmount: number;
  creditedCurrency: Currency;
  exchangeRate: number | null;
  description: string | null;
  failureCode: string | null;
  failureReason: string | null;
  createdAt: string;
  completedAt: string | null;
}

export interface TransferRequest {
  fromAccountId: string;
  toAccountNumber: string;
  amount: number;
  description?: string;
}

export interface TransferPreview {
  fromAccountId: string;
  fromAccountNumber: string;
  toAccountNumber: string;
  recipientName: string;
  amount: number;
  currency: Currency;
  creditedAmount: number;
  creditedCurrency: Currency;
  exchangeRate: number;
  fee: number;
  availableBalance: number;
  sufficientFunds: boolean;
}

export interface ExchangeRates {
  base: Currency;
  rates: Record<Currency, number>;
  updatedAt: string;
}

export interface HistoryFilter {
  accountId?: string;
  type?: OperationType | '';
  status?: OperationStatus | '';
  from?: string;
  to?: string;
  page?: number;
  size?: number;
}

export interface PaymentStats {
  totalOperations: number;
  pending: number;
  completed: number;
  failed: number;
  byTypeAndStatus: Record<string, Record<string, number>>;
  transferVolumeByCurrency: Record<string, number>;
}

// ---------- loans ----------
export interface LoanProduct {
  id: string;
  code: string;
  name: string;
  description: string;
  currency: Currency;
  annualRate: number;
  minAmount: number;
  maxAmount: number;
  minTermMonths: number;
  maxTermMonths: number;
  autoApproveLimit: number;
  active: boolean;
}

export interface ScheduleItem {
  number: number;
  dueDate: string;
  payment: number;
  principal: number;
  interest: number;
  remaining: number;
  status: 'PLANNED' | 'PAID';
}

export interface LoanCalculation {
  amount: number;
  currency: Currency;
  annualRate: number;
  termMonths: number;
  monthlyPayment: number;
  totalPayment: number;
  totalInterest: number;
  schedule: ScheduleItem[];
}

export type ApplicationStatus = 'SUBMITTED' | 'APPROVED' | 'REJECTED' | 'CANCELLED';

export interface LoanApplication {
  id: string;
  userId: number;
  productId: string;
  productName: string;
  accountId: string;
  accountNumber: string;
  amount: number;
  currency: Currency;
  termMonths: number;
  annualRate: number;
  monthlyIncome: number | null;
  purpose: string | null;
  status: ApplicationStatus;
  decisionReason: string | null;
  decidedAt: string | null;
  loanId: string | null;
  createdAt: string;
}

export interface LoanPayment {
  id: string;
  accountId: string;
  mode: 'NEXT' | 'FULL';
  amount: number;
  status: 'PENDING' | 'COMPLETED' | 'FAILED';
  failureReason: string | null;
  createdAt: string;
  completedAt: string | null;
}

export interface Loan {
  id: string;
  applicationId: string;
  productName: string;
  accountId: string;
  currency: Currency;
  principal: number;
  annualRate: number;
  termMonths: number;
  monthlyPayment: number;
  outstandingPrincipal: number;
  totalRemainingPayments: number;
  paidInstallments: number;
  nextPaymentDate: string | null;
  nextPaymentAmount: number | null;
  status: 'PENDING_DISBURSEMENT' | 'ACTIVE' | 'CLOSED' | 'CANCELLED';
  issuedAt: string | null;
  closedAt: string | null;
  schedule: ScheduleItem[] | null;
  payments: LoanPayment[] | null;
}

export interface LoanStats {
  applicationsSubmitted: number;
  applicationsApproved: number;
  applicationsRejected: number;
  activeLoans: number;
  closedLoans: number;
  activeDeposits: number;
  closedDeposits: number;
}

// ---------- deposits ----------
export interface DepositProduct {
  id: string;
  code: string;
  name: string;
  description: string;
  currency: Currency;
  annualRate: number;
  earlyCloseRate: number;
  minAmount: number;
  maxAmount: number;
  minTermMonths: number;
  maxTermMonths: number;
  capitalization: boolean;
  active: boolean;
}

export interface Deposit {
  id: string;
  productId: string;
  productName: string;
  accountId: string;
  currency: Currency;
  principal: number;
  annualRate: number;
  earlyCloseRate: number;
  capitalization: boolean;
  termMonths: number;
  openDate: string;
  maturityDate: string;
  accruedThrough: string;
  accruedInterest: number;
  currentValue: number;
  projectedInterest: number;
  status: 'OPENING' | 'ACTIVE' | 'CLOSING' | 'CLOSED' | 'FAILED';
  earlyClosed: boolean;
  payoutAmount: number | null;
  payoutInterest: number | null;
  failureReason: string | null;
  createdAt: string;
  closedAt: string | null;
  accruals: { date: string; base: number; amount: number }[] | null;
}

// ---------- notifications ----------
export interface AppNotification {
  id: string;
  eventType: string;
  level: 'INFO' | 'SUCCESS' | 'WARNING' | 'ERROR';
  title: string;
  message: string;
  read: boolean;
  createdAt: string;
}

// ---------- admin ----------
export interface ServiceHealth {
  name: string;
  status: string;
  latencyMs: number;
}

export interface SystemHealth {
  status: 'UP' | 'DEGRADED';
  checkedAt: string;
  gateway: string;
  services: ServiceHealth[];
}
