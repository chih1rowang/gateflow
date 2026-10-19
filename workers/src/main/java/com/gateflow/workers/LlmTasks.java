package com.gateflow.workers;

import com.netflix.conductor.sdk.workflow.task.InputParam;
import com.netflix.conductor.sdk.workflow.task.OutputParam;
import com.netflix.conductor.sdk.workflow.task.WorkerTask;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * llm_answer 任务执行器：把问题发给 DeepSeek，取回生成的文本。
 * DeepSeek 提供 OpenAI 兼容接口，因此用 LangChain4j 的 OpenAI 客户端、仅切换 baseUrl 即可。
 */
@Component
public class LlmTasks {

    private static final Logger log = LoggerFactory.getLogger(LlmTasks.class);

    private final OpenAiChatModel model;

    public LlmTasks(@Value("${gateflow.deepseek.base-url}") String baseUrl,
                    @Value("${gateflow.deepseek.api-key}") String apiKey,
                    @Value("${gateflow.deepseek.model-name}") String modelName) {
        this.model = OpenAiChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .modelName(modelName)
                .build();
    }

    @WorkerTask("llm_answer")
    public @OutputParam("answer") String answer(@InputParam("question") String question) {
        long start = System.currentTimeMillis();
        String answer = model.chat(question);
        log.info("llm_answer done in {} ms, question='{}', answer length={} chars",
                System.currentTimeMillis() - start, question, answer.length());
        return answer;
    }
}
