package com.gateflow.workers;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.gateflow.workers.entity.Ticket;
import com.gateflow.workers.mapper.TicketMapper;
import com.netflix.conductor.sdk.workflow.task.InputParam;
import com.netflix.conductor.sdk.workflow.task.OutputParam;
import com.netflix.conductor.sdk.workflow.task.WorkerTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

/**
 * 工单类任务执行器（W2）：ticket_create 创建报销/事务工单。
 * 同标题重复提交时复用已有工单（标题唯一键兜底防重）。
 */
@Component
public class TicketTasks {

    private static final Logger log = LoggerFactory.getLogger(TicketTasks.class);

    private final TicketMapper ticketMapper;

    public TicketTasks(TicketMapper ticketMapper) {
        this.ticketMapper = ticketMapper;
    }

    @WorkerTask("ticket_create")
    public @OutputParam("ticketId") Long create(@InputParam("title") String title,
                                                @InputParam("content") String content) {
        Ticket existing = ticketMapper.selectOne(
                new QueryWrapper<Ticket>().eq("title", title));
        if (existing != null) {
            log.info("ticket_create idempotent hit: title='{}' already id={}", title, existing.getId());
            return existing.getId();
        }
        Ticket t = new Ticket();
        t.setTitle(title);
        t.setContent(content);
        t.setStatus("OPEN");
        t.setCreatedBy("gateflow-workflow");
        try {
            ticketMapper.insert(t);
        } catch (DuplicateKeyException e) {
            Ticket raced = ticketMapper.selectOne(new QueryWrapper<Ticket>().eq("title", title));
            return raced != null ? raced.getId() : null;
        }
        log.info("ticket_create saved: '{}' -> id={}", title, t.getId());
        return t.getId();
    }
}
