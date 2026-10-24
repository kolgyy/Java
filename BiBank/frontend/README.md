# BiBank Frontend

Angular 21: standalone-компоненты, signals, zoneless change detection, lazy routes, функциональные guards и HTTP-interceptor.
Дизайн-система написана на SCSS без UI-библиотек (`src/styles.scss`).

## Структура

```
src/app/
├── core/        модели API, AuthService (сессия, single-flight refresh), interceptor, guards, клиенты API
├── shared/      иконки, пайпы (деньги, номера счетов, курсы, даты), общие компоненты (статус, состояние, модалка, пагинация)
├── layout/      оболочка: боковое меню, верхняя панель, счётчик уведомлений
└── features/    страницы: вход, регистрация, главная, счета, перевод, история, вклады, кредиты, уведомления, профиль, админка
```

## Запуск для разработки

```bash
npm ci
npx ng serve          # http://localhost:4200, запросы /api проксируются на gateway :8080 (proxy.conf.json)
npx ng test --watch=false
npx ng build          # production-сборка в dist/frontend/browser
```

В Docker приложение собирается многоэтапным `Dockerfile` и раздаётся nginx (`nginx.conf`),
который также проксирует `/api` и Swagger на `api-gateway`, поэтому браузер работает с одним origin.
