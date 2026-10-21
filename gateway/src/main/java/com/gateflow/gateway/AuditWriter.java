package com.gateflow.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

/**
 * 审计写入器（W4）：同步直写 与 Kafka 异步双模式。
 * - sync：请求线程直接写 MySQL（W3 原始形态，压测对照组）
 * - kafka：请求线程只发消息（毫秒级返回），消费者异步落库（压测实验组）
 *          幂等消费：msg_id 唯一键 + INSERT IGNORE——Kafka 至少一次投递下重复消息不产生重复行
 * 模式经 gateflow.audit.mode 切换，便于成对压测（演进证据链见 design.md）。
 */
@Service
public class AuditWriter {

    public static final String TOPIC = "gateflow-audit";
    private static final Logger log = LoggerFactory.getLogger(AuditWriter.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final AuditLogMapper mapper;
    private final KafkaTemplate<String, String> kafka;
    private final boolean kafkaMode;

    public AuditWriter(AuditLogMapper mapper,
                       KafkaTemplate<String, String> kafka,
                       @org.springframework.beans.factory.annotation.Value("${gateflow.audit.mode}") String mode) {
        this.mapper = mapper;
        this.kafka = kafka;
        this.kafkaMode = "kafka".equalsIgnoreCase(mode);
        log.info("AuditWriter mode = {}", mode);
    }

    /** 供 AuditFilter 调用：按模式分发 */
    public void write(AuditLog row) {
        if (!kafkaMode) {
            insert(row);
            return;
        }
        try {
            row.setMsgId(UUID.randomUUID().toString());
            String payload = MAPPER.writeValueAsString(row);
            kafka.send(TOPIC, row.getClientName(), payload);
        } catch (Exception e) {
            // 发送失败不阻断主链路；兜底策略见 design.md（本地消息表为 Roadmap）
            log.error("audit kafka send failed, fallback to sync insert: {}", e.getMessage());
            insert(row);
        }
    }

    /** 消费者：INSERT IGNORE 按 msg_id 幂等 */
    @KafkaListener(topics = TOPIC, groupId = "gateflow-audit-consumer")
    public void consume(String payload) {
        try {
            AuditLog row = MAPPER.readValue(payload, AuditLog.class);
            insert(row);
        } catch (Exception e) {
            log.error("audit consume failed: {}", e.getMessage());
        }
    }

    private void insert(AuditLog row) {
        try {
            mapper.insertIgnore(row);
        } catch (Exception e) {
            log.error("audit insert failed: {}", e.getMessage());
        }
    }
}
