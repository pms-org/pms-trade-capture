#!/bin/bash

# DLQ Handling Verification
# Verifies Dead Letter Queue handling for unparseable messages

echo "═══════════════════════════════════════════════"
echo "DLQ HANDLING VERIFICATION"
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

# Check DLQ table structure and contents
echo "Checking DLQ table structure..."
docker exec -i postgres psql -U pms -d pmsdb << 'EOF'
\d dlq_entry
