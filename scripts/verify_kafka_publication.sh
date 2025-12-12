#!/bin/bash

# Kafka Publication Verification
# Verifies trades are being published to Kafka topic

echo "═══════════════════════════════════════════════"
echo "KAFKA PUBLICATION VERIFICATION"
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

# Check Kafka topics
echo "Checking Kafka topics..."
docker exec kafka kafka-topics --bootstrap-server localhost:9092 --list

echo ""
echo "Checking raw-trades-proto topic details..."
docker exec kafka kafka-topics --bootstrap-server localhost:9092 --describe --topic raw-trades-proto

echo ""
echo "Checking topic message count..."
MESSAGES=$(docker exec kafka kafka-run-class kafka.tools.GetOffsetShell --broker-list localhost:9092 --topic raw-trades-proto --time -1 | awk -F ":" '{sum += $2} END {print sum}' 2>/dev/null || echo "0")
echo "Messages in raw-trades-proto topic: $MESSAGES"

echo ""
echo "Checking Schema Registry subjects..."
curl -s http://localhost:8081/subjects | jq . 2>/dev/null || echo "Schema Registry subjects: $(curl -s http://localhost:8081/subjects)"

echo ""
echo "Checking application logs for Kafka publishing..."
if docker ps --format "table {{.Names}}" | grep -q "^trade-capture-app$"; then
    echo "Recent Kafka publishing logs:"
    docker logs trade-capture-app 2>&1 | grep -E "(Kafka|kafka|producer|publish|sent to Kafka)" | tail -15

    echo ""
    echo "Checking for Kafka producer initialization..."
    if docker logs trade-capture-app 2>&1 | grep -q "Kafka.*producer\|producer.*started\|Kafka.*connected"; then
        print_status "PASS" "Kafka producer initialized successfully"
    else
        print_status "WARN" "Kafka producer initialization not explicitly logged"
    fi

    echo ""
    echo "Checking for successful message publishing..."
    PUBLISH_COUNT=$(docker logs trade-capture-app 2>&1 | grep -c "published\|Published\|sent to Kafka\|sent to kafka" || echo "0")
    if [ "$PUBLISH_COUNT" -gt 0 ]; then
        print_status "PASS" "Message publishing activity detected ($PUBLISH_COUNT publish operations)"
    else
        print_status "WARN" "No explicit publishing logs found"
    fi

    echo ""
    echo "Checking for Kafka connection errors..."
    ERROR_COUNT=$(docker logs trade-capture-app 2>&1 | grep -c "Kafka.*error\|kafka.*error\|producer.*error" || echo "0")
    if [ "$ERROR_COUNT" -eq 0 ]; then
        print_status "PASS" "No Kafka connection errors detected"
    else
        print_status "FAIL" "$ERROR_COUNT Kafka errors detected"
    fi
else
    print_status "FAIL" "Trade capture application is not running"
fi

echo ""
echo "Sample messages from Kafka topic (if available):"
if [ "$MESSAGES" -gt 0 ]; then
    echo "Consuming last 3 messages from raw-trades-proto topic..."
    docker exec kafka kafka-console-consumer --bootstrap-server localhost:9092 --topic raw-trades-proto --from-beginning --max-messages 3 --timeout-ms 5000 2>/dev/null | head -10
else
    echo "No messages in topic yet"
fi

echo ""
echo "═══════════════════════════════════════════════"
echo "KAFKA PUBLICATION VERIFICATION COMPLETE"
echo "═══════════════════════════════════════════════"
