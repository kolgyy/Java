import { HttpErrorResponse } from '@angular/common/http';
import { ApiError } from './models';

/** Человекочитаемое сообщение об ошибке API. */
export function errorMessage(error: unknown): string {
  if (error instanceof HttpErrorResponse) {
    const body = error.error as Partial<ApiError> | null;
    if (error.status === 0) {
      return 'Сервер недоступен. Проверьте подключение и попробуйте ещё раз.';
    }
    if (error.status === 429) {
      return 'Слишком много запросов. Подождите немного и повторите.';
    }
    if (body && typeof body === 'object' && body.fieldErrors) {
      const first = Object.values(body.fieldErrors)[0];
      if (first) {
        return first;
      }
    }
    if (body && typeof body === 'object' && body.message) {
      return body.message;
    }
    if (error.status >= 500) {
      return 'Сервис временно недоступен. Попробуйте позже.';
    }
    return `Ошибка запроса (${error.status})`;
  }
  return 'Непредвиденная ошибка';
}

export function fieldErrors(error: unknown): Record<string, string> {
  if (error instanceof HttpErrorResponse && error.error && typeof error.error === 'object') {
    return (error.error as ApiError).fieldErrors ?? {};
  }
  return {};
}
