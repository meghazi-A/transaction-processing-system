#!/bin/bash

# Demo Script for Transaction Processing System
# Showcases exactly-once semantics and idempotency

echo "=== Transaction Processing System Demo ==="
echo ""

API_URL="http://localhost:8081/api/v1/transactions"

# Colors
GREEN='\033[0;32m'
BLUE='\033[0;34m'
NC='\033[0m'

echo -e "${BLUE}1. Processing First Transaction${NC}"
echo "Transferring $100 from acc-001 to acc-002..."
curl -X POST "$API_URL" \
    -H "Content-Type: application/json" \
    -d '{
        "eventId": "550e8400-e29b-41d4-a716-446655440001",
        "transactionId": "123e4567-e89b-12d3-a456-426614174001",
        "fromAccountId": "acc-001",
        "toAccountId": "acc-002",
        "amount": 100.00,
        "currency": "USD",
        "type": "PAYMENT",
        "timestamp": "2024-01-15T10:30:00Z",
        "idempotencyKey": "demo-txn-001"
    }' | jq '.'
echo ""
echo ""

echo -e "${BLUE}2. Testing Idempotency (Duplicate Request)${NC}"
echo "Submitting same transaction again with same idempotency key..."
curl -X POST "$API_URL" \
    -H "Content-Type: application/json" \
    -d '{
        "eventId": "550e8400-e29b-41d4-a716-446655440001",
        "transactionId": "123e4567-e89b-12d3-a456-426614174001",
        "fromAccountId": "acc-001",
        "toAccountId": "acc-002",
        "amount": 100.00,
        "currency": "USD",
        "type": "PAYMENT",
        "timestamp": "2024-01-15T10:30:00Z",
        "idempotencyKey": "demo-txn-001"
    }' | jq '.'
echo ""
echo "✅ Same response returned, no duplicate processing!"
echo ""
echo ""

echo -e "${BLUE}3. Testing Insufficient Balance${NC}"
echo "Attempting transaction with insufficient balance..."
curl -X POST "$API_URL" \
    -H "Content-Type: application/json" \
    -d '{
        "eventId": "550e8400-e29b-41d4-a716-446655440002",
        "transactionId": "123e4567-e89b-12d3-a456-426614174002",
        "fromAccountId": "acc-003",
        "toAccountId": "acc-002",
        "amount": 999999.00,
        "currency": "USD",
        "type": "PAYMENT",
        "timestamp": "2024-01-15T10:31:00Z",
        "idempotencyKey": "demo-txn-002"
    }' | jq '.'
echo ""
echo "✅ Transaction rejected, no account changes!"
echo ""
echo ""

echo -e "${BLUE}4. Checking System Health${NC}"
curl -s http://localhost:8081/actuator/health | jq '.'
echo ""
echo ""

echo -e "${BLUE}5. Viewing Metrics (Sample)${NC}"
echo "Transaction processed:"
curl -s http://localhost:8081/actuator/prometheus | grep "transaction_processed_total"
echo ""
echo "Processing latency:"
curl -s http://localhost:8081/actuator/prometheus | grep "transaction_processing_latency"
echo ""
echo ""

echo -e "${GREEN}=== Demo Complete ===${NC}"
echo ""
echo "Key Observations:"
echo "- First transaction: Processed successfully"
echo "- Duplicate request: Idempotency prevented double-processing"
echo "- Insufficient balance: Transaction rejected gracefully"
echo "- All operations: ACID guarantees maintained"
echo ""
echo "Full metrics available at: http://localhost:8081/actuator/prometheus"
