#!/bin/bash

echo "🛑 Stopping EzyPay Dev Environment..."

# Stop services defined in docker-compose.services.yml
docker compose -f docker-compose.services.yml down

# Stop infrastructure containers from docker-compose.all.yml
docker compose -f infrastructure/docker-compose.all.yml down

echo "✅ All services and infrastructure stopped."
