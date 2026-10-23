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
            // send 是异步的：投递失败不会在下方 catch 抛出，必须在回调里兜底（此前 catch 接不住，
            // Kafka 不可用期间审计会静默丢失）。insertIgnore 按 msg_id 幂等，回调与消费端重复写也无害
            kafka.send(TOPIC, row.getClientName(), payload).whenComplete((ignored, e) -> {
                if (e != null) {
                    log.error("audit kafka delivery failed, fallback to sync insert: {}", e.getMessage());
                    insert(row);
                }
            });
        } catch (Exception e) {
            // 同步失败（序列化/本地缓冲）仍走直写兜底
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
