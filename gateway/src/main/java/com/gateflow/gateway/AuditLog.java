package com.gateflow.gateway;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/** 审计表实体（audit_logs） */
@TableName("audit_logs")
public class AuditLog {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String clientName;
    private Integer httpStatus;
    private String rpcMethod;
    private String toolName;
    private String paramsSnippet;
    private String rejectedReason;
    private Integer latencyMs;
    private LocalDateTime createdAt;
    /** 幂等消息键（Kafka 模式下生成；唯一索引去重） */
    private String msgId;

    public String getMsgId() { return msgId; }
    public void setMsgId(String msgId) { this.msgId = msgId; }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getClientName() { return clientName; }
    public void setClientName(String clientName) { this.clientName = clientName; }
    public Integer getHttpStatus() { return httpStatus; }
    public void setHttpStatus(Integer httpStatus) { this.httpStatus = httpStatus; }
    public String getRpcMethod() { return rpcMethod; }
    public void setRpcMethod(String rpcMethod) { this.rpcMethod = rpcMethod; }
    public String getToolName() { return toolName; }
    public void setToolName(String toolName) { this.toolName = toolName; }
    public String getParamsSnippet() { return paramsSnippet; }
    public void setParamsSnippet(String paramsSnippet) { this.paramsSnippet = paramsSnippet; }
    public String getRejectedReason() { return rejectedReason; }
    public void setRejectedReason(String rejectedReason) { this.rejectedReason = rejectedReason; }
    public Integer getLatencyMs() { return latencyMs; }
    public void setLatencyMs(Integer latencyMs) { this.latencyMs = latencyMs; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
