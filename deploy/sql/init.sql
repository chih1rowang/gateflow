-- GateFlow W2：业务表初始化（社团经费报销场景）
-- 执行：docker exec -i gateflow-mysql mysql -uroot -p*** gateflow < init.sql

CREATE TABLE IF NOT EXISTS expense_records (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  activity_name VARCHAR(128) NOT NULL COMMENT '活动名称',
  item          VARCHAR(128) NOT NULL COMMENT '支出事项',
  amount        DECIMAL(10,2) NOT NULL COMMENT '金额',
  spender       VARCHAR(64)  COMMENT '经办人',
  spent_at      DATE COMMENT '支出日期',
  created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_spent_at (spent_at),
  INDEX idx_activity (activity_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='经费支出流水（db_query 只读演示）';

CREATE TABLE IF NOT EXISTS tickets (
  id         BIGINT AUTO_INCREMENT PRIMARY KEY,
  title      VARCHAR(200) NOT NULL COMMENT '工单标题',
  content    TEXT COMMENT '工单内容',
  status     VARCHAR(20) NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN/PROCESSING/DONE',
  created_by VARCHAR(64) DEFAULT 'ai-assistant' COMMENT '创建者',
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_workflow (created_by(20), title(80))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工单（ticket_create 写入）';

CREATE TABLE IF NOT EXISTS approved_drafts (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  workflow_id VARCHAR(64) COMMENT '来源执行ID（幂等键）',
  question    TEXT,
  draft       TEXT COMMENT 'AI起草、人批准的草稿',
  comment     VARCHAR(500) COMMENT '审批意见',
  created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_workflow_id (workflow_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='已批准草稿存档（persist_draft 写入）';

-- 种子数据（幂等：先清再插，重复执行不翻倍）
TRUNCATE expense_logs_never;
INSERT INTO expense_records (activity_name, item, amount, spender, spent_at) VALUES
('摄影社春季外拍采风', '包车费（往返）', 320.00, '王同学', '2026-09-05'),
('摄影社春季外拍采风', '景点门票（10人）', 200.00, '王同学', '2026-09-05'),
('摄影社春季外拍采风', '饮用水及应急药品', 45.00, '王同学', '2026-09-05'),
('编程社秋季算法训练营', '场地租用（2天）', 600.00, '李同学', '2026-09-12'),
('编程社秋季算法训练营', '午餐（20人）', 460.00, '李同学', '2026-09-12'),
('编程社秋季算法训练营', '打印讲义', 88.50, '李同学', '2026-09-13'),
('迎新晚会', '舞台布置材料', 412.00, '张同学', '2026-09-20'),
('迎新晚会', '音响设备租赁', 800.00, '张同学', '2026-09-20'),
('迎新晚会', '嘉宾礼品', 350.00, '张同学', '2026-09-21'),
('社团联合招新', '易拉宝与传单', 275.00, '陈同学', '2026-09-25');

-- W3：网关审计表（gateway 同步写入；W4 改 Kafka 异步）
CREATE TABLE IF NOT EXISTS audit_logs (
  id             BIGINT AUTO_INCREMENT PRIMARY KEY,
  client_name    VARCHAR(64) NOT NULL COMMENT '调用方（JWT sub；401 时为 unknown）',
  http_status    INT COMMENT '本次响应状态码',
  rpc_method     VARCHAR(64) COMMENT 'JSON-RPC 方法（initialize/tools_list/tools_call）',
  tool_name      VARCHAR(64) COMMENT 'tools/call 时的工具名',
  params_snippet TEXT COMMENT '请求参数截断（500 字符）',
  rejected_reason VARCHAR(32) NOT NULL DEFAULT 'none' COMMENT 'none/unauthorized/rate_limited',
  latency_ms     INT,
  created_at     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_client_time (client_name, created_at),
  INDEX idx_tool_time (tool_name, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MCP 网关调用审计';
