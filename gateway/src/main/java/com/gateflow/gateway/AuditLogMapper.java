package com.gateflow.gateway;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;

public interface AuditLogMapper extends BaseMapper<AuditLog> {

    /** 幂等插入：msg_id 唯一键冲突时静默忽略（Kafka 至少一次投递的去重落点） */
    @Insert("INSERT IGNORE INTO audit_logs (client_name, http_status, rpc_method, tool_name, " +
            "params_snippet, rejected_reason, latency_ms, msg_id) VALUES " +
            "(#{clientName}, #{httpStatus}, #{rpcMethod}, #{toolName}, " +
            "#{paramsSnippet}, #{rejectedReason}, #{latencyMs}, #{msgId})")
    @Options(useGeneratedKeys = true)
    void insertIgnore(AuditLog row);
}
