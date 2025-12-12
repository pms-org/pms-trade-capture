#!/bin/bash

# Stream Consumption Verification
# Verifies RabbitMQ stream connection and trade reception

echo "═══════════════════════════════════════════════"
echo "STREAM CONSUMPTION VERIFICATION"
echo "═══════════════════════════════════════════════"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

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

# Check RabbitMQ stream status
echo "Checking RabbitMQ Streams..."
docker exec rabbitmq rabbitmq-streams list_streams

echo ""
echo "Checking stream consumers..."
docker exec rabbitmq rabbitmq-streams list_consumers trade-stream 2>/dev/null || echo "No consumers found"

echo ""
echo "Checking application logs for stream consumption..."
if docker ps --format "table {{.Names}}" | grep -q "^trade-capture-app$"; then
    echo "Recent stream consumption logs:"
    docker logs trade-capture-app 2>&1 | grep -E "(StreamConsumer|RabbitMQ|stream|trade-stream)" | tail -10

    echo ""
    echo "Checking for connection errors..."
    if docker logs trade-capture-app 2>&1 | grep -q "StreamConsumerManager.*connected"; then
        print_status "PASS" "StreamConsumerManager connected successfully"
    else
        print_status "WARN" "StreamConsumerManager connection status unclear"
    fi

    # Check for trade reception
    echo ""
    echo "Checking for trade reception..."
    TRADE_COUNT=$(docker logs trade-capture-app 2>&1 | grep -c "Received trade" || echo "0")
    if [ "$TRADE_COUNT" -gt 0 ]; then
        print_status "PASS" "Trades are being received ($TRADE_COUNT messages logged)"
    else
        print_status "WARN" "No trade reception logs found (may be using different log level)"
    fi
else
    print_status "FAIL" "Trade capture application is not running"
fi

echo ""
echo "═══════════════════════════════════════════════"
echo "STREAM CONSUMPTION VERIFICATION COMPLETE"
echo "═══════════════════════════════════════════════"
