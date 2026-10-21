package com.gateflow.workers;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 开发者运维端点（W2）：知识库热重载——换/增语料后免重启进程。
 * 对应 README"使用你自己的知识库"三步：放文件 → curl 本端点 → 提问验证。
 */
@RestController
public class AdminController {

    private final RagTasks ragTasks;

    public AdminController(RagTasks ragTasks) {
        this.ragTasks = ragTasks;
    }

    @PostMapping("/admin/knowledge/reload")
    public Map<String, Object> reload() {
        int documents = ragTasks.reload();
        return Map.of("status", "reloaded", "documents", documents);
    }
}
