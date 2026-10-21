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
