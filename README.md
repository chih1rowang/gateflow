# GateFlow

**A secure MCP tool gateway with durable agent orchestration — in pure Java/Spring Boot.**

[![CI](https://github.com/chih1rowang/gateflow/actions/workflows/ci.yml/badge.svg)](https://github.com/chih1rowang/gateflow/actions/workflows/ci.yml)

AI coding assistants (Cursor, or any MCP client) cannot reach your internal systems — expense databases, policy documents, ticketing. Wiring them directly means no authentication, no rate control, no audit trail, and multi-step tasks die on process restart. **GateFlow is the self-hosted front door that fixes this**: one MCP endpoint for every client, with auth / rate limiting / auditing / tool registry, plus durable workflow orchestration with human approval.

```
任意 MCP 客户端（Cursor / Inspector / 自研面板）
        │ MCP (Streamable HTTP, JSON-RPC)          ← JWT 鉴权 / Redis 限流 / 审计
        ▼
┌─────────────────────────────────────────────┐
│ gateflow-gateway (Spring Boot 3 + Spring AI) │  :8090 /mcp
│   tools: doc_search / db_query / ticket_create│
└──────┬───────────────────────────┬───────────┘
       │ REST（工具实现全在 workers） │ 触发
       ▼                           ▼
 gateflow-workers :8081      Conductor 引擎（持久化编排）
   RAG 检索(BGE+BM25)         draft_review: 检索→LLM起草→人工审核→落库
   DB 只读查询 / 工单           （进程崩溃断点恢复，实测 kill -9）
       │
   MySQL / Redis / Kafka  ←全部由 docker compose 编排，Nginx 统一入口
```

## Quick start（一键全家桶）

```bash
git clone https://github.com/chih1rowang/gateflow && cd gateflow/deploy
docker compose up -d --build          # 7 服务：conductor/mysql/redis/kafka/gateway/workers/nginx
# 首次启动约 1-2 分钟（构建镜像）；清空数据卷后重建实测 7 秒
```

| 入口 | 地址 |
|---|---|
| 管理面板（任务看板/审计/成本） | http://localhost:8088 |
| MCP 端点（需令牌） | http://localhost:8088/mcp |
| Conductor 执行 UI | http://localhost:5000 |

```bash
# 拿一个令牌（无令牌调用一律 401；60 秒超 10 次调用返回 429）
curl -X POST "http://localhost:8088/admin/token?client=demo"
# 用 MCP Inspector 连 http://localhost:8088/mcp（Authentication 填 Bearer <token>）即可看到三个工具
```

换成你自己的知识库：把 Markdown 文档放进 `deploy/knowledge/`，`curl -X POST http://localhost:8088/admin/knowledge/reload`（或重启 workers）后提问即可。

## 一次完整演示（8 步）

1. 不带令牌连 `/mcp` → **401**；伪造令牌 → **401**
2. 带合法令牌 → tools 列表出现 `doc_search / db_query / ticket_create`
3. 对 AI 助手说："按报销制度为上周活动起草报销申请"
4. 它调 `doc_search` → 面板**审计页**出现调用记录（客户端/方法/参数/耗时）
5. 它调 `db_query`（只读 SELECT 守卫，DELETE 一律拒绝）→ 返回真实经费数据
6. 触发 `draft_review` 工作流；当场 `kill` workers 进程再重启 → **任务断点恢复跑完**
7. 在面板**任务看板**打开待审任务，查看 AI 起草的申请，点"批准"
8. 批准触发落库+建工单；审计页回放全链路，成本页展示 token 用量

## 实测数字（详见 [deploy/perf/REPORT.md](deploy/perf/REPORT.md)）

| 指标 | 改造前 | 改造后 |
|---|---|---|
| 网关吞吐（审计同步→Kafka 异步，JMeter 50 并发×60s） | 1,290 QPS | **5,497 QPS** |
| P95 响应时间 | 64ms | **11ms** |
| RAG 召回率（100 条自建评测集，纯向量→混合检索） | 97.0% | **100.0%** |
| 清空数据卷后全家桶恢复 | — | **7 秒** |

## 与现有方案对比

| 方案 | 语言/形态 | 与 GateFlow 的差异 |
|---|---|---|
| AWS AgentCore Gateway / Azure APIM | 云托管 | 锁云、数据出域、按量计费；GateFlow 自托管单应用 |
| Kong MCP Gateway / Higress / agentgateway | 网关平台（Go/Envoy/Rust） | 需运维独立网关平台；不做任务编排与人工审核 |
| IBM ContextForge / Bifrost / ToolHive | Python / Go | 非 Java 栈，Java 团队难二次开发 |
| springai-mcp-gateway | Java | 最接近，但只做协议聚合：无限流/审计/成本/编排闭环 |

GateFlow 的位置：**把"网关治理 + 工具注册 + 持久化编排 + 人工审核"装进一个 Spring Boot 应用**——Rust/Go/Python 各有代表，Java 侧此前没有对应物。

## Roadmap

- MCP 会话过期回收（压测中发现的会话堆积问题，见 REPORT.md）
- 审计消费者批量落库（当前逐条 ~100 条/s）
- 知识库管理界面与多库隔离（当前：放文件 + 热重载端点，开发者传统）
- 审计查询接口的管理员鉴权

## License

MIT（开发中，主链路已全部跑通；欢迎 issue 交流）
