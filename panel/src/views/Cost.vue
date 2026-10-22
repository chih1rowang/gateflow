<template>
  <n-space vertical>
    <n-space align="center">
      <n-h2 style="margin:0">成本统计</n-h2>
      <n-text depth="3">调用与 token 用量（token 数为真实记录，按 DeepSeek 官网价目可折算费用）</n-text>
      <n-button size="small" @click="load">刷新</n-button>
    </n-space>

    <n-grid :cols="4" :x-gap="12">
      <n-gi><n-card size="small"><n-statistic label="总调用量" :value="s.totalCalls ?? 0" /></n-card></n-gi>
      <n-gi><n-card size="small"><n-statistic label="被拒绝(401/429)" :value="s.rejected ?? 0" /></n-card></n-gi>
      <n-gi><n-card size="small"><n-statistic label="平均耗时" :value="s.avgLatencyMs ?? 0"><template #suffix>ms</template></n-statistic></n-card></n-gi>
      <n-gi><n-card size="small"><n-statistic label="LLM 调用" :value="s.llm?.calls ?? 0" /></n-card></n-gi>
    </n-grid>

    <n-grid :cols="2" :x-gap="12">
      <n-gi>
        <n-card size="small" title="LLM token 用量（真实记录）">
          <n-space vertical>
            <n-statistic label="输入 tokens" :value="s.llm?.inputTokens ?? 0" />
            <n-statistic label="输出 tokens" :value="s.llm?.outputTokens ?? 0" />
            <n-statistic label="合计 tokens" :value="s.llm?.totalTokens ?? 0" />
            <n-text depth="3" style="font-size:12px">嵌入检索用本地模型（BGE），不产生 API 费用</n-text>
          </n-space>
        </n-card>
      </n-gi>
      <n-gi>
        <n-card size="small" title="按工具/方法调用量">
          <n-table size="small" :bordered="false">
            <thead><tr><th>名称</th><th style="text-align:right">调用数</th><th style="text-align:right">平均耗时ms</th></tr></thead>
            <tbody>
              <tr v-for="t in s.byTool || []" :key="t.name">
                <td>{{ t.name }}</td>
                <td style="text-align:right">{{ t.calls }}</td>
                <td style="text-align:right">{{ t.avg_ms }}</td>
              </tr>
            </tbody>
          </n-table>
        </n-card>
      </n-gi>
    </n-grid>

    <n-card size="small" title="按客户端调用 Top10">
      <n-table size="small" :bordered="false">
        <thead><tr><th>客户端</th><th style="text-align:right">调用数</th></tr></thead>
        <tbody>
          <tr v-for="c in s.byClient || []" :key="c.client_name">
            <td>{{ c.client_name }}</td>
            <td style="text-align:right">{{ c.calls }}</td>
          </tr>
        </tbody>
      </n-table>
    </n-card>
  </n-space>
</template>

<script setup>
import { ref, onMounted } from 'vue'

const s = ref({})

async function load() {
  const r = await fetch('/admin/stats')
  s.value = await r.json()
}
onMounted(load)
</script>
