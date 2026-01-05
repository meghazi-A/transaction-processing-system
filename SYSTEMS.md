\# Systems Notes



\## Targets

\- Throughput: 1K TPS (goal)

\- Latency: p99 < 50ms (goal)



\## Scaling Plan

\- Partition strategy (Kafka)

\- DB write path bottlenecks

\- Cache (if any) strategy



\## Failure Modes Covered

\- Duplicate messages

\- Crash after DB write before publish (Outbox)

\- Retry storms

\- Partial failures



\## What breaks first at 10x load

\- DB write contention / indexing

\- Consumer lag / partition count

\- Connection pool saturation



