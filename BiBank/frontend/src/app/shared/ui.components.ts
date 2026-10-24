import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { IconComponent } from './icon.component';
import { statusLabel, statusTone } from './labels';

@Component({
  selector: 'app-status',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<span class="badge" [class]="'badge badge--' + tone()">{{ label() }}</span>`,
})
export class StatusBadgeComponent {
  readonly status = input.required<string>();
  protected readonly label = computed(() => statusLabel(this.status()));
  protected readonly tone = computed(() => statusTone(this.status()));
}

/** Состояния блока данных: загрузка, ошибка, пусто. */
@Component({
  selector: 'app-state',
  imports: [IconComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (loading()) {
      <div class="state">
        <div class="skeleton-list">
          @for (i of rows; track i) {
            <div class="skeleton"></div>
          }
        </div>
      </div>
    } @else if (error()) {
      <div class="state state--error">
        <app-icon name="alert" [size]="28" />
        <p>{{ error() }}</p>
        <button class="btn btn--ghost btn--sm" (click)="retry.emit()">
          <app-icon name="refresh" [size]="16" /> Повторить
        </button>
      </div>
    } @else if (empty()) {
      <div class="state">
        <app-icon [name]="emptyIcon()" [size]="32" />
        <p class="state__title">{{ emptyTitle() }}</p>
        @if (emptyText()) {
          <p class="muted">{{ emptyText() }}</p>
        }
        <ng-content />
      </div>
    }
  `,
})
export class StateComponent {
  readonly loading = input(false);
  readonly error = input<string | null>(null);
  readonly empty = input(false);
  readonly emptyTitle = input('Здесь пока ничего нет');
  readonly emptyText = input<string | null>(null);
  readonly emptyIcon = input('info');
  readonly retry = output<void>();
  protected readonly rows = [1, 2, 3];
}

@Component({
  selector: 'app-modal',
  imports: [IconComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="modal-backdrop" (click)="closed.emit()">
      <div class="modal" role="dialog" aria-modal="true" (click)="$event.stopPropagation()">
        <header class="modal__header">
          <h3>{{ title() }}</h3>
          <button class="icon-btn" (click)="closed.emit()" aria-label="Закрыть">
            <app-icon name="x" />
          </button>
        </header>
        <div class="modal__body"><ng-content /></div>
      </div>
    </div>
  `,
  host: { '(document:keydown.escape)': 'closed.emit()' },
})
export class ModalComponent {
  readonly title = input.required<string>();
  readonly closed = output<void>();
}

@Component({
  selector: 'app-page-header',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="page-header">
      <div>
        <h1>{{ title() }}</h1>
        @if (subtitle()) {
          <p class="muted">{{ subtitle() }}</p>
        }
      </div>
      <div class="page-header__actions"><ng-content /></div>
    </div>
  `,
})
export class PageHeaderComponent {
  readonly title = input.required<string>();
  readonly subtitle = input<string | null>(null);
}

@Component({
  selector: 'app-pager',
  imports: [IconComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (totalPages() > 1) {
      <nav class="pager">
        <button class="btn btn--ghost btn--sm" [disabled]="page() === 0" (click)="change.emit(page() - 1)">
          <app-icon name="chevronLeft" [size]="16" /> Назад
        </button>
        <span class="muted">Стр. {{ page() + 1 }} из {{ totalPages() }}</span>
        <button class="btn btn--ghost btn--sm" [disabled]="page() + 1 >= totalPages()" (click)="change.emit(page() + 1)">
          Вперёд <app-icon name="chevronRight" [size]="16" />
        </button>
      </nav>
    }
  `,
})
export class PagerComponent {
  readonly page = input.required<number>();
  readonly totalPages = input.required<number>();
  readonly change = output<number>();
}
