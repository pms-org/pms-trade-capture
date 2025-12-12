#!/bin/bash

# Trade Order Verification
# Verifies that trades maintain correct order throughout the system

echo "═══════════════════════════════════════════════"
echo "TRADE ORDER VERIFICATION"
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

# Check trade order correlation
echo "Checking trade order correlation (first 30 trades)..."
docker exec -i postgres psql -U pms -d pmsdb << 'EOF'
WITH received_trades AS (
    SELECT
        ROW_NUMBER() OVER (ORDER BY received_at ASC) as received_seq,
        id,
        portfolio_id,
        trade_id,
        received_at
    FROM safe_store_trade
    ORDER BY received_at ASC
    LIMIT 30
),
outbox_trades AS (
    SELECT
        ROW_NUMBER() OVER (ORDER BY created_at ASC) as outbox_seq,
        id,
        portfolio_id,
        trade_id,
        created_at
    FROM outbox_event
    ORDER BY created_at ASC
    LIMIT 30
)
SELECT
    r.received_seq,
    o.outbox_seq,
    CASE WHEN r.received_seq = o.outbox_seq THEN '✓ IN ORDER' ELSE '✗ OUT OF ORDER' END as order_status,
    LEFT(r.trade_id::text, 8) as trade_id_short,
    r.received_at,
    o.created_at as outbox_time,
    EXTRACT(EPOCH FROM (o.created_at - r.received_at)) * 1000 as lag_ms
FROM received_trades r
JOIN outbox_trades o ON r.trade_id = o.trade_id
ORDER BY r.received_seq;
