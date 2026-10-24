// Подписи статусов и типов для интерфейса.

export type Tone = 'success' | 'danger' | 'warning' | 'info' | 'neutral';

export const OPERATION_TYPES: Record<string, string> = {
  TRANSFER: 'Перевод',
  TOP_UP: 'Пополнение',
  LOAN_DISBURSEMENT: 'Выдача кредита',
  LOAN_REPAYMENT: 'Погашение кредита',
  DEPOSIT_OPEN: 'Открытие вклада',
  DEPOSIT_CLOSE: 'Закрытие вклада',
};

const STATUS: Record<string, [string, Tone]> = {
  ACTIVE: ['Активен', 'success'],
  FROZEN: ['Заморожен', 'info'],
  CLOSED: ['Закрыт', 'neutral'],
  BLOCKED: ['Заблокирован', 'danger'],
  PENDING: ['В обработке', 'warning'],
  COMPLETED: ['Выполнено', 'success'],
  FAILED: ['Ошибка', 'danger'],
  SUBMITTED: ['На рассмотрении', 'warning'],
  APPROVED: ['Одобрена', 'success'],
  REJECTED: ['Отклонена', 'danger'],
  CANCELLED: ['Отменена', 'neutral'],
  PENDING_DISBURSEMENT: ['Выдаётся', 'warning'],
  OPENING: ['Открывается', 'warning'],
  CLOSING: ['Закрывается', 'warning'],
  PLANNED: ['Запланирован', 'neutral'],
  PAID: ['Оплачен', 'success'],
  UP: ['Работает', 'success'],
  DOWN: ['Недоступен', 'danger'],
  DEGRADED: ['Частично', 'warning'],
};

export function statusLabel(status: string): string {
  return STATUS[status]?.[0] ?? status;
}

export function statusTone(status: string): Tone {
  return STATUS[status]?.[1] ?? 'neutral';
}

export const ACCOUNT_TYPES: Record<string, string> = {
  MAIN: 'Текущий',
  SAVINGS: 'Накопительный',
};

export const CURRENCY_NAMES: Record<string, string> = {
  RUB: 'Российский рубль',
  USD: 'Доллар США',
  EUR: 'Евро',
};

export function months(n: number): string {
  const mod10 = n % 10;
  const mod100 = n % 100;
  if (mod10 === 1 && mod100 !== 11) return `${n} месяц`;
  if (mod10 >= 2 && mod10 <= 4 && (mod100 < 12 || mod100 > 14)) return `${n} месяца`;
  return `${n} месяцев`;
}

export function uuid(): string {
  if (typeof crypto !== 'undefined' && 'randomUUID' in crypto) {
    return crypto.randomUUID();
  }
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    return (c === 'x' ? r : (r & 0x3) | 0x8).toString(16);
  });
}
