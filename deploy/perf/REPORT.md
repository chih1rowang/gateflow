# GateFlow 压测与评测报告（W4，2026-10-21）

环境：Windows 11 单机，Docker Desktop（WSL2）。全部服务容器化：nginx→gateway→(redis/mysql/kafka)，workers、conductor、mysql、redis、kafka 各一容器。
工具：Apache JMeter 5.6.3（CLI 模式），JDK 21。

## 一、MCP 网关压测（验收②）

场景：POST /mcp（tools/list，固定合法会话与令牌，经 nginx:8088 完整链路）。50 并发线程，ramp 5s，持续 60s。两轮同机同负载（后台均有等量消费者积压争用，成对公平）。

| 审计模式 | 请求数 | QPS | P50 | **P95** | P99 | 错误 |
|---|---|---|---|---|---|---|
| 同步写 MySQL（W3 形态） | 76,993 | 1,290 | 33ms | **64ms** | 115ms | 0 |
| **Kafka 异步（W4 形态）** | 328,156 | **5,497** | 9ms | **11ms** | 13ms | 0 |

结论：审计异步化后吞吐 **×4.3**、P95 **64ms→11ms**；请求路径与落库解耦（MySQL 故障不再阻塞调用）。审计完整性：同步轮 76,994 行=请求+1（握手）；Kafka 轮经消费者幂等落库（msg_id 唯一键）。

## 二、压测过程中的两个真实发现（面试素材）

1. **MCP 会话上限**：以 initialize 灌压会在约 10 万请求处触发 503——每次 initialize 新建一个协议会话且不释放，会话存储打满。稳态负载应测 tools/call 类会话内调用（已改）；也说明网关需要会话过期回收（Roadmap）。
2. **消费者单条写入瓶颈**：Kafka 消费者逐条 INSERT，追积压速率约 100 条/s——高吞吐审计需要批量消费+批量落库（Roadmap，面试可讲"我知道下一步优化在哪"）。

## 三、RAG 召回率评测（验收③）

- 评测集：**100 条**自建（问题→期望文档），覆盖 9 份制度文档；标注人=作者本人（局限如实说明）。
- 指标：recall@3（Top3 中含期望文档的片段即命中）。
- 对照：纯向量（BGE-small-zh）vs 混合检索（BM25 中文字符二元组 + 向量，各取深度候选 50，**RRF 融合** k=60，输出 Top3）。

| 检索模式 | 命中 | **recall@3** |
|---|---|---|
| 纯向量（W1 形态） | 97/100 | **97.0%** |
| **混合检索+RRF（W4 上线）** | 100/100 | **100.0%** |

诚实分析：①语料仅 19 片段、Top3 覆盖 16%，存在天花板效应，差距数字不可外推到大规模语料；②3 个纯向量未命中项均为"字面精确术语/数字"类问题（如涂改处数、比价阈值），与 BM25 补短板的理论预期一致；③优化已设为生产默认（`gateflow.rag.hybrid: true`），评测脚本 `RecallEvalTest` 可复现。

## 四、可复现命令

```bash
# 压测（先放宽限流：GATEFLOW_RATELIMIT_MAXREQUESTS=1000000 docker compose up -d gateway）
jmeter -n -t deploy/perf/loadtest2.jmx   # 令牌/会话/JTL 路径经 sed 注入，见 git 历史
# 召回评测
cd workers && mvn test -Dtest=RecallEvalTest
# 全家桶
cd deploy && docker compose down -v && docker compose up -d --build   # 实测：清库后 7s 可用（首建 89s）
```
