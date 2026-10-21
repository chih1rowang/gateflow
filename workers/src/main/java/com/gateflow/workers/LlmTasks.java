package com.gateflow.workers;

import com.netflix.conductor.sdk.workflow.task.InputParam;
import com.netflix.conductor.sdk.workflow.task.OutputParam;
import com.netflix.conductor.sdk.workflow.task.WorkerTask;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * llm_answer 任务执行器（Day5 升级为 RAG 版）：
 * system 消息携带检索到的制度片段（限定模型只依据资料回答），
 * user 消息携带问题。这是 RAG 的标准消息构造方式。
 */
@Component
public class LlmTasks {

    private static final Logger log = LoggerFactory.getLogger(LlmTasks.class);

    /** RAG 受限生成的 system 模板（StreamController 复用） */
    public static final String SYSTEM_PROMPT = """
            你是社团经费报销制度助手。请仅根据下面提供的【制度资料】回答问题；
            资料中没有依据时，明确回答"制度资料中未规定"，不得编造。
            回答末尾注明依据来自哪份资料。制度资料如下：
            %s""";

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
    public @OutputParam("answer") String answer(@InputParam("question") String question,
                                                @InputParam("chunks") List<String> chunks) {
        long start = System.currentTimeMillis();
        String context = String.join("\n----\n", chunks);
        String answer = model.chat(List.of(
                SystemMessage.from(SYSTEM_PROMPT.formatted(context)),
                UserMessage.from(question))).aiMessage().text();
        log.info("llm_answer done in {} ms, question='{}', context={} chunks, answer {} chars",
                System.currentTimeMillis() - start, question, chunks.size(), answer.length());
        return answer;
    }
}
