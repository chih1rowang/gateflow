# GateFlow 技术设计（渐进式）

> 本文档永远比代码先行一小步，严禁瀑布式写全。当前只有第 1 节（W1 所需）；
> W2 末补 human task 流程与 SSE 接口定义；W3 末补审计表 DDL、JWT 校验流程、限流算法伪码；W4 末补 compose 拓扑与压测方案。
> 与代码对不上 = vibe coding 跑偏的信号，立即停下核对。

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

### 1.2 第一条 workflow：rag_retriever → llm_answer → echo

当前实况（Day3 后）：v2 已上线 = `llm_answer → echo` 两步链真实跑通；`rag_retriever` 待 W1 后半补入最前端。

| 节点 | 类型 | 输入 | 输出 | 实现 |
|---|---|---|---|---|
| rag_retriever | simple | `question: String` | `chunks: List<String>` | 待实现：langchain4j easy-rag + 本地 BGE 中文向量模型，TopK 待定（W4 评测调优） |
| llm_answer | simple | `question` | `answer: String` | ✅ 已实现：langchain4j-open-ai（OpenAI 兼容接口指向 DeepSeek baseUrl，模型 deepseek-chat） |
| echo | simple | `answer` | `answer` | ✅ 已实现：链路占位节点（W2 由人工审核节点替换） |

W1 验收：Conductor UI（localhost:5000）Executions 页能看到每个节点的输入输出；llm_answer 的输出是基于自己文档生成的真实回答原文（待 rag_retriever 就位后达成"基于文档"）。

### 1.3 工具签名草案（W3 网关注册的三个工具）

| 工具名 | 签名草案 | 权限 | 说明 |
|---|---|---|---|
| doc_search | `(query: String, topK: int) → List<DocChunk>` | 只读 | 制度文档 RAG 检索 |
| db_query | `(sql: String) → QueryResult`（仅放行 SELECT） | 只读 | 数据库只读查询 |
| ticket_create | `(title: String, content: String) → ticketId` | 写（经人审） | 工单创建 |

签名随实现修订；每次改动同步更新本节。

### 1.4 worker 模块与 Conductor 连接（W1 Day2 落地）

- 模块：`workers/`（Spring Boot 3.5.16 + `org.conductoross:conductor-client:6.0.2`，Java 21，非 web 起步）
- worker 写法：官方 SDK 注解式——`@WorkerTask("任务名")` + `@InputParam`/`@OutputParam`（见 `EchoTasks`）；启动时 `WorkflowExecutor(conductorApi).initWorkersFromInstances(...)` 注册并开始轮询
- 连接地址：`gateflow.conductor-api`（`workers/src/main/resources/application.yml`，默认 `http://localhost:8080/api`）
- workflow 定义：`deploy/workflows/*.json`，注册 `PUT /api/metadata/workflow`，触发 `POST /api/workflow/{name}`；执行详情 `GET /api/workflow/{id}`（此版本无 `/status` 子路径）
- Day2 切片：`gateflow_demo` = 单任务 `echo_demo`，已跑通 COMPLETED（execution: 输入输出在 UI Executions 页可见）；后续节点按同模式原位扩展
- 踩坑记录：① conductor 容器偶发端口代理失效（8080 未监听），`docker compose up -d --force-recreate conductor` 解决；② Windows 命令行发中文 JSON 会被 GBK 编码搞坏，请求体一律写 UTF-8 文件再 `--data @file`
