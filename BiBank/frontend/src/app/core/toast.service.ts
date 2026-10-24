import { Injectable, signal } from '@angular/core';

export interface Toast {
  id: number;
  tone: 'success' | 'error' | 'info';
  text: string;
}

@Injectable({ providedIn: 'root' })
export class ToastService {
  readonly toasts = signal<Toast[]>([]);
  private seq = 0;

  success(text: string): void {
    this.push('success', text);
  }

  error(text: string): void {
    this.push('error', text);
  }

  info(text: string): void {
    this.push('info', text);
  }

  dismiss(id: number): void {
    this.toasts.update((list) => list.filter((t) => t.id !== id));
  }

  private push(tone: Toast['tone'], text: string): void {
    const id = ++this.seq;
    this.toasts.update((list) => [...list.slice(-3), { id, tone, text }]);
    setTimeout(() => this.dismiss(id), tone === 'error' ? 7000 : 4500);
  }
}
