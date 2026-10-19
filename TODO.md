# GateFlow 任务清单

> 依据《立项文档》§4 六周周表做格式转换，勾选制；与 commit 历史互为证据。红线：不读引擎源码；网关只做四件事；前端只做一页三 tab。

## W1（10/19–10/25）切片：提问 → RAG 检索 → LLM 回答（curl 即可）

- [ ] Day1 装环境：JDK21 ✅(AI代办) / Docker ✅ / pnpm ✅ / Maven镜像 ✅
- [ ] Day1 仓库初始化 Step 0：骨架 / .gitignore / compose 底座 / 空 CI / README（AI 已代办，待你验收）
- [ ] Day1 补课层0：撞墙实验 A/B/C，能讲清"软墙 vs 硬墙"
- [x] Day2 docker 起 Conductor，localhost:5000 UI 可用（8080 端口代理故障已修复）；UI Executions 页已有第一条 demo 执行可看
- [x] Day2 补课层1：DeepSeek 充值✅（余额 57.61 元，key 已存 .env）；裸调改为 AI 代验（余额 API 已通），messages 结构随 Day3 llm_answer 讲解
- [x] Day2 补课层2：取消独立实验（学生决定），并入 Day3 llm_answer worker 边写边讲
- [ ] Day3 跑通 aideepin；精读 RAG + MCP 模块，笔记存 private/notes/
- [ ] Day3 补课层3：配 filesystem MCP server；读 modelcontextprotocol.io Introduction
- [x] Day3 llm_answer worker：LangChain4j 1.22.0 → DeepSeek（deepseek-chat），workflow v2 两步链 llm_answer→echo 跑通（2026-10-19，AI 代跑 2588ms/740字，中文完好）
- [x] Day4 风险 spike ✅（2026-10-19）：Spring AI 2.0.1 MCP 端点验证通过（initialize→tools/list→tools/call 全链，hello 工具被远程调用）。结论+踩坑已入 design.md §1.4。gateway 骨架上线 8090 端口。注解坑：2.0 包路径 org.springframework.ai.mcp.annotation，@McpToolParam 无 name 属性
- [x] Day5 rag_retriever ✅（2026-10-19）：4 份报销制度文档入库（本地 BGE-small-zh，ONNX 免费）+ LlmTasks 升级 RAG 版（system 携带片段+限定出处）+ workflow v3 三步链跑通。验收实录：500元上限问题答对且带引用（3 chunks, top 0.8571）——**W1 验收④达成**
- [ ] Day6 W1 验收五项：①:5000 UI ②execution 节点可见 ③repo 已 push ④RAG→LLM 回答原文 ⑤spike 结论
- [ ] Day6 场景已定：社团经费报销（AI 代定；演示文档由 AI 生成，之后可随时换真实资料）；第一层话术据此定稿
- [ ] Day7 W1《面试问答卡》+ 15 分钟模拟追问

## W2（10/26–11/1）切片：+ 人工审核节点 + 断点恢复

- [ ] 三个 worker：文档检索 / 数据库只读查询 / 工单操作
- [ ] human task：workflow 停在人工节点，批准后走完落库
- [ ] SSE 流式接口：curl 逐 chunk 收到输出
- [ ] kill -9 杀 worker 重启，未完成任务被重新领取跑完
- [ ] 补课层5（durable execution）+ W2 问答卡 + 模拟追问

## W3（11/2–11/8）切片：Claude Code 经网关调工具（401/429/审计）

- [ ] gateway 模块：Spring AI MCP Server + @McpTool 三工具
- [ ] JWT 过滤器：无 token→401 / 错 token→401 / 对 token→成功
- [ ] Redis 滑动窗口限流：连续 N+1 次→429
- [ ] MySQL 审计表（同步版，W4 改 Kafka 异步）
- [ ] **开始投日常实习（简历"开发中"版）**
- [ ] 补课层4（网关为什么存在）+ W3 问答卡

## W4（11/9–11/15）切片：compose 一键全家桶 + 数字出炉

- [ ] Kafka 审计异步落库 + 幂等消费
- [ ] Nginx 反代 + docker-compose 7 服务
- [ ] 清空容器后 compose up，10 分钟内全家桶可用
- [ ] JMeter 压测出 QPS / P95
- [ ] 自建 100 条评测集测 RAG 召回 + 一轮优化（成对数字，如 68%→83%）
- [ ] W4 问答卡

## W5（11/16–11/22）切片：+ 面板 + 开源成品

- [ ] Vue3 + Naive UI 三页：任务看板 / 审计流水 / 成本统计
- [ ] 双语 README：架构图 + 竞品对比表 + 压测报告 + 一键部署
- [ ] GitHub Actions CI 绿
- [ ] 找一位同学按 README 独立跑通（真实测试）
- [ ] 微作品（有余力才做）：注解化 MCP Starter 或独立小 MCP server
- [ ] 简历换完整版 + W5 问答卡

## W6（11/23–11/29）防御冲刺

- [ ] 各阶段问答卡汇总成总复习册
- [ ] 追问 Top10 逐题写"标准答案 + 我的取舍"两层
- [ ] 两轮模拟面试不卡壳
- [ ] 简历终稿
