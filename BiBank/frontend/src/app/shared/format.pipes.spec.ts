import { describe, expect, it } from 'vitest';
import { AccountNumberPipe, formatMoney, formatRate } from './format.pipes';
import { months } from './labels';
import { operationAmount, operationTitle } from './operation.components';
import { Operation } from '../core/models';

const nbsp = (s: string) => s.replace(/ | /g, ' ');

describe('formatMoney', () => {
  it('форматирует сумму с валютой', () => {
    expect(nbsp(formatMoney(12345.6, 'RUB'))).toBe('12 345,60 ₽');
  });
  it('ставит знак для зачислений и минус для списаний', () => {
    expect(nbsp(formatMoney(100, 'USD', true))).toBe('+100,00 $');
    expect(nbsp(formatMoney(-5, 'EUR'))).toBe('−5,00 €');
  });
  it('возвращает прочерк для пустого значения', () => {
    expect(formatMoney(null, 'RUB')).toBe('—');
  });
});

describe('formatRate', () => {
  it('показывает курс в привычном направлении', () => {
    expect(nbsp(formatRate(1 / 92.5, 'RUB', 'USD'))).toBe('1 $ = 92,5 ₽');
    expect(nbsp(formatRate(92.5, 'USD', 'RUB'))).toBe('1 $ = 92,5 ₽');
  });
});

describe('AccountNumberPipe', () => {
  const pipe = new AccountNumberPipe();
  it('группирует номер по 4 цифры', () => {
    expect(pipe.transform('40817810123456789012')).toBe('4081 7810 1234 5678 9012');
  });
  it('маскирует номер', () => {
    expect(pipe.transform('40817810123456789012', true)).toBe('•• 9012');
  });
});

describe('months', () => {
  it('склоняет месяцы', () => {
    expect(months(1)).toBe('1 месяц');
    expect(months(3)).toBe('3 месяца');
    expect(months(12)).toBe('12 месяцев');
    expect(months(21)).toBe('21 месяц');
  });
});

describe('operation helpers', () => {
  const base = {
    id: '1', type: 'TRANSFER', status: 'COMPLETED', fromAccountId: 'a', fromAccountNumber: '1', toAccountId: 'b',
    toAccountNumber: '2', counterpartyName: 'Борис П.', amount: 9250, currency: 'RUB', creditedAmount: 100,
    creditedCurrency: 'USD', exchangeRate: 0.0108, description: null, failureCode: null, failureReason: null,
    createdAt: '2026-01-01T10:00:00Z', completedAt: null,
  } as Omit<Operation, 'direction'>;

  it('входящий перевод показывает зачисленную сумму', () => {
    const op = { ...base, direction: 'IN' } as Operation;
    expect(operationAmount(op)).toEqual({ value: 100, currency: 'USD' });
    expect(operationTitle(op)).toBe('Входящий перевод · Борис П.');
  });
  it('исходящий перевод показывает списание со знаком минус', () => {
    const op = { ...base, direction: 'OUT' } as Operation;
    expect(operationAmount(op)).toEqual({ value: -9250, currency: 'RUB' });
  });
});
