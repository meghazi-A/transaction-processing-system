\# Architecture



\## Goal

Process financial transactions with strong consistency, idempotency, and auditability under retries, crashes, and duplicate events.



\## High-Level Diagram

Client

&nbsp; -> API (Spring Boot)

&nbsp;     -> Transaction Service

&nbsp;         -> PostgreSQL (ACID)

&nbsp;         -> Outbox Table

&nbsp;     -> Kafka Publisher (from Outbox)

&nbsp;         -> Kafka Topics

&nbsp;             -> Consumers (ledger updates / notifications)



\## Request Flow (Happy Path)

1\) Client calls POST /transactions

2\) API validates request + idempotency key

3\) Persist transaction + audit entry (single DB transaction)

4\) Insert Outbox event in same DB transaction

5\) Background publisher reads Outbox and publishes to Kafka

6\) Consumer processes event (idempotent) and updates derived state



\## Idempotency Strategy

\- Client provides idempotency key

\- Server stores key -> transaction result

\- Duplicate requests return same result (no double spend)



\## Consistency Strategy (Outbox Pattern)

\- State change + event write happen in the same DB transaction

\- Publisher retries safely

\- Consumer is idempotent to handle duplicate delivery



\## Failure Scenarios Handled

\- Duplicate HTTP requests -> idempotency key prevents double spend

\- Crash after DB commit before Kafka publish -> Outbox ensures publish later

\- Duplicate Kafka messages -> idempotent consumer

\- Consumer failure -> retry / dead letter strategy (if implemented)



\## Tradeoffs

\- Outbox adds complexity but guarantees state-to-event consistency

\- Strict consistency may reduce raw throughput but prevents financial corruption



