package com.gateflow.workers;

import com.netflix.conductor.sdk.workflow.task.InputParam;
import com.netflix.conductor.sdk.workflow.task.OutputParam;
import com.netflix.conductor.sdk.workflow.task.WorkerTask;

/**
 * W1 Day2 第一个 worker：原样返回输入文本。
 * 它的唯一意义是打通"Conductor 派活 → 我们代码接活 → 结果回写"这条链路；
 * 后续 rag_retriever / llm_answer 按同样模式往里加。
 */
public class EchoTasks {

    @WorkerTask("echo_demo")
    public @OutputParam("echoed") String echo(@InputParam("text") String text) {
        return text;
    }
}
