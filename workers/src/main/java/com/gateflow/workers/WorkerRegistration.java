package com.gateflow.workers;

import com.netflix.conductor.sdk.workflow.executor.WorkflowExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 启动时向 Conductor 注册本进程能处理的任务，随后 SDK 开始轮询领活。
 */
@Component
public class WorkerRegistration implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(WorkerRegistration.class);

    private final String conductorApi;
    private final LlmTasks llmTasks;
    private final RagTasks ragTasks;

    public WorkerRegistration(@Value("${gateflow.conductor-api}") String conductorApi,
                              LlmTasks llmTasks,
                              RagTasks ragTasks) {
        this.conductorApi = conductorApi;
        this.llmTasks = llmTasks;
        this.ragTasks = ragTasks;
    }

    @Override
    public void run(String... args) {
        WorkflowExecutor executor = new WorkflowExecutor(conductorApi);
        executor.initWorkersFromInstances(List.of(new EchoTasks(), llmTasks, ragTasks));
        log.info("GateFlow workers online (echo_demo, llm_answer, rag_retriever), polling tasks from {}", conductorApi);
    }
}
