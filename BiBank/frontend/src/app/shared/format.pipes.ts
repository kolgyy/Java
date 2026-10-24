import { Pipe, PipeTransform } from '@angular/core';

const SYMBOLS: Record<string, string> = { RUB: '₽', USD: '$', EUR: '€' };

export function formatMoney(value: number | null | undefined, currency?: string | null, signed = false): string {
  if (value === null || value === undefined || isNaN(Number(value))) {
    return '—';
  }
  const n = Number(value);
  const text = new Intl.NumberFormat('ru-RU', { minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(Math.abs(n));
  const sign = n < 0 ? '−' : signed && n > 0 ? '+' : '';
  return `${sign}${text}${currency ? ' ' + (SYMBOLS[currency] ?? currency) : ''}`;
}

/** 12345.6 | money:'RUB' → «12 345,60 ₽» */
@Pipe({ name: 'money' })
export class MoneyPipe implements PipeTransform {
  transform(value: number | null | undefined, currency?: string | null, signed = false): string {
    return formatMoney(value, currency, signed);
  }
}

/** 40817810123456789012 → «40817 810 1 2345 6789012» с группировкой по 4 */
@Pipe({ name: 'accountNumber' })
export class AccountNumberPipe implements PipeTransform {
  transform(value: string | null | undefined, masked = false): string {
    if (!value) {
      return '—';
    }
    if (masked) {
      return '•• ' + value.slice(-4);
    }
    return value.replace(/(\d{4})(?=\d)/g, '$1 ');
  }
}

@Pipe({ name: 'currencySymbol' })
export class CurrencySymbolPipe implements PipeTransform {
  transform(value: string | null | undefined): string {
    return value ? (SYMBOLS[value] ?? value) : '';
  }
}

/** Курс конвертации в привычном виде: «1 $ = 92,50 ₽». */
export function formatRate(rate: number | null | undefined, from: string, to: string): string {
  if (!rate) return '—';
  const fmt = (n: number) => new Intl.NumberFormat('ru-RU', { maximumFractionDigits: 4 }).format(n);
  const [base, quote, value] = rate >= 1 ? [from, to, rate] : [to, from, 1 / rate];
  return `1 ${SYMBOLS[base] ?? base} = ${fmt(Math.round(value * 10000) / 10000)} ${SYMBOLS[quote] ?? quote}`;
}

@Pipe({ name: 'rate' })
export class RatePipe implements PipeTransform {
  transform(rate: number | null | undefined, from: string, to: string): string {
    return formatRate(rate, from, to);
  }
}

/** Дата и время в московском часовом поясе в коротком формате. */
@Pipe({ name: 'dt' })
export class DateTimePipe implements PipeTransform {
  transform(value: string | null | undefined, mode: 'datetime' | 'date' = 'datetime'): string {
    if (!value) {
      return '—';
    }
    const d = new Date(value.length === 10 ? value + 'T00:00:00' : value);
    const options: Intl.DateTimeFormatOptions =
      mode === 'date'
        ? { day: '2-digit', month: 'short', year: 'numeric' }
        : { day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit' };
    return new Intl.DateTimeFormat('ru-RU', options).format(d);
  }
}
