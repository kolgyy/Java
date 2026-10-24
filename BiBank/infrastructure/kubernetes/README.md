# BiBank в Kubernetes

Манифесты рассчитаны на локальный кластер (minikube, kind, Docker Desktop) и показывают целевую схему развёртывания:
Deployment + Service на каждый сервис, StatefulSet для PostgreSQL, Redis и Kafka, ConfigMap для настроек,
Secret для паролей и ключей, readiness/liveness/startup probes, запросы и лимиты ресурсов, Ingress.

> Манифесты собираются `kubectl kustomize` и проходят проверку схем Kubernetes 1.31 (`kubeconform -strict`), но в реальном кластере систему пока не запускали.

## Запуск в minikube

```bash
minikube start --cpus 4 --memory 8g
minikube addons enable ingress

# 1. Собрать образы прямо в Docker внутри minikube
eval $(minikube docker-env)
docker compose build

# 2. Namespace и секреты (значения берутся из вашего .env)
kubectl apply -f infrastructure/kubernetes/00-namespace.yaml
kubectl -n bibank create secret generic bibank-secrets --from-env-file=.env

# 3. Всё остальное
kubectl apply -k infrastructure/kubernetes
kubectl -n bibank get pods -w
```

Когда все поды `Ready`, добавьте в `/etc/hosts` строку `$(minikube ip) bibank.local` и откройте http://bibank.local.
Без Ingress можно пробросить порт: `kubectl -n bibank port-forward svc/frontend 4200:80`.

Для kind вместо `minikube docker-env` загрузите образы: `kind load docker-image bibank/auth-service:latest …`.

## Что учтено

- **Секреты** не лежат в репозитории: `secret.example.yaml` — только образец, Secret создаётся из `.env` или внешним менеджером секретов.
- **Масштабирование:** account-service и payment-service по 2 реплики. Корректность денег не зависит от числа экземпляров:
  блокировки строк и уникальные ключи живут в PostgreSQL, outbox разбирается через `FOR UPDATE SKIP LOCKED`,
  а Kafka-потребители одной группы делят партиции.
- **Пробы:** `startupProbe` даёт Spring Boot до 3 минут на старт, `readinessProbe` (`/actuator/health/readiness`)
  убирает под из балансировки, пока он не готов, `livenessProbe` перезапускает зависший процесс.
- **Хранилища** — StatefulSet с PersistentVolumeClaim, данные переживают перезапуск подов.

## Что стоит добавить для продакшена

Kafka из трёх брокеров и replication factor 3, управляемый PostgreSQL или оператор (CloudNativePG),
Redis Sentinel/Cluster, HorizontalPodAutoscaler, NetworkPolicy (сервисы доступны только gateway и друг другу),
TLS на Ingress, сбор логов и метрик (Prometheus + Grafana, Loki), образы с конкретными тегами вместо `latest`.
