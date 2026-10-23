package com.gateflow.workers;

import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * SSE 流式问答（W2 验收②）：RAG 检索 + DeepSeek 流式生成，逐块推送给客户端。
 * SSE（Server-Sent Events）：HTTP 上的服务器单向推送协议，响应 Content-Type 为
 * text/event-stream，客户端（curl -N / 浏览器 EventSource）逐块收到 data: ... 事件。
 */
@RestController
public class StreamController {

    private static final Logger log = LoggerFactory.getLogger(StreamController.class);

    private final RagTasks ragTasks;
    private final OpenAiStreamingChatModel streamingModel;

    public StreamController(RagTasks ragTasks,
                            @Value("${gateflow.deepseek.base-url}") String baseUrl,
                            @Value("${gateflow.deepseek.api-key}") String apiKey,
                            @Value("${gateflow.deepseek.model-name}") String modelName) {
        this.ragTasks = ragTasks;
        this.streamingModel = OpenAiStreamingChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .modelName(modelName)
                .build();
    }

    @GetMapping("/stream/ask")
    public SseEmitter ask(@RequestParam("q") String question) {
        SseEmitter emitter = new SseEmitter(120_000L);
        List<String> chunks = ragTasks.retrieve(question);
        String context = String.join("\n----\n", chunks);
        log.info("stream/ask: q='{}' retrieved {} chunks, streaming starts", question, chunks.size());

        // 每请求一线程的朴素实现（守护线程：不阻塞 JVM 关闭）；W4 压测时换线程池（对应"线程池七参数"八股）
        Thread worker = new Thread(() -> {
            try {
                streamingModel.chat(List.of(
                        SystemMessage.from(LlmTasks.SYSTEM_PROMPT.formatted(context)),
                        UserMessage.from(question)),
                        new StreamingChatResponseHandler() {
                            @Override
                            public void onPartialResponse(String token) {
                                try {
                                    emitter.send(token);
                                } catch (Exception ignored) {
                                    // 客户端断开：停止发送（onComplete 后续仍会被调用）
                                }
                            }

                            @Override
                            public void onCompleteResponse(ChatResponse response) {
                                try {
                                    emitter.send(SseEmitter.event()
                                            .name("done")
                                            .data("[DONE]"));
                                } catch (Exception ignored) {
                                }
                                emitter.complete();
                                log.info("stream/ask completed, tokens used: {}", response.tokenUsage());
                            }

                            @Override
                            public void onError(Throwable error) {
                                emitter.completeWithError(error);
                            }
                        });
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        }, "sse-ask");
        worker.setDaemon(true);
        worker.start();
        return emitter;
    }
}
