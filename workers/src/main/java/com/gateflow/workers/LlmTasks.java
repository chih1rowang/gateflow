package com.gateflow.workers;

import com.netflix.conductor.sdk.workflow.task.InputParam;
import com.netflix.conductor.sdk.workflow.task.OutputParam;
import com.netflix.conductor.sdk.workflow.task.WorkerTask;
import com.gateflow.workers.entity.LlmUsage;
import com.gateflow.workers.mapper.LlmUsageMapper;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * llm_answer 任务执行器（RAG 版）：
 * system 消息携带检索到的制度片段（限定模型只依据资料回答），
 * user 消息携带问题。每次调用把 token 用量写入 llm_usage（成本统计页数据源）。
 */
@Component
public class LlmTasks {

    private static final Logger log = LoggerFactory.getLogger(LlmTasks.class);

    public static final String SYSTEM_PROMPT = """
            你是社团经费报销制度助手。请仅根据下面提供的【制度资料】回答问题；
            资料中没有依据时，明确回答"制度资料中未规定"，不得编造。
            回答末尾注明依据来自哪份资料。制度资料如下：
            %s""";

    private final OpenAiChatModel model;
    private final LlmUsageMapper usageMapper;

    public LlmTasks(@Value("${gateflow.deepseek.base-url}") String baseUrl,
                    @Value("${gateflow.deepseek.api-key}") String apiKey,
                    @Value("${gateflow.deepseek.model-name}") String modelName,
                    LlmUsageMapper usageMapper) {
        this.model = OpenAiChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .modelName(modelName)
                .build();
        this.usageMapper = usageMapper;
    }

    @WorkerTask("llm_answer")
    public @OutputParam("answer") String answer(@InputParam("question") String question,
                                                @InputParam("chunks") List<String> chunks,
                                                @InputParam(value = "workflowId", required = false) String workflowId) {
        long start = System.currentTimeMillis();
        String context = String.join("\n----\n", chunks);
        ChatResponse response = model.chat(List.of(
                SystemMessage.from(SYSTEM_PROMPT.formatted(context)),
                UserMessage.from(question)));
        String answer = response.aiMessage().text();
        recordUsage(workflowId, response);
        log.info("llm_answer done in {} ms, question='{}', context={} chunks, answer {} chars",
                System.currentTimeMillis() - start, question, chunks.size(), answer.length());
        return answer;
    }

    private void recordUsage(String workflowId, ChatResponse response) {
        try {
            if (response.tokenUsage() == null) return;
            LlmUsage u = new LlmUsage();
            u.setWorkflowId(workflowId);
            u.setInputTokens(response.tokenUsage().inputTokenCount());
            u.setOutputTokens(response.tokenUsage().outputTokenCount());
            u.setTotalTokens(response.tokenUsage().totalTokenCount());
            usageMapper.insert(u);
        } catch (Exception e) {
            log.warn("token usage record failed: {}", e.getMessage());
        }
    }
}
