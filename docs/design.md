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

| 节点 | 类型 | 输入 | 输出 | 实现 |
|---|---|---|---|---|
| rag_retriever | simple | `question: String` | `chunks: List<String>` | langchain4j easy-rag + 本地 in-process BGE 中文向量模型（免费），TopK 待定（W4 评测调优） |
| llm_answer | simple | `question`、`chunks` | `answer: String` | langchain4j-open-ai 配 DeepSeek baseUrl，回答需附来源 |
| echo | simple | `answer` | `answer` | 打通链路的占位节点（人审节点 W2 替换它） |

W1 验收：Conductor UI（localhost:5000）Executions 页能看到每个节点的输入输出；llm_answer 的输出是基于自己文档生成的真实回答原文。

### 1.3 工具签名草案（W3 网关注册的三个工具）

| 工具名 | 签名草案 | 权限 | 说明 |
|---|---|---|---|
| doc_search | `(query: String, topK: int) → List<DocChunk>` | 只读 | 制度文档 RAG 检索 |
| db_query | `(sql: String) → QueryResult`（仅放行 SELECT） | 只读 | 数据库只读查询 |
| ticket_create | `(title: String, content: String) → ticketId` | 写（经人审） | 工单创建 |

签名随实现修订；每次改动同步更新本节。
