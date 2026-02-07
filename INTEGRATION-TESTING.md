# Integration Testing Guide: Trade Capture → Validation Pipeline

This guide explains how to run integration tests for the complete trade processing pipeline.

## 🎯 Pipeline Flow

```
┌─────────────┐    RabbitMQ     ┌───────────────┐    Kafka        ┌────────────┐
│ Simulation  │──► Stream  ────►│ Trade-Capture │──► raw-trades ──►│ Validation │
└─────────────┘                 └───────────────┘                  └────────────┘
                                       │                                   │
                                       ├──► RTTM Events ──────────────────┤
                                       │    (Observability)                │
                                       ↓                                   ↓
                                  PostgreSQL                         valid/invalid
                                  (Outbox)                              trades
```

## 📋 Prerequisites

- Docker Desktop or Docker Engine + Docker Compose
- At least 8GB RAM available for Docker
- Ports available: 4000, 5432, 5552, 5672, 6379, 8080, 8081, 8082, 9021, 9092, 15672

## 🚀 Quick Start

### 1. Build All Services

```bash
# From pms-trade-capture directory
cd /mnt/c/Developer/pms-org/pms-trade-capture

# Build trade-capture
./mvnw clean package -DskipTests

# Build validation service
cd ../pms-validation
./mvnw clean package -DskipTests

# Build simulation service
cd ../pms-simulation
./mvnw clean package -DskipTests

# Return to trade-capture
cd ../pms-trade-capture
```

### 2. Start the Integration Stack

```bash
# Start all services
docker-compose -f docker-compose-integration.yaml up -d

# Watch logs (all services)
docker-compose -f docker-compose-integration.yaml logs -f

# Watch specific service
docker-compose -f docker-compose-integration.yaml logs -f trade-capture
docker-compose -f docker-compose-integration.yaml logs -f validation
```

### 3. Verify Services are Running

```bash
# Check all containers
docker-compose -f docker-compose-integration.yaml ps

# Expected output:
# NAME                    STATUS              PORTS
# pms-postgres           Up (healthy)        5432
# pms-rabbitmq           Up (healthy)        5552, 5672, 15672
# pms-kafka              Up (healthy)        9092, 29092, 29093
# pms-schema-registry    Up (healthy)        8081
# pms-redis-master       Up (healthy)        6379
# pms-trade-capture      Up (healthy)        8082
# pms-validation         Up (healthy)        8080
# pms-simulation         Up (healthy)        4000
# pms-control-center     Up                  9021
```

### 4. Health Check Endpoints

```bash
# Trade Capture
curl http://localhost:8082/actuator/health

# Validation Service
curl http://localhost:8080/actuator/health

# Simulation Service
curl http://localhost:4000/actuator/health

# Schema Registry
curl http://localhost:8081/subjects
```

## 🧪 Running Tests

### Test 1: Send Trades via Simulation Service

```bash
# Send 100 trades
curl -X POST "http://localhost:4000/api/simulation/generate?count=100&delay=10"

# Expected flow:
# 1. Simulation → RabbitMQ Stream (trade-stream)
# 2. Trade-Capture consumes from stream
# 3. Trade-Capture → Kafka (raw-trades-topic)
# 4. Validation consumes from Kafka
# 5. Validation → Kafka (valid-trades-topic / invalid-trades-topic)
```

### Test 2: Monitor RTTM Events

```bash
# Watch trade events
docker exec -it pms-kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic rttm.trade.events \
  --from-beginning

# Watch error events
docker exec -it pms-kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic rttm.error.events \
  --from-beginning

# Watch DLQ events
docker exec -it pms-kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic rttm.dlq.events \
  --from-beginning

# Watch queue metrics
docker exec -it pms-kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic rttm.queue.metrics \
  --from-beginning
```

### Test 3: Verify Trade Processing

```bash
# Check raw trades (from trade-capture)
docker exec -it pms-kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic raw-trades-topic \
  --from-beginning \
  --max-messages 10

# Check valid trades (from validation)
docker exec -it pms-kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic valid-trades-topic \
  --from-beginning \
  --max-messages 10

# Check invalid trades (from validation)
docker exec -it pms-kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic invalid-trades-topic \
  --from-beginning \
  --max-messages 10
```

### Test 4: Database Verification

```bash
# Connect to PostgreSQL
docker exec -it pms-postgres psql -U pms -d pmsdb

# Check trade-capture tables
\dt

# Check outbox events
SELECT id, portfolio_id, trade_id, created_at, dispatched_at 
FROM outbox_events 
ORDER BY created_at DESC 
LIMIT 10;

# Check safe store trades
SELECT id, portfolio_id, trade_id, is_valid, created_at 
FROM safe_store_trades 
ORDER BY created_at DESC 
LIMIT 10;

# Check DLQ entries
SELECT id, reason, created_at 
FROM dlq_entries 
ORDER BY created_at DESC 
LIMIT 10;

# Exit
\q
```

### Test 5: Redis Cache Check

```bash
# Connect to Redis
docker exec -it pms-redis-master redis-cli

# Check cached portfolios (validation service)
KEYS portfolio:*

# Check a portfolio
GET portfolio:some-uuid-here

# Exit
exit
```

## 📊 Monitoring UIs

### Kafka Control Center
- URL: http://localhost:9021
- Monitor topics, consumer groups, throughput
- View schema registry

### RabbitMQ Management
- URL: http://localhost:15672
- Username: `guest`
- Password: `guest`
- Monitor stream connections and message rates

## 🔧 Configuration Override

You can override any environment variable:

```bash
# Example: Increase trade-capture batch size
docker-compose -f docker-compose-integration.yaml up -d \
  -e INGEST_BATCH_MAX_SIZE=1000 \
  -e INGEST_BATCH_DRAIN_SIZE=1000
```

## 🐛 Troubleshooting

### Service won't start
```bash
# Check logs
docker-compose -f docker-compose-integration.yaml logs <service-name>

# Rebuild specific service
docker-compose -f docker-compose-integration.yaml build --no-cache <service-name>
docker-compose -f docker-compose-integration.yaml up -d <service-name>
```

### Kafka connection issues
```bash
# Verify Kafka topics exist
docker exec -it pms-kafka kafka-topics --bootstrap-server localhost:9092 --list

# Recreate topics if needed
docker exec -it pms-kafka kafka-topics --bootstrap-server localhost:9092 \
  --create --if-not-exists --topic raw-trades-topic --partitions 2 --replication-factor 1
```

### Database connection issues
```bash
# Check PostgreSQL is ready
docker exec -it pms-postgres pg_isready -U pms -d pmsdb

# Reset database (⚠️ DESTRUCTIVE)
docker-compose -f docker-compose-integration.yaml down -v
docker-compose -f docker-compose-integration.yaml up -d
```

### Port conflicts
```bash
# Check what's using a port
lsof -i :8082  # On Mac/Linux
netstat -ano | findstr :8082  # On Windows

# Change port in docker-compose if needed
```

## 🧹 Cleanup

```bash
# Stop all services (keep data)
docker-compose -f docker-compose-integration.yaml down

# Stop and remove volumes (⚠️ deletes all data)
docker-compose -f docker-compose-integration.yaml down -v

# Remove images
docker-compose -f docker-compose-integration.yaml down --rmi all
```

## 📈 Performance Testing

### Load Test with Simulation
```bash
# Send 10,000 trades with 1ms delay
curl -X POST "http://localhost:4000/api/simulation/generate?count=10000&delay=1"

# Monitor lag
docker exec -it pms-kafka kafka-consumer-groups \
  --bootstrap-server localhost:9092 \
  --describe --group validation-consumer-group

docker exec -it pms-kafka kafka-consumer-groups \
  --bootstrap-server localhost:9092 \
  --describe --group trade-capture-consumer
```

## 🔍 Advanced Debugging

### Enable SQL logging
```bash
# Update environment variable
docker-compose -f docker-compose-integration.yaml up -d \
  -e HIBERNATE_SHOW_SQL=true \
  trade-capture validation
```

### Increase log verbosity
```bash
docker-compose -f docker-compose-integration.yaml up -d \
  -e LOGGING_LEVEL_ROOT=DEBUG \
  trade-capture
```

### Access service containers
```bash
# Shell into trade-capture
docker exec -it pms-trade-capture sh

# Shell into validation
docker exec -it pms-validation sh

# Shell into simulation
docker exec -it pms-simulation sh
```

## 📝 Service URLs Summary

| Service | URL | Description |
|---------|-----|-------------|
| Trade Capture | http://localhost:8082 | Trade ingestion service |
| Validation | http://localhost:8080 | Trade validation service |
| Simulation | http://localhost:4000 | Trade generator |
| Kafka Control Center | http://localhost:9021 | Kafka monitoring UI |
| RabbitMQ Management | http://localhost:15672 | RabbitMQ UI |
| Schema Registry | http://localhost:8081 | Protobuf schemas |
| PostgreSQL | localhost:5432 | Database (pms/pms) |
| Redis | localhost:6379 | Cache |

## ✅ Success Criteria

A successful integration test should show:

1. ✅ All services healthy
2. ✅ Trades flowing: Simulation → Trade-Capture → Validation
3. ✅ RTTM events being published
4. ✅ No errors in service logs
5. ✅ Consumer groups showing zero lag
6. ✅ Database tables populated
7. ✅ Redis cache hit rate > 0%

## 🎓 Next Steps

- Add automated integration tests with Testcontainers
- Set up CI/CD pipeline with GitHub Actions
- Deploy to Kubernetes with Helm charts
- Add distributed tracing with Jaeger/Zipkin
- Implement RTTM dashboard for real-time monitoring
