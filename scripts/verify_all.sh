#!/bin/bash

# Trade Capture System - Complete Verification Suite
# This script runs all verification checks for the trade capture system

set -e

echo "═══════════════════════════════════════════════"
echo "TRADE CAPTURE SYSTEM - COMPLETE VERIFICATION"
echo "═══════════════════════════════════════════════"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Function to print status
print_status() {
    local status=$1
    local message=$2
    case $status in
        "PASS")
            echo -e "${GREEN}✅ PASS${NC}: $message"
            ;;
        "FAIL")
            echo -e "${RED}❌ FAIL${NC}: $message"
            ;;
        "WARN")
            echo -e "${YELLOW}⚠️  WARN${NC}: $message"
            ;;
        "INFO")
            echo -e "${BLUE}ℹ️  INFO${NC}: $message"
            ;;
    esac
}

# Function to check if service is running
check_service() {
    local service_name=$1
    local container_name=$2

    if docker ps --format "table {{.Names}}" | grep -q "^${container_name}$"; then
        print_status "PASS" "$service_name is running"
        return 0
    else
        print_status "FAIL" "$service_name is not running"
        return 1
    fi
}

# Function to check database connectivity
check_database() {
    echo ""
    print_status "INFO" "Checking PostgreSQL connectivity..."

    if docker exec postgres pg_isready -U pms -d pmsdb -h localhost >/dev/null 2>&1; then
        print_status "PASS" "PostgreSQL is accessible"
        return 0
    else
        print_status "FAIL" "PostgreSQL is not accessible"
        return 1
    fi
}

# Function to check RabbitMQ connectivity
check_rabbitmq() {
    echo ""
    print_status "INFO" "Checking RabbitMQ connectivity..."

    if docker exec rabbitmq rabbitmq-diagnostics -q ping >/dev/null 2>&1; then
        print_status "PASS" "RabbitMQ is accessible"
        return 0
    else
        print_status "FAIL" "RabbitMQ is not accessible"
        return 1
    fi
}

# Function to check Kafka connectivity
check_kafka() {
    echo ""
    print_status "INFO" "Checking Kafka connectivity..."

    if docker exec kafka kafka-cluster cluster-id --bootstrap-server localhost:9092 >/dev/null 2>&1; then
        print_status "PASS" "Kafka is accessible"
        return 0
    else
        print_status "FAIL" "Kafka is not accessible"
        return 1
    fi
}

# Function to check Schema Registry
check_schema_registry() {
    echo ""
    print_status "INFO" "Checking Schema Registry connectivity..."

    if curl -s http://localhost:8081/subjects >/dev/null 2>&1; then
        print_status "PASS" "Schema Registry is accessible"
        return 0
    else
        print_status "FAIL" "Schema Registry is not accessible"
        return 1
    fi
}

# Function to check application health
check_application() {
    echo ""
    print_status "INFO" "Checking Trade Capture Application..."

    if docker ps --format "table {{.Names}}" | grep -q "^trade-capture-app$"; then
        print_status "PASS" "Trade Capture Application is running"

        # Check if application is responding
        if curl -s http://localhost:8082/actuator/health >/dev/null 2>&1; then
            print_status "PASS" "Application health endpoint is responding"
            return 0
        else
            print_status "WARN" "Application health endpoint not responding"
            return 1
        fi
    else
        print_status "FAIL" "Trade Capture Application is not running"
        return 1
    fi
}

# Main verification function
main() {
    local failures=0

    echo "🔍 INFRASTRUCTURE CHECKS"
    echo "═══════════════════════════════════════════════"

    # Check infrastructure services
    check_service "PostgreSQL" "postgres" || ((failures++))
    check_service "RabbitMQ" "rabbitmq" || ((failures++))
    check_service "Kafka" "kafka" || ((failures++))
    check_service "Schema Registry" "schema-registry" || ((failures++))
    check_service "Trade Simulation" "pms-simulation" || ((failures++))

    # Check connectivity
    check_database || ((failures++))
    check_rabbitmq || ((failures++))
    check_kafka || ((failures++))
    check_schema_registry || ((failures++))

    # Check application
    check_application || ((failures++))

    echo ""
    echo "🔍 SYSTEM VERIFICATION CHECKS"
    echo "═══════════════════════════════════════════════"

    # Run detailed verifications
    echo "Running detailed system checks..."

    # Run individual verification scripts
    if [ -f "./scripts/verify_stream_consumption.sh" ]; then
        echo ""
        ./scripts/verify_stream_consumption.sh
    fi

    if [ -f "./scripts/verify_batch_ingestion.sh" ]; then
        echo ""
        ./scripts/verify_batch_ingestion.sh
    fi

    if [ -f "./scripts/verify_outbox_polling.sh" ]; then
        echo ""
        ./scripts/verify_outbox_polling.sh
    fi

    if [ -f "./scripts/verify_kafka_publication.sh" ]; then
        echo ""
        ./scripts/verify_kafka_publication.sh
    fi

    if [ -f "./scripts/verify_trade_order.sh" ]; then
        echo ""
        ./scripts/verify_trade_order.sh
    fi

    if [ -f "./scripts/verify_dlq_handling.sh" ]; then
        echo ""
        ./scripts/verify_dlq_handling.sh
    fi

    echo ""
    echo "═══════════════════════════════════════════════"
    if [ $failures -eq 0 ]; then
        print_status "PASS" "All infrastructure checks passed!"
        echo ""
        print_status "INFO" "Run individual verification scripts for detailed analysis"
    else
        print_status "FAIL" "$failures infrastructure checks failed"
        echo ""
        print_status "INFO" "Fix infrastructure issues before running detailed verifications"
        exit 1
    fi
    echo "═══════════════════════════════════════════════"
}

# Run main function
main "$@"
