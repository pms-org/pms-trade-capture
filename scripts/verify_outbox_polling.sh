#!/bin/bash

# Outbox Polling Verification
# Verifies OutboxDispatcher is processing pending events

echo "═══════════════════════════════════════════════"
echo "OUTBOX POLLING VERIFICATION"
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

# Check outbox event processing status
echo "Checking outbox event processing status..."
docker exec -i postgres psql -U pms -d pmsdb << 'EOF'
SELECT
    status,
    COUNT(*) as count,
    ROUND(AVG(EXTRACT(EPOCH FROM (updated_at - created_at))), 2) as avg_processing_time_seconds,
    MIN(created_at) as oldest_pending,
    MAX(created_at) as newest_pending
FROM outbox_event
GROUP BY status
ORDER BY status;
