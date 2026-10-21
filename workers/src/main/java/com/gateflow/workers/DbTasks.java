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
 * 数据库类任务执行器（W2）：
 * - db_query：只读查询（仅放行单条 SELECT，防注入式越权）
 * - persist_draft：人工批准后的草稿落库（workflow_id 唯一键 = 幂等：重试不重复插入）
 */
@Component
public class DbTasks {

    private static final Logger log = LoggerFactory.getLogger(DbTasks.class);

    private final JdbcTemplate jdbc;
    private final ApprovedDraftMapper draftMapper;

    public DbTasks(JdbcTemplate jdbc, ApprovedDraftMapper draftMapper) {
        this.jdbc = jdbc;
        this.draftMapper = draftMapper;
    }

    @WorkerTask("db_query")
    public @OutputParam("rows") List<Map<String, Object>> query(@InputParam("sql") String sql) {
        String trimmed = sql == null ? "" : sql.trim();
        if (trimmed.endsWith(";")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        if (!trimmed.toLowerCase().startsWith("select")) {
            throw new IllegalArgumentException("db_query 仅放行 SELECT 查询，收到: " + sql);
        }
        List<Map<String, Object>> rows = jdbc.queryForList(trimmed);
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
