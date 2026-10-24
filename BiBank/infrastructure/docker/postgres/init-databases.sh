#!/bin/bash
# Отдельная база и отдельный пользователь для каждого сервиса:
# сервис не может читать или изменять таблицы другого сервиса.
set -euo pipefail

create_db() {
  local db=$1 user=$2 password=$3
  echo "Creating database ${db} owned by ${user}"
  psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres <<-SQL
    CREATE USER ${user} WITH PASSWORD '${password}';
    CREATE DATABASE ${db} OWNER ${user};
    REVOKE ALL ON DATABASE ${db} FROM PUBLIC;
SQL
}

create_db bibank_auth          "$AUTH_DB_USER"         "$AUTH_DB_PASSWORD"
create_db bibank_accounts      "$ACCOUNT_DB_USER"      "$ACCOUNT_DB_PASSWORD"
create_db bibank_payments      "$PAYMENT_DB_USER"      "$PAYMENT_DB_PASSWORD"
create_db bibank_loans         "$LOAN_DB_USER"         "$LOAN_DB_PASSWORD"
create_db bibank_notifications "$NOTIFICATION_DB_USER" "$NOTIFICATION_DB_PASSWORD"
