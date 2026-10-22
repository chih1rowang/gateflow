# GateFlow

**A secure MCP tool gateway with durable agent orchestration — in pure Java/Spring Boot.** *(English | [中文](README.md))*

AI coding assistants (Cursor, or any MCP client) cannot reach internal systems — expense databases, policy documents, ticketing. Wiring them directly means no authentication, no rate control, no audit trail, and multi-step tasks die on process restart. **GateFlow is the self-hosted front door that fixes this**: one MCP endpoint for every client with auth / rate limiting / auditing / tool registry, plus durable workflow orchestration with human approval.

## Highlights

- **MCP gateway** (Spring Boot 3 + Spring AI, Streamable HTTP): JWT auth (401 on missing/invalid tokens), Redis sliding-window rate limiting (ZSET + atomic Lua, 429 on overuse), full call audit persisted to MySQL via Kafka (idempotent consumer by message key)
- **Durable orchestration** (Conductor): retrieval → LLM draft → human approval → persistence/reject. Verified by killing the worker process mid-execution: the task times out per its task definition and resumes from the checkpoint on retry; completed steps are replayed, not re-executed (at-least-once semantics with idempotent writes)
- **RAG with local models**: in-process BGE Chinese embeddings (data never leaves the machine) + hybrid retrieval (hand-rolled BM25 + vector, RRF fusion), grounding answers with citations; 100-item self-built eval set measured recall@3 **97% → 100%**
- **Measured performance** (see [deploy/perf/REPORT.md](deploy/perf/REPORT.md)): moving audit writes from synchronous to Kafka-async raised gateway throughput **1290 → 5497 QPS** and cut **P95 64ms → 11ms** (JMeter, 50 threads × 60s)
- **One-command deployment**: `docker compose up -d --build` starts all 7 services (conductor/mysql/redis/kafka/gateway/workers/nginx); full stack recovers **7 seconds** after wiping volumes
- **Admin panel** (Vue3 + Naive UI): execution board with in-browser approval, audit trail, token-usage cost page

## Quick start

```bash
git clone https://github.com/chih1rowang/gateflow && cd gateflow/deploy
docker compose up -d --build
# Panel:   http://localhost:8088
# MCP:     http://localhost:8088/mcp   (POST /admin/token?client=demo to get a JWT)
# Conductor UI: http://localhost:5000
```

Bring your own knowledge base: drop Markdown files into `deploy/knowledge/` and `curl -X POST http://localhost:8088/admin/knowledge/reload`.

## How it compares

Cloud gateways (AWS AgentCore, Azure APIM) lock you in; gateway platforms (Kong, Higress, agentgateway) mean operating a separate platform and don't orchestrate; Python/Go tools (ContextForge, Bifrost, ToolHive) don't fit Java teams. GateFlow packs gateway governance + tool registry + durable orchestration + human approval into **one Spring Boot app** — the Rust/Go/Python ecosystems all have representatives; Java did not.

## Roadmap

MCP session expiry & recycling · batch audit consumption · knowledge-base management UI & multi-tenant isolation · admin auth for query APIs

## License

MIT (under active development; core flows are all working — issues welcome)
