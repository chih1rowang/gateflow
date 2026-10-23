package com.gateflow.workers;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.gateflow.workers.entity.ApprovedDraft;
import com.gateflow.workers.mapper.ApprovedDraftMapper;
import com.netflix.conductor.sdk.workflow.task.InputParam;
import com.netflix.conductor.sdk.workflow.task.OutputParam;
import com.netflix.conductor.sdk.workflow.task.WorkerTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 数据库类任务执行器（W2，安全加固见 2026-10 审查报告）：
 * - db_query：双层只读防护。
 *   第一层（代码守卫）：仅放行单条 SELECT——拒绝非 SELECT 开头、语句内分号（多语句）、
 *   INTO OUTFILE/DUMPFILE（写文件）、FOR UPDATE/LOCK（加锁）、超长语句。
 *   第二层（数据库权限）：查询走专用只读账号 gateflow_ro，仅对 expense_records 一张表有
 *   SELECT 权限——守卫被绕过（如 UNION 读系统表）时由权限兜底。纵深防御：每层假设上一层会破。
 * - persist_draft：人工批准后的草稿落库（workflow_id 唯一键 = 幂等：重试不重复插入）
 */
@Component
public class DbTasks {

    private static final Logger log = LoggerFactory.getLogger(DbTasks.class);

    private final JdbcTemplate roJdbc;
    private final ApprovedDraftMapper draftMapper;

    public DbTasks(ApprovedDraftMapper draftMapper,
                   @org.springframework.beans.factory.annotation.Value("${gateflow.db.ro.url}") String roUrl,
                   @org.springframework.beans.factory.annotation.Value("${gateflow.db.ro.username}") String roUser,
                   @org.springframework.beans.factory.annotation.Value("${gateflow.db.ro.password}") String roPass) {
        // 只读账号独立连接池（上限 4）：压力测试（50 并发）显示"每请求新建连接"能扛但连接数无上界，
        // 真实突刺下会在 MySQL 侧堆积连接——池化封顶后多余的并发在 5 秒内拿到明确超时而不是拖垮数据库
        com.zaxxer.hikari.HikariConfig cfg = new com.zaxxer.hikari.HikariConfig();
        cfg.setJdbcUrl(roUrl);
        cfg.setUsername(roUser);
        cfg.setPassword(roPass);
        cfg.setMaximumPoolSize(4);
        cfg.setPoolName("gateflow-ro");
        cfg.setConnectionTimeout(5_000);
        this.roJdbc = new JdbcTemplate(new com.zaxxer.hikari.HikariDataSource(cfg));
        this.draftMapper = draftMapper;
    }

    @WorkerTask("db_query")
    public @OutputParam("rows") List<Map<String, Object>> query(@InputParam("sql") String sql) {
        String trimmed = sql == null ? "" : sql.trim();
        if (trimmed.endsWith(";")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1).trim();
        }
        String lower = trimmed.toLowerCase();
        if (!lower.startsWith("select")) {
            throw new IllegalArgumentException("db_query 仅放行 SELECT 查询，收到: " + sql);
        }
        if (trimmed.contains(";")) {
            throw new IllegalArgumentException("db_query 仅放行单条语句（检测到语句内分号）");
        }
        if (lower.contains("into outfile") || lower.contains("into dumpfile")) {
            throw new IllegalArgumentException("db_query 禁止 INTO OUTFILE/DUMPFILE");
        }
        if (lower.contains("for update") || lower.contains("lock in share mode")) {
            throw new IllegalArgumentException("db_query 禁止加锁子句");
        }
        if (trimmed.length() > 2000) {
            throw new IllegalArgumentException("db_query 语句过长（上限 2000 字符）");
        }
        List<Map<String, Object>> rows = roJdbc.queryForList(trimmed);
        log.info("db_query: {} rows returned", rows.size());
        return rows;
    }

    @WorkerTask("persist_draft")
    public @OutputParam("recordId") Long persist(@InputParam("workflowId") String workflowId,
                                                 @InputParam("question") String question,
                                                 @InputParam("draft") String draft,
                                                 @InputParam("comment") String comment) {
        // 幂等：同一次执行的重试（taskId 不变、workflowId 不变）不产生第二条记录
        ApprovedDraft existing = draftMapper.selectOne(
                new QueryWrapper<ApprovedDraft>().eq("workflow_id", workflowId));
        if (existing != null) {
            log.info("persist_draft idempotent hit: workflowId={} already saved as id={}", workflowId, existing.getId());
            return existing.getId();
        }
        ApprovedDraft record = new ApprovedDraft();
        record.setWorkflowId(workflowId);
        record.setQuestion(question);
        record.setDraft(draft);
        record.setComment(comment);
        try {
            draftMapper.insert(record);
        } catch (DuplicateKeyException e) {
            // 并发兜底：唯一键冲突则查回已有记录（try-then-insert 的竞态窗口）
            ApprovedDraft raced = draftMapper.selectOne(
                    new QueryWrapper<ApprovedDraft>().eq("workflow_id", workflowId));
            return raced != null ? raced.getId() : null;
        }
        log.info("persist_draft saved: workflowId={} -> id={}", workflowId, record.getId());
        return record.getId();
    }
}
