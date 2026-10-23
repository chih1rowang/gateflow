<template>
  <n-space vertical>
    <n-space align="center">
      <n-h2 style="margin:0">审计流水</n-h2>
      <n-text depth="3">网关 /mcp 全量调用记录（含被拒绝的请求）</n-text>
      <n-input v-model:value="client" size="small" placeholder="按客户端过滤" style="width:160px" clearable @keyup.enter="load(1)" />
      <n-button size="small" @click="load(1)">查询</n-button>
    </n-space>

    <n-data-table :columns="cols" :data="rows" :loading="loading" :pagination="pagination" size="small" remote />
  </n-space>
</template>

<script setup>
import { ref, reactive, onMounted, h } from 'vue'
import { NTag, useMessage } from 'naive-ui'
import { adminFetch } from '../adminToken'

const message = useMessage()
const rows = ref([])
const loading = ref(false)
const client = ref('')
const pagination = reactive({
  page: 1, pageSize: 20, itemCount: 0,
  onChange: p => { pagination.page = p; load() },
  onPageSizeChange: ps => { pagination.pageSize = ps; load(1) }
})

const cols = [
  { title: 'ID', key: 'id', width: 70 },
  { title: '客户端', key: 'client_name', width: 110 },
  { title: '状态码', key: 'http_status', width: 80,
    render: r => h(NTag, { type: r.http_status >= 400 ? 'error' : 'success', size: 'small' }, { default: () => r.http_status }) },
  { title: '方法', key: 'rpc_method', width: 110, render: r => r.rpc_method || '-' },
  { title: '工具', key: 'tool_name', width: 110, render: r => r.tool_name || '-' },
  { title: '拒绝原因', key: 'rejected_reason', width: 100,
    render: r => r.rejected_reason === 'none' ? '-' : h(NTag, { type: 'warning', size: 'small' }, { default: () => r.rejected_reason }) },
  { title: '耗时(ms)', key: 'latency_ms', width: 90 },
  { title: '时间', key: 'created_at', width: 180, render: r => (r.created_at || '').replace('T', ' ').slice(0, 19) }
]

async function load(page) {
  if (page) pagination.page = page
  loading.value = true
  try {
    const q = new URLSearchParams({ page: pagination.page, size: pagination.pageSize })
    if (client.value) q.set('client', client.value)
    const r = await adminFetch('/admin/audit?' + q)
    const d = await r.json()
    rows.value = d.rows || []
    pagination.itemCount = d.total || 0
  } catch (e) {
    message.error(String(e.message || e))
  } finally { loading.value = false }
}

onMounted(() => load(1))
</script>
