# 💼 EzyPay Wallet — Backend Microservices

EzyPay Wallet is a modular, event-driven digital payment platform built with Spring Boot 3.5.3, Java 21, and cloud-native technologies. It is designed to handle millions of users using a secure and scalable architecture inspired by fintech leaders like Paytm and PhonePe.

---

## 📂 Project Structure and Exposed Ports

| Module Name               | Description                                  | Port (Local) |
| ------------------------- | -------------------------------------------- | ------------- |
| `api-gateway`             | Entry point, routes requests, DPoP Auth      | `9000`        |
| `auth-service`            | Login, refresh, JWT, DPoP token handling     | `9001`        |
| `user-service`            | User identity and profile management         | `9002`        |
| `account-service`         | Wallet and linked bank accounts              | `9003`        |
| `payment-service`         | UPI, IMPS, NEFT payment processing           | `9004`        |
| `transaction-service`     | Ledger and historical transactions           | `9005`        |
| `notification-service`    | SMS, email, push alerts                      | `9006`        |
| `loan-service`            | Loan application and repayments              | `9007`        |
| `creditcard-service`      | Credit card lifecycle mgmt                   | `9008`        |
| `frauddetection-service`  | Kafka-based fraud detection engine           | `9009`        |
| `bankintegration-service` | Integrates with external bank/UPI APIs       | `9010`        |
| `infra-service`           | Shared tools: file store, mailer, etc.       | `9011`        |
| `config-server`           | Spring Cloud centralized config              | `8888`        |
| `eureka-server`           | Service discovery using Eureka               | `8761`        |

---

## 📦 Infrastructure Services

| Component           | Purpose                                | Port (Local) |
|---------------------|-----------------------------------------|--------------|
| `zookeeper`          | Required for Kafka coordination         | `6001`       |
| `kafka`              | Message broker                          | `6002`       |
| `kafka-ui`           | Kafka topic explorer                    | `6003`       |
| `postgresql`         | Main SQL database                       | `6006`       |
| `pgAdmin`            | PostgreSQL admin UI                     | `6007`       |
| `mysql`              | Secondary SQL database                  | `6008`       |
| `adminer`            | MySQL admin interface                   | `6009`       |
| `mongodb`            | NoSQL database for flexible storage     | `6010`       |
| `mongo-express`      | MongoDB web viewer                      | `6011`       |
| `redis-auth`         | Redis for Auth tokens and sessions      | `6380`       |
| `redis-payment`      | Redis for Payment state/cache           | `6381`       |
| `redis-fraud`        | Redis for Fraud signals cache           | `6382`       |
| `redis-central`      | Shared Redis for other services         | `6383`       |
| `redis-commander`    | Redis UI viewer                         | `8081`       |
| `zipkin`             | Distributed tracing                     | `6012`       |
| `prometheus`         | Monitoring metrics collector            | `6013`       |
| `grafana`            | Dashboards for metrics and alerts       | `6014`       |

---

## 🛠️ Development & Deployment

### 🔹 Start Infra (Redis, Kafka, DBs, etc.)
```bash

cd infrastructure/
docker compose -f docker-compose.infra.yml up -d
```


## 🚀 Dev Scripts

### Start All (Infra + Services)

```bash
   ./start-ezypay-dev.sh
```

### Stop All

```bash
  ./stop-ezypay-dev.sh
```

> First time? Make them executable:

```bash
  chmod +x start-ezypay-dev.sh stop-ezypay-dev.sh
```


# Key-store  
```bash

server:
ssl:
enabled: true
key-store: classpath:client.keystore.p12
key-store-password: ${KEYSTORE_PASSWORD:ezpay@01}
key-store-type: PKCS12
key-alias: ezpay
trust-store: classpath:truststore.p12
trust-store-password: ${TRUSTSTORE_PASSWORD:ezpay@01}
trust-store-type: PKCS12


# Generating a keystore with alias 'ezpay' and password 'ezpay@01'
keytool -genkeypair -alias ezpay -keyalg RSA -keysize 2048 -storetype PKCS12 -keystore keystore.p12 -validity 365 -storepass ezpay@01 -keypass ezpay@01 -dname "CN=EzPay, OU=AuthService, O=EzPay, L=Unknown, S=Unknown, C=Unknown"

# Exporting the certificate (public key) from the keystore
keytool -exportcert -alias ezpay -keystore keystore.p12 -storepass ezpay@01 -file cert.crt

# Creating a truststore and importing the certificate
keytool -importcert -alias ezpay -file cert.crt -keystore truststore.p12 -storetype PKCS12 -storepass ezpay@01 -noprompt
```




