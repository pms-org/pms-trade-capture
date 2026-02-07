#!/bin/bash

# Integration Test Helper Script
# Simplifies common operations for the PMS integration test environment

set -e

COMPOSE_FILE="docker-compose-integration.yaml"
COMPOSE_CMD="docker-compose -f $COMPOSE_FILE"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

function print_header() {
    echo -e "${BLUE}========================================${NC}"
    echo -e "${BLUE}$1${NC}"
    echo -e "${BLUE}========================================${NC}"
}

function print_success() {
    echo -e "${GREEN}✅ $1${NC}"
}

function print_error() {
    echo -e "${RED}❌ $1${NC}"
}

function print_warning() {
    echo -e "${YELLOW}⚠️  $1${NC}"
}

function build_all() {
    print_header "Building All Services"
    
    echo "Building trade-capture..."
    ./mvnw clean package -DskipTests
    
    echo "Building validation..."
    cd ../pms-validation && ./mvnw clean package -DskipTests
    
    echo "Building simulation..."
    cd ../pms-simulation && ./mvnw clean package -DskipTests
    
    cd ../pms-trade-capture
    print_success "All services built successfully!"
}

function start() {
    print_header "Starting Integration Stack"
    $COMPOSE_CMD up -d
    print_success "Stack started!"
    echo ""
    status
}

function stop() {
    print_header "Stopping Integration Stack"
    $COMPOSE_CMD down
    print_success "Stack stopped!"
}

function restart() {
    print_header "Restarting Integration Stack"
    stop
    start
}

function destroy() {
    print_header "Destroying Integration Stack (including volumes)"
    print_warning "This will delete all data!"
    read -p "Are you sure? (yes/no): " confirm
    if [ "$confirm" = "yes" ]; then
        $COMPOSE_CMD down -v
        print_success "Stack destroyed!"
    else
        echo "Cancelled."
    fi
}

function status() {
    print_header "Service Status"
    $COMPOSE_CMD ps
}

function logs() {
    service=${1:-}
    if [ -z "$service" ]; then
        print_header "Tailing All Logs"
        $COMPOSE_CMD logs -f
    else
        print_header "Tailing $service Logs"
        $COMPOSE_CMD logs -f "$service"
    fi
}

function health() {
    print_header "Health Check"
    
    echo "Trade Capture:"
    curl -s http://localhost:8082/actuator/health | jq '.' || echo "❌ Not responding"
    echo ""
    
    echo "Validation:"
    curl -s http://localhost:8080/actuator/health | jq '.' || echo "❌ Not responding"
    echo ""
    
    echo "Simulation:"
    curl -s http://localhost:4000/actuator/health | jq '.' || echo "❌ Not responding"
    echo ""
    
    echo "Schema Registry:"
    curl -s http://localhost:8081/subjects || echo "❌ Not responding"
    echo ""
}

function test_flow() {
    count=${1:-100}
    delay=${2:-10}
    
    print_header "Testing Trade Flow"
    echo "Sending $count trades with ${delay}ms delay..."
    
    curl -X POST "http://localhost:4000/api/simulation/generate?count=$count&delay=$delay"
    echo ""
    print_success "Trades sent!"
    echo ""
    
    sleep 5
    
    echo "Consumer Group Status:"
    docker exec pms-kafka kafka-consumer-groups \
        --bootstrap-server localhost:9092 \
        --describe --group validation-consumer-group
}

function watch_topic() {
    topic=${1:-raw-trades-topic}
    
    print_header "Watching Topic: $topic"
    docker exec -it pms-kafka kafka-console-consumer \
        --bootstrap-server localhost:9092 \
        --topic "$topic" \
        --from-beginning
}

function list_topics() {
    print_header "Kafka Topics"
    docker exec pms-kafka kafka-topics \
        --bootstrap-server localhost:9092 \
        --list
}

function consumer_groups() {
    print_header "Consumer Groups"
    docker exec pms-kafka kafka-consumer-groups \
        --bootstrap-server localhost:9092 \
        --list
}

function db_shell() {
    print_header "PostgreSQL Shell"
    docker exec -it pms-postgres psql -U pms -d pmsdb
}

function redis_shell() {
    print_header "Redis Shell"
    docker exec -it pms-redis-master redis-cli
}

function kafka_shell() {
    print_header "Kafka Shell"
    docker exec -it pms-kafka bash
}

function reset_kafka() {
    print_header "Resetting Kafka Topics"
    print_warning "This will delete all messages in topics!"
    read -p "Are you sure? (yes/no): " confirm
    if [ "$confirm" = "yes" ]; then
        topics=(
            "raw-trades-topic"
            "valid-trades-topic"
            "invalid-trades-topic"
            "rttm.trade.events"
            "rttm.dlq.events"
            "rttm.queue.metrics"
            "rttm.error.events"
        )
        
        for topic in "${topics[@]}"; do
            echo "Deleting $topic..."
            docker exec pms-kafka kafka-topics \
                --bootstrap-server localhost:9092 \
                --delete --topic "$topic" 2>/dev/null || true
            
            echo "Recreating $topic..."
            docker exec pms-kafka kafka-topics \
                --bootstrap-server localhost:9092 \
                --create --if-not-exists \
                --topic "$topic" --partitions 2 --replication-factor 1
        done
        print_success "Kafka topics reset!"
    else
        echo "Cancelled."
    fi
}

function show_urls() {
    print_header "Service URLs"
    echo "Trade Capture:      http://localhost:8082"
    echo "Validation:         http://localhost:8080"
    echo "Simulation:         http://localhost:4000"
    echo "Kafka Control:      http://localhost:9021"
    echo "RabbitMQ Mgmt:      http://localhost:15672 (guest/guest)"
    echo "Schema Registry:    http://localhost:8081"
    echo "PostgreSQL:         localhost:5432 (pms/pms)"
    echo "Redis:              localhost:6379"
}

function show_help() {
    cat << EOF
PMS Integration Test Helper

Usage: $0 <command> [options]

Commands:
  build         Build all services (mvn package)
  start         Start the integration stack
  stop          Stop the integration stack
  restart       Restart the integration stack
  destroy       Stop and remove all data (requires confirmation)
  status        Show service status
  logs [svc]    Tail logs (all or specific service)
  health        Check health of all services
  
  test [n] [d]  Send n trades with d ms delay (default: 100 trades, 10ms)
  
  watch <topic> Watch messages in Kafka topic
  topics        List all Kafka topics
  groups        List all consumer groups
  reset-kafka   Delete and recreate all Kafka topics
  
  db            Open PostgreSQL shell
  redis         Open Redis shell
  kafka         Open Kafka container shell
  
  urls          Show all service URLs
  help          Show this help message

Examples:
  $0 build                    # Build all services
  $0 start                    # Start the stack
  $0 logs trade-capture       # Watch trade-capture logs
  $0 test 1000 5              # Send 1000 trades with 5ms delay
  $0 watch rttm.trade.events  # Watch RTTM events
  $0 db                       # Open database shell

EOF
}

# Main command router
case "${1:-help}" in
    build)
        build_all
        ;;
    start)
        start
        ;;
    stop)
        stop
        ;;
    restart)
        restart
        ;;
    destroy)
        destroy
        ;;
    status)
        status
        ;;
    logs)
        logs "$2"
        ;;
    health)
        health
        ;;
    test)
        test_flow "$2" "$3"
        ;;
    watch)
        watch_topic "$2"
        ;;
    topics)
        list_topics
        ;;
    groups)
        consumer_groups
        ;;
    reset-kafka)
        reset_kafka
        ;;
    db)
        db_shell
        ;;
    redis)
        redis_shell
        ;;
    kafka)
        kafka_shell
        ;;
    urls)
        show_urls
        ;;
    help|*)
        show_help
        ;;
esac
