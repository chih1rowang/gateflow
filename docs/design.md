# GateFlow 技术设计（渐进式）

> 本文档永远比代码先行一小步，严禁瀑布式写全；与代码对不上 = vibe coding 跑偏的信号，立即停下核对。
> 节奏：W1 节已落地；W2 节于 W2D1 增补（见文末 §2）；W3 末补审计 DDL/JWT/限流伪码；W4 末补 compose 拓扑与压测方案。

## 1. 模块图与 W1 最小切片（2026-10-19）

### 1.1 模块图

```
任意 MCP 客户端（Cursor / Trae CN / 自研问答页；开发验证用 MCP Inspector + curl）
        │  MCP (Streamable HTTP, JSON-RPC)
        ▼
┌─────────────────────────────┐
│ gateway（W3 动工）           │  Spring Boot 3.5 + Spring AI 2.0 MCP Server
│  JWT 鉴权 / Redis 滑动窗口   │  @McpTool 声明工具（三个）
│  MySQL 审计(W4 走 Kafka)     │
└──────────┬──────────────────┘
           │
     ┌─────┼──────────┬──────────┐
     ▼     ▼          ▼
   MySQL  RAG 文档   工单/通知     （工具本体 = Spring bean × 3）
   只读    检索        服务

Conductor（docker，引擎现成）◄──注册── workers（langchain4j 调 DeepSeek）
  workflow: rag_retriever → llm_answer → (echo 占位)
```

仓库结构映射：`gateway/`（W3）· `workers/`（W1 起主战场）· `panel/`（W5）· `deploy/`（compose/nginx/压测）。

### 1.2 第一条 workflow：rag_retriever → llm_answer → echo（W1 定稿形态，v3 已跑通）

| 节点 | 类型 | 输入 | 输出 | 实现 |
|---|---|---|---|---|
| rag_retriever | simple | `question` | `chunks: List<String>` | ✅ 本地 BGE-small-zh-v15（ONNX in-process，免费离线）+ InMemoryEmbeddingStore；启动时入库（recursive 切分 300/30），检索 TopK=3（W4 评测调优） |
| llm_answer | simple | `question`、`chunks` | `answer` | ✅ system 消息携带制度片段（限定只依据资料回答+注明出处）+ user 消息携带问题 |
| echo | simple | `answer` | `answer` | ✅ 链路终端节点（W2 由人工审核节点替换） |

知识库：`deploy/knowledge/*.md`（4 份社团经费制度文档，含独有数字如 500 元上限——保证"答对=检索到了"可验证）。
W1 验收实录：问"单笔报销上限是多少？超过怎么办" → 回答含 500 元、超额预批流程、引用《报销制度》第 1 条；检索 3 片段 top score 0.8571。

### 1.6 RAG 实现要点（W1 Day5 落地）

- 依赖：`langchain4j-easy-rag` + `langchain4j-embeddings-bge-small-zh-v15`（1.22.0-beta32 线）
- 启动入库（一次性）：FileSystemDocumentLoader 加载 → DocumentSplitters.recursive(300, 30) 切分 → BGE 向量化 → InMemoryEmbeddingStore
- 检索（每次任务）：question 向量化 → store.search(maxResults=3) → 片段文本列表
- BGE 类路径：`dev.langchain4j.model.embedding.onnx.bgesmallzhv15.BgeSmallZhV15EmbeddingModel`（jar 内 ONNX 模型随包分发，进程内推理）
- LLM 消息构造：`model.chat(List.of(SystemMessage, UserMessage)).aiMessage().text()`——chat(List) 重载返回 ChatResponse
- 内存库局限（诚实边界）：重启进程需重新入库（约 5 秒，4 文档规模无感）；文档量大后换持久化向量库；TopK 与切分参数未调优——W4 用评测集做（钩子 5 的演进素材）

### 1.3 工具签名草案（W3 网关注册的三个工具）

| 工具名 | 签名草案 | 权限 | 说明 |
|---|---|---|---|
| doc_search | `(query: String, topK: int) → List<DocChunk>` | 只读 | 制度文档 RAG 检索 |
| db_query | `(sql: String) → QueryResult`（仅放行 SELECT） | 只读 | 数据库只读查询 |
| ticket_create | `(title: String, content: String) → ticketId` | 写（经人审） | 工单创建 |

签名随实现修订；每次改动同步更新本节。

### 1.4 W1 Day4 风险 spike 结论：Spring AI MCP 端点 ✅ 能连（2026-10-19）

**结论：能连。** 全项目最大技术风险（W3 网关的前提）解除。验证实录（curl 模拟 MCP 客户端，全程可复现）：

| 步骤 | 请求 | 结果 |
|---|---|---|
| initialize | POST /mcp（protocolVersion 2025-06-18） | 返回 serverInfo=gateflow-gateway + `Mcp-Session-Id` 响应头 |
| notifications/initialized | 带 Session-Id | 204（协议要求，无响应体） |
| tools/list | 带 Session-Id | hello 工具完整呈现：描述 + 自动生成的 JSON Schema（参数 name/string/required） |
| tools/call hello | arguments={"name":"GateFlow"} | `"hello GateFlow from gateflow-gateway"`——Java 方法被 MCP 协议远程执行 |

**关键观察**：
1. 传输层为 Streamable HTTP：单一 `/mcp` 端点，POST 请求；响应在客户端 Accept 时以 SSE 格式（`event:message` + `data:{...}`）回流——SSE 自 2.0 起从"独立传输"降级为"流式应答格式"（八股点）
2. 会话有状态：initialize 签发 `Mcp-Session-Id`，后续请求必须携带——W3 的 JWT 过滤器与此 Session-Id 是两个层面（认证 vs 协议会话），注意区分
3. **端点默认无鉴权**：以上全部请求未携带任何凭证，直接成功——官方文档警告实测属实，W3 JWT 过滤器的必要性实锤
4. 踩坑记录：Spring AI 2.0.1 的注解在 `org.springframework.ai.mcp.annotation` 包（非 1.x 的 tool.annotation）；`@McpToolParam` 无 name 属性，参数名取自方法形参名（依赖 -parameters 编译参数，Spring Boot parent 默认开启）

**对策（原风险预案作废）**：无需备用方案，W3 直接在此骨架上叠加 JWT/限流/审计。

**给学生的真实客户端配置**（Cursor / Trae 通用）：
```json
{ "mcpServers": { "gateflow": { "url": "http://localhost:8090/mcp" } } }
```

### 1.5 worker 模块与 Conductor 连接（W1 Day2 落地）

- 模块：`workers/`（Spring Boot 3.5.16 + `org.conductoross:conductor-client:6.0.2`，Java 21，非 web 起步）
- worker 写法：官方 SDK 注解式——`@WorkerTask("任务名")` + `@InputParam`/`@OutputParam`（见 `EchoTasks`）；启动时 `WorkflowExecutor(conductorApi).initWorkersFromInstances(...)` 注册并开始轮询
- 连接地址：`gateflow.conductor-api`（`workers/src/main/resources/application.yml`，默认 `http://localhost:8080/api`）
- workflow 定义：`deploy/workflows/*.json`，注册 `PUT /api/metadata/workflow`，触发 `POST /api/workflow/{name}`；执行详情 `GET /api/workflow/{id}`（此版本无 `/status` 子路径）
- Day2 切片：`gateflow_demo` = 单任务 `echo_demo`，已跑通 COMPLETED（execution: 输入输出在 UI Executions 页可见）；后续节点按同模式原位扩展
- 踩坑记录：① conductor 容器偶发端口代理失效（8080 未监听），`docker compose up -d --force-recreate conductor` 解决；② Windows 命令行发中文 JSON 会被 GBK 编码搞坏，请求体一律写 UTF-8 文件再 `--data @file`

## 2. W2：人工审核与断点恢复

### 2.1 draft_review 工作流（W2D1）

```
rag_retriever → llm_answer → human_review(HUMAN) → echo(终态回显)
```

| 节点 | 类型 | 说明 |
|---|---|---|
| rag_retriever / llm_answer | SIMPLE | 复用 W1 现有 worker，不变 |
| human_review | **HUMAN（系统任务，零 Java 代码）** | 到达后挂起等待外部审批；输入携带草稿与问题；审批输出约定 `{decision: APPROVED\|REJECTED, comment}` |
| echo | SIMPLE | 终态回显（W2D2-3 由落库动作替换） |

预期行为（已实测验证，2026-10-21）：触发后流程停在 human_review（workflow RUNNING、该 task IN_PROGRESS，可无限期等待——状态在引擎库）；审批走 **`POST /api/tasks`**，体为 `{taskId, workflowInstanceId, status:"COMPLETED", outputData:{decision, comment}}`（PUT /api/tasks/{id} 不支持，实测 500）；写完引擎立即续跑。执行实录 7a38edd1：rag→llm 完成、HUMAN 挂起、批准后 echo 完成，最终输出携带审批结论。
W2 验收映射：①停在人工节点/批准走完 ✅D1（落库部分 D2-3 补）②SSE 逐 chunk（D4）③kill -9 恢复（D2-3）。

### 2.2 W2 终态（2026-10-21 收官，三项验收全过）

**新增工位**：db_query（JdbcTemplate，仅放行单条 SELECT）/ persist_draft（MyBatis-Plus，workflow_id 唯一键幂等）/ ticket_create（标题查重复用）。数据源：gateflow-mysql(3307)，表见 deploy/sql/init.sql。

**SWITCH 系统任务（踩坑实录）**：value-param 评估器的 expression 必须填**输入参数名**（如 "decision"），并在 inputParameters 里用 ${} 取值；直接写 "${review_ref.output.decision}" 会静默走 defaultCase。两条路径实测：APPROVED→persist+ticket 落库；REJECTED→不写库。

**任务超时与重试（内嵌 taskDefinition）**：llm_answer 嵌入 retryCount=2 / timeoutSeconds=120 / responseTimeoutSeconds=30 / TIME_OUT_WF。元数据 taskdef API 在 conductor:next 未找到可用路径，内嵌方式实测有效。

**kill -9 两阶段实验（W2 验收③）**：
- 阶段一：worker 先死→触发→rag SCHEDULED（队列等待）→重启 worker→领取执行→HUMAN→批准→落库（record_id=2）✅
- 阶段二：llm 执行中杀进程→孤儿任务 IN_PROGRESS→120s 准时 TIMED_OUT（TIME_OUT_WF）→POST /api/workflow/{id}/retry→**rag 单实例不重执行（输出重放）、llm 双实例（一次 TIMED_OUT + 一次 COMPLETED，LLM 真实被调两次）**→HUMAN ✅。at-least-once 与重放语义的执行级证据。
- 未观察到：响应超时自动重派（responseTimeoutSeconds=30 嵌入后 2 分钟内未见孤儿自动回队列）——引擎 sweeper 行为，红线内不深究，生产对策=任务定义超时+retry 接口/外部监控。

**SSE（验收②）**：GET /stream/ask?q=...（8081）→RAG 检索→DeepSeek 流式生成逐块推送。实测 143 chunks/1.35s，Content-Type=text/event-stream，末尾 event:done。实现：SseEmitter + OpenAiStreamingChatModel 三回调（onPartialResponse/onCompleteResponse/onError），每请求一线程（朴素实现，W4 换线程池——对应线程池八股）。

**知识库热重载**：POST /admin/knowledge/reload → volatile 换库（RagTasks.reload）。实测：新增场地预约制度.md→reload(5 docs)→仅新文档可答的问题正确回答并带出处。

**编码方法论（终版，替代 Day2 版）**：①命令行内联中文进 curl 参数=绝对禁止（服务端拒收）；②中文请求体必经 UTF-8 文件（Write 工具最稳）；③验证数据真身用 HEX/码点（unicode_escape），终端显示不可信（本会话显示层全程 GBK，数据实为干净 UTF-8）。

## 3. W3：网关三件套（JWT / 限流 / 审计）+ 三工具上线

### 3.1 架构决策：gateway 是纯门口，工具实现全在 workers

gateway 的 @McpTool 方法只做转发（RestClient → workers:8081 的 /api/search、/api/db-query、/api/ticket），不直接碰数据库/RAG。理由：①红线"网关只做四件事"（鉴权/限流/审计/工具注册），工具本体留在工具宿主进程；②同一实现同时服务两条消费路径（workflow 的 @WorkerTask 和 HTTP 的 REST——workers 里 ToolsApiController 直接复用 DbTasks/TicketTasks/RagTasks 的方法）；③与 agentgateway"代理到后端"的形态一致。代价：一跳 HTTP（内网 <5ms，可接受）。

### 3.2 JWT 校验流程

过滤器顺序（均为 OncePerRequestFilter，只拦 /mcp）：**Audit（最外层，记录一切）→ JWT → 限流**。
JWT：Authorization: Bearer <token>；HS256 签名（密钥来自 .env 的 JWT_SECRET，仅运行时）；校验签名+过期；sub 声明=客户端身份（后续限流与审计的 key）。缺失/伪造/过期 → 401 {"error":"unauthorized"}。签发：POST /admin/token?client=xxx（开发用，返回 token+过期时间，TTL 可配默认 24h）。
注意区分：JWT=应用层身份认证；Mcp-Session-Id=协议层会话（Day4 精读），互不相干。

### 3.3 限流：Redis 滑动窗口（ZSET + Lua 原子化）

```
key = ratelimit:{client}（窗口 60s，上限 max-requests 默认 10）
Lua 原子执行：ZREMRANGEBYSCORE(0, now-window) 清窗外 → ZCARD 计数 → ≥max 则拒绝 → 否则 ZADD(now, now-随机后缀) + EXPIRE
超限 → 429 {"error":"rate_limited"}
为什么滑动窗口不用令牌桶：目标恰是防"模型失控式突发"，令牌桶允许突发与目标相反；ZSET 一个键搞定、窗口语义直读。原子性靠 Lua 单线程执行（Redis 单线程执行脚本期间不插入其他命令——八股点）。
```

### 3.4 审计（同步版，W4 改 Kafka 异步）

最外层过滤器：ContentCachingRequestWrapper 缓存请求体 → doFilter 计时 → 事后解析 JSON-RPC（method、tools/call 时的工具名与参数截断 500 字符）→ 连同 client（401 时 unknown）、http_status、rejected_reason（none/unauthorized/rate_limited）、latency_ms 写 audit_logs。同步写是刻意的演进起点（钩子 3：压测暴露瓶颈后改 Kafka，commit 历史即证据）。

### 3.5 验收标准（立项文档原文五项）
①无 token→401 ②错 token→401 ③对 token→tools/list 三工具 + doc_search 调用成功 ④新 client 连续第 11 次→429 ⑤audit_logs 出现完整记录

### 3.6 W3 验收实录（2026-10-21，五项全过）

①无 token→401 ✓ ②伪造 token→401 ✓ ③对 token：握手+tools/list 三工具（doc_search/db_query/ticket_create）+ doc_search 调用返回"单笔报销上限 500 元"原文（672 字符）✓ ④新 client 连发 12 次：200×10 → **429×2**（窗口 60s/上限 10）✓ ⑤audit_logs 完整记录（含 tools/call 的工具名+参数摘要+25ms 耗时，429 行 rejected_reason=rate_limited）

追加修正：①init.sql 种子段改为 TRUNCATE+INSERT 幂等（此前追加 DDL 重跑导致数据翻倍，实查 20 行→修正 10 行）；②db_query 守卫拒绝改为业务错误返回（gateway 解析 error 字段输出"rejected: 仅放行 SELECT"），替代原先的 500。
审计初值：单次 /mcp 调用网关开销 4-6ms（限流判定+审计写入同步）——W4 压测的基线数字。
