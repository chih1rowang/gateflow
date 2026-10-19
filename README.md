# GateFlow

> A Secure MCP Tool Gateway with Durable Agent Orchestration.

**Status: 早期开发中**（2026-10 启动，纵向切片推进：网关 → 编排 → 一键部署）

让 AI 编程助手（Claude Code / Cursor 等）**安全、统一、可靠**地调用内部系统能力（数据库 / 文档 / 工单）的自托管 Java 平台——

- **对外**：一个 MCP 服务器（AI 客户端视角永远简单，插上即用）
- **对内**：MCP 工具网关（JWT 鉴权 / Redis 限流 / 调用审计 / 工具注册）+ Conductor 持久化任务编排（重试 / 断点恢复 / 人工审核）

## 技术栈

Java 21 · Spring Boot 3.5 · Spring AI 2.0（MCP Server, Streamable HTTP）· LangChain4j + DeepSeek · MySQL · Redis · Kafka · Conductor · Vue3 + Naive UI · Docker Compose + Nginx

## 仓库结构（随开发逐步填充）

| 目录 | 说明 | 状态 |
|---|---|---|
| `gateway/` | MCP 工具网关（鉴权/限流/审计/工具注册） | W3 动工 |
| `workers/` | Conductor workers（RAG 检索 / DB 只读 / 工单） | W1 起 |
| `panel/` | Vue3 面板（看板/审计/成本三页） | W5 |
| `deploy/` | docker-compose / Nginx / 压测脚本 | 底座已就绪 |
| `docs/` | design.md（渐进式技术设计） | 第 1 节已就绪 |

## 快速开始

待 W4 一键部署就绪后补充（`docker compose up` 全家桶）。

---
*README 将随项目推进完善为双语版，含架构图、竞品对比与压测报告。*
