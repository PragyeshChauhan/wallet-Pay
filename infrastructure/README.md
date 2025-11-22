# Infrastructure Setup for ezyPay

## Docker Compose Files

| File Name                   | Services Included                   |
|-----------------------------|-------------------------------------|
| docker-compose.services.yml | All Ezy Pay Services Docker Compose |
| docker-compose.infra.yml    | All Tools Services Included         |



## Usage

### Start all for dev Only

```bash
   docker-compose -f docker-compose.infra.yml up

| Component         | Purpose                                             | Port (Local) |
| ----------------- | --------------------------------------------------- | ------------ |
| `zookeeper`       | Required for Kafka coordination                     | `6001`       |
| `kafka`           | Kafka message broker                                | `6002`       |
| `kafka-ui`        | Kafka topic explorer (Kafka UI)                     | `6003`       |
| `postgresql`      | Main SQL database (PostgreSQL)                      | `6006`       |
| `pgAdmin`         | PostgreSQL admin web UI                             | `6007`       |
| `mysql`           | Secondary SQL database (MySQL)                      | `6008`       |
| `adminer`         | MySQL/PostgreSQL web UI (alternative to phpMyAdmin) | `6009`       |
| `mongodb`         | NoSQL database for flexible storage                 | `6010`       |
| `mongo-express`   | MongoDB web viewer                                  | `6011`       |
| `redis-auth`      | Redis for Auth tokens/sessions (instance 1)         | `6380`       |
| `redis-payment`   | Redis for Payment state/cache (instance 2)          | `6381`       |
| `redis-fraud`     | Redis for Fraud detection cache (instance 3)        | `6382`       |
| `redis-central`   | Shared Redis for other microservices                | `6383`       |
| `redis-commander` | Redis UI viewer for all Redis instances             | `8081`       |
| `zipkin`          | Distributed tracing system                          | `6012`       |
| `prometheus`      | Metrics collection for observability                | `6013`       |
| `grafana`         | Dashboard for metrics, alerts, tracing              | `6014`       |


hostname : service name refer block for postgres DB
host = postgres
port = 5432


```