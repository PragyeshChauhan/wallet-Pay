#!/bin/bash

echo "🚀 Starting EzyPay Local Development Environment..."

# Navigate to infrastructure
cd infrastructure || exit

echo "🧱 Starting infrastructure containers (Kafka, Redis, DBs, Zipkin, etc)..."
docker compose -f docker-compose.all.yml up -d

cd ..

echo "⏳ Waiting for infrastructure to initialize (15s)..."
sleep 15

echo "🔄 Starting EzyPay microservices (API Gateway, Auth, Users, etc)..."
docker compose -f docker-compose.services.yml up -d

echo "✅ All services started successfully!"
echo ""
echo "📌 Visit API Gateway: http://localhost:9000"
echo "📌 Kafka UI: http://localhost:6003"
echo "📌 Redis Commander: http://localhost:8081"
echo "📌 pgAdmin: http://localhost:6007"
echo "📌 Grafana: http://localhost:6014"
echo ""
