#!/usr/bin/env bash
# Сквозной сценарий BiBank через API Gateway.
# Требования: curl, jq. Запуск: ./scripts/e2e-smoke.sh [http://localhost:8080]
set -euo pipefail

API="${1:-http://localhost:8080}"
RUN=$(date +%s)
PASS="Secret123"
ADMIN_EMAIL="${BIBANK_ADMIN_EMAIL:-admin@bibank.local}"
ADMIN_PASSWORD="${BIBANK_ADMIN_PASSWORD:-Admin12345}"

step() { printf '\n\033[1;34m▶ %s\033[0m\n' "$*"; }
ok()   { printf '  \033[32m✓ %s\033[0m\n' "$*"; }
fail() { printf '  \033[31m✗ %s\033[0m\n' "$*"; exit 1; }

# call METHOD PATH [BODY] [TOKEN] [EXTRA_HEADER] → тело в /tmp/bibank_body, код в $CODE
call() {
  local method=$1 path=$2 body=${3:-} token=${4:-} extra=${5:-}
  local args=(-s -o /tmp/bibank_body -w '%{http_code}' -X "$method" "$API$path" -H 'Content-Type: application/json')
  [[ -n "$token" ]] && args+=(-H "Authorization: Bearer $token")
  [[ -n "$extra" ]] && args+=(-H "$extra")
  [[ -n "$body" ]] && args+=(-d "$body")
  CODE=$(curl "${args[@]}")
}

# jq по телу последнего ответа
j() { jq -r "$1" /tmp/bibank_body; }
jc() { jq -c "$1" /tmp/bibank_body; }

expect() { [[ "$CODE" == "$1" ]] && ok "$2 (HTTP $CODE)" || { cat /tmp/bibank_body; echo; fail "$2: ожидался $1, получен $CODE"; }; }

# register_and_login EMAIL NAME → токен в $TOKEN
register_and_login() {
  local email=$1 name=$2
  call POST /api/auth/register "{\"email\":\"$email\",\"password\":\"$PASS\",\"fullName\":\"$name\"}"
  expect 201 "Регистрация $email"
  call POST /api/auth/login "{\"email\":\"$email\",\"password\":\"$PASS\"}"
  expect 200 "Вход $email"
  TOKEN=$(j .accessToken)
}
uuid() { cat /proc/sys/kernel/random/uuid 2>/dev/null || uuidgen; }

step "Проверка доступности"
call GET /actuator/health; expect 200 "Gateway работает"

step "Безопасность: запрос без токена"
call GET /api/accounts; expect 401 "Нет токена → 401"
call GET /api/accounts "" "not-a-jwt"; expect 401 "Поддельный токен → 401"

step "Регистрация и вход двух клиентов"
register_and_login "alice.$RUN@example.com" "Алиса Иванова"; ALICE=$TOKEN
register_and_login "bob.$RUN@example.com" "Борис Петров"; BOB=$TOKEN
call POST /api/auth/register "{\"email\":\"alice.$RUN@example.com\",\"password\":\"$PASS\",\"fullName\":\"Дубль\"}"
expect 409 "Повторная регистрация → 409"

step "Открытие счетов"
call POST /api/accounts '{"currency":"RUB","type":"MAIN"}' "$ALICE"; expect 201 "Счёт Алисы RUB"; A_RUB=$(j .id)
call POST /api/accounts '{"currency":"USD"}' "$ALICE"; expect 201 "Счёт Алисы USD"; A_USD=$(j .id)
call POST /api/accounts '{"currency":"RUB"}' "$BOB"; expect 201 "Счёт Бориса RUB"; B_RUB_NUM=$(j .number)
call GET "/api/accounts/$A_RUB" "" "$BOB"; expect 404 "Чужой счёт недоступен"

step "Демо-пополнение (идемпотентное)"
KEY=$(uuid)
call POST /api/top-ups "{\"accountId\":\"$A_RUB\",\"amount\":50000}" "$ALICE" "Idempotency-Key: $KEY"; expect 201 "Пополнение 50 000 ₽"
call POST /api/top-ups "{\"accountId\":\"$A_RUB\",\"amount\":50000}" "$ALICE" "Idempotency-Key: $KEY"; expect 200 "Повтор с тем же ключом не пополняет второй раз"
call GET "/api/accounts/$A_RUB/balance" "" "$ALICE"; BAL=$(j .balance)
[[ "$BAL" == "50000.00" || "$BAL" == "50000" || "$BAL" == "50000.0" ]] && ok "Баланс 50 000" || fail "Баланс $BAL"

step "Перевод Алиса → Борис"
call POST /api/transfers/preview "{\"fromAccountId\":\"$A_RUB\",\"toAccountNumber\":\"$B_RUB_NUM\",\"amount\":1500}" "$ALICE"; jc '{recipientName, creditedAmount, sufficientFunds}'
expect 200 "Предпросмотр перевода"
TKEY=$(uuid)
call POST /api/transfers "{\"fromAccountId\":\"$A_RUB\",\"toAccountNumber\":\"$B_RUB_NUM\",\"amount\":1500,\"description\":\"За обед\"}" "$ALICE" "Idempotency-Key: $TKEY"
expect 201 "Перевод создан"
[[ $(j .status) == "COMPLETED" ]] && ok "Статус COMPLETED" || fail "Статус $(j .status)"
call POST /api/transfers "{\"fromAccountId\":\"$A_RUB\",\"toAccountNumber\":\"$B_RUB_NUM\",\"amount\":1500}" "$ALICE" "Idempotency-Key: $TKEY"
expect 200 "Повторная отправка не списывает повторно"
call GET "/api/accounts/$A_RUB/balance" "" "$ALICE"; BAL=$(j .balance)
[[ "$BAL" == "48500.00" || "$BAL" == "48500" || "$BAL" == "48500.0" ]] && ok "Баланс Алисы 48 500" || fail "Баланс $BAL"

step "Ошибочные переводы"
call POST /api/transfers "{\"fromAccountId\":\"$A_RUB\",\"toAccountNumber\":\"$B_RUB_NUM\",\"amount\":99999999}" "$ALICE" "Idempotency-Key: $(uuid)"
[[ "$CODE" == "422" ]] && ok "Превышение лимита → 422" || fail "Код $CODE"
call POST /api/transfers "{\"fromAccountId\":\"$A_RUB\",\"toAccountNumber\":\"$B_RUB_NUM\",\"amount\":60000}" "$ALICE" "Idempotency-Key: $(uuid)"
[[ $(j .status) == "FAILED" && $(j .failureCode) == "INSUFFICIENT_FUNDS" ]] && ok "Недостаточно средств → FAILED, операция в истории" || fail "$(cat /tmp/bibank_body)"
call POST /api/transfers "{\"fromAccountId\":\"$A_RUB\",\"toAccountNumber\":\"40817810999999999999\",\"amount\":10}" "$ALICE" "Idempotency-Key: $(uuid)"
expect 404 "Несуществующий получатель → 404"
call POST /api/transfers "{\"fromAccountId\":\"$A_RUB\",\"toAccountNumber\":\"$B_RUB_NUM\",\"amount\":10}" "$BOB" "Idempotency-Key: $(uuid)"
expect 404 "Списание с чужого счёта → 404"

step "Конвертация RUB → USD"
call GET "/api/accounts/$A_USD" "" "$ALICE"; A_USD_NUM=$(j .number)
call POST /api/transfers "{\"fromAccountId\":\"$A_RUB\",\"toAccountNumber\":\"$A_USD_NUM\",\"amount\":9250}" "$ALICE" "Idempotency-Key: $(uuid)"
expect 201 "Перевод между своими счетами с конвертацией"; jc '{status, amount, currency, creditedAmount, creditedCurrency, exchangeRate, direction}'

step "Параллельные переводы с одного счёта (20 × 2 000 при балансе 39 250)"
# Часть запросов может получить 429 от rate limiter шлюза (10 операций/с на пользователя) — это ожидаемо.
for i in $(seq 1 20); do
  curl -s -o "/tmp/bibank_par_$i" -w '%{http_code}\n' -X POST "$API/api/transfers" -H 'Content-Type: application/json' \
    -H "Authorization: Bearer $ALICE" -H "Idempotency-Key: $(uuid)" \
    -d "{\"fromAccountId\":\"$A_RUB\",\"toAccountNumber\":\"$B_RUB_NUM\",\"amount\":2000}" &
done > /tmp/bibank_par_codes
wait
echo "  HTTP-коды: $(sort /tmp/bibank_par_codes | uniq -c | tr -s ' ' | tr '\n' ' ')"
echo "  Статусы:   $(cat /tmp/bibank_par_* 2>/dev/null | jq -r '.status? // empty' 2>/dev/null | sort | uniq -c | tr -s ' ' | tr '\n' ' ')"
call GET "/api/accounts/$A_RUB/balance" "" "$ALICE"; BAL=$(j .balance)
awk "BEGIN{exit !($BAL >= 0)}" && ok "Баланс не отрицательный: $BAL" || fail "Отрицательный баланс $BAL"

step "История операций"
call GET "/api/transactions?accountId=$A_RUB&size=5" "" "$ALICE"; jc '{total: .totalElements, last: [.content[] | {type, status, amount, direction}][:3]}'
expect 200 "История с фильтром по счёту"

step "Кредит и вклад"
call GET /api/loans/products "" "$ALICE"; PRODUCT=$(j '[.[] | select(.code=="CONSUMER")][0].id')
call POST /api/loans/applications "{\"productId\":\"$PRODUCT\",\"accountId\":\"$A_RUB\",\"amount\":100000,\"termMonths\":12}" "$ALICE"
expect 201 "Заявка на кредит"
jc '{status, decisionReason}'
LOAN=$(j .loanId)
sleep 1
call GET "/api/loans/$LOAN" "" "$ALICE"; expect 200 "Кредит выдан"; jc '{status, monthlyPayment, outstandingPrincipal, nextPaymentDate}'
call POST "/api/loans/$LOAN/payments" "{\"accountId\":\"$A_RUB\",\"mode\":\"NEXT\"}" "$ALICE"; jc '{status, amount}'
expect 200 "Платёж по кредиту"
call GET /api/deposits/products "" "$ALICE"; DPROD=$(j '[.[] | select(.code=="SAVE_START")][0].id')
call POST /api/deposits "{\"productId\":\"$DPROD\",\"accountId\":\"$A_RUB\",\"amount\":20000,\"termMonths\":3}" "$ALICE"
expect 201 "Вклад открыт"; DEP=$(j .id)

step "Администратор"
call POST /api/auth/login "{\"email\":\"$ADMIN_EMAIL\",\"password\":\"$ADMIN_PASSWORD\"}"; ADMIN=$(j .accessToken)
expect 200 "Вход администратора"
call GET /api/admin/users "" "$ALICE"; expect 403 "USER не видит админку"
call GET "/api/admin/users?search=$RUN" "" "$ADMIN"; jc '{total: .totalElements}'; expect 200 "Список пользователей"
call POST "/api/admin/deposits/simulate-days?days=30" "" "$ADMIN"; jc .; expect 200 "Начисление процентов за 30 дней"
call GET "/api/deposits/$DEP" "" "$ALICE"; jc '{status, accruedInterest, currentValue, accruals: (.accruals | length)}'
call GET /api/admin/system/health "" "$ADMIN"; jc '{status, services: [.services[] | {name, status}]}'

step "Уведомления (через Kafka, ждём доставку)"
sleep 4
call GET "/api/notifications?size=50" "" "$ALICE"
j '.content[] | "  • " + .title + ": " + .message' | head -12
COUNT=$(j .totalElements)
[[ "$COUNT" -ge 5 ]] && ok "Получено уведомлений: $COUNT" || fail "Уведомлений: $COUNT"
call GET /api/notifications "" "$BOB"; j '.content[] | select(.eventType=="TransferReceived") | "  • Борис: " + .message' | head -2

step "Выход и отзыв refresh token"
call POST /api/auth/login "{\"email\":\"bob.$RUN@example.com\",\"password\":\"$PASS\"}"; REFRESH=$(j .refreshToken)
call POST /api/auth/refresh "{\"refreshToken\":\"$REFRESH\"}"; j .refreshToken > /tmp/bibank_rt; expect 200 "Обновление токенов"
call POST /api/auth/refresh "{\"refreshToken\":\"$REFRESH\"}"; expect 401 "Повторное использование refresh token → 401"
call POST /api/auth/logout "{\"refreshToken\":\"$(cat /tmp/bibank_rt)\"}"; expect 204 "Выход"
call POST /api/auth/refresh "{\"refreshToken\":\"$(cat /tmp/bibank_rt)\"}"; expect 401 "После выхода токен отозван"

printf '\n\033[1;32mСквозной сценарий пройден успешно.\033[0m\n'
