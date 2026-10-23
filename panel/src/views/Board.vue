<template>
  <n-space vertical>
    <n-space align="center">
      <n-h2 style="margin:0">任务看板</n-h2>
      <n-text depth="3">Conductor 执行列表 · 人审任务可在此批准</n-text>
      <n-button size="small" @click="load">刷新</n-button>
      <n-button size="small" type="primary" @click="triggerDialog = true">触发新流程</n-button>
    </n-space>

    <n-data-table :columns="cols" :data="rows" :loading="loading" :row-key="r => r.workflowId" size="small" />

    <n-drawer v-model:show="detailShow" :width="640" placement="right">
      <n-drawer-content :title="`执行详情 · ${current?.workflowId?.slice(0, 8) ?? ''}`" closable>
        <n-space vertical v-if="detail">
          <n-descriptions bordered :column="1" size="small" label-placement="left">
            <n-descriptions-item label="状态">
              <n-tag :type="statusType(detail.status)" size="small">{{ detail.status }}</n-tag>
            </n-descriptions-item>
            <n-descriptions-item label="输入">{{ detail.input?.question }}</n-descriptions-item>
            <n-descriptions-item label="输出">{{ JSON.stringify(detail.output) }}</n-descriptions-item>
          </n-descriptions>
          <n-table size="small" :bordered="true">
            <thead><tr><th>步骤</th><th>类型</th><th>状态</th><th>操作</th></tr></thead>
            <tbody>
              <tr v-for="t in detail.tasks" :key="t.taskId">
                <td>{{ t.referenceTaskName }}</td>
                <td>{{ t.taskType }}</td>
                <td>
                  <n-tag size="tiny" :type="statusType(t.status)">{{ t.status }}</n-tag>
                </td>
                <td>
                  <n-button v-if="t.taskType === 'HUMAN' && t.status === 'IN_PROGRESS'"
                            size="tiny" type="success" @click="openApprove(t)">批准 / 驳回</n-button>
                </td>
              </tr>
            </tbody>
          </n-table>
          <n-card size="small" title="草稿（llm_answer 输出）" v-if="draft">
            <n-scrollbar style="max-height:240px">{{ draft }}</n-scrollbar>
          </n-card>
        </n-space>
      </n-drawer-content>
    </n-drawer>

    <n-modal v-model:show="approveShow" preset="dialog" title="人工审核">
      <n-space vertical>
        <n-text>决定：</n-text>
        <n-radio-group v-model:value="decision">
          <n-radio value="APPROVED">批准（落库+建工单）</n-radio>
          <n-radio value="REJECTED">驳回（不落库）</n-radio>
        </n-radio-group>
        <n-input v-model:value="comment" type="textarea" placeholder="审批意见" />
      </n-space>
      <template #action>
        <n-button @click="approveShow = false">取消</n-button>
        <n-button type="primary" :loading="approving" @click="submitApprove">提交</n-button>
      </template>
    </n-modal>

    <n-modal v-model:show="triggerDialog" preset="dialog" title="触发 draft_review 流程">
      <n-input v-model:value="newQuestion" type="textarea" placeholder="例如：请按报销制度为摄影社活动起草报销申请：包车费320元、门票200元。" />
      <template #action>
        <n-button @click="triggerDialog = false">取消</n-button>
        <n-button type="primary" :loading="triggering" @click="trigger">触发</n-button>
      </template>
    </n-modal>
  </n-space>
</template>

<script setup>
import { ref, computed, onMounted, h } from 'vue'
import { NButton, NTag, useMessage } from 'naive-ui'
import { adminFetch } from '../adminToken'

const message = useMessage()
const rows = ref([])
const loading = ref(false)
const detailShow = ref(false)
const detail = ref(null)
const current = ref(null)
const approveShow = ref(false)
const decision = ref('APPROVED')
const comment = ref('')
const approving = ref(false)
const triggerDialog = ref(false)
const triggering = ref(false)
const newQuestion = ref('')

const statusType = s => ({ COMPLETED: 'success', RUNNING: 'info', IN_PROGRESS: 'warning', FAILED: 'error', TIMED_OUT: 'error', TERMINATED: 'warning' }[s] || 'default')
const draft = computed(() => detail.value?.tasks?.find(t => t.referenceTaskName === 'llm_ref')?.outputData?.answer)

const cols = [
  { title: '流程', key: 'workflowType', width: 130 },
  { title: '执行ID', key: 'workflowId', width: 130, render: r => r.workflowId.slice(0, 8) + '…' },
  { title: '状态', key: 'status', width: 110, render: r => h(NTag, { type: statusType(r.status), size: 'small' }, { default: () => r.status }) },
  { title: '开始时间', key: 'startTime', width: 170, render: r => (r.startTime || '').replace('T', ' ').slice(0, 19) },
  { title: '耗时(s)', key: 'executionTime', width: 90, render: r => r.executionTime ? (r.executionTime / 1000).toFixed(1) : '-' },
  { title: '操作', key: 'actions', render: r => h(NButton, { size: 'tiny', onClick: () => openDetail(r) }, { default: () => '详情' }) }
]

async function load() {
  loading.value = true
  try {
    const r = await adminFetch('/conductor/api/workflow/search?size=30')
    const d = await r.json()
    rows.value = d.results || []
  } catch (e) {
    message.error(String(e.message || e))
  } finally { loading.value = false }
}

async function openDetail(row) {
  current.value = row
  try {
    const r = await adminFetch('/conductor/api/workflow/' + row.workflowId)
    detail.value = await r.json()
    detailShow.value = true
  } catch (e) {
    message.error(String(e.message || e))
  }
}

let approveTask = null
function openApprove(t) { approveTask = t; decision.value = 'APPROVED'; comment.value = ''; approveShow.value = true }

async function submitApprove() {
  approving.value = true
  try {
    const r = await adminFetch('/conductor/api/tasks', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        taskId: approveTask.taskId,
        workflowInstanceId: detail.value.workflowId,
        status: 'COMPLETED',
        outputData: { decision: decision.value, comment: comment.value || '面板审批' }
      })
    })
    if (r.ok) { message.success('已提交审批'); approveShow.value = false; detailShow.value = false; load() }
    else message.error('提交失败 ' + r.status)
  } finally { approving.value = false }
}

async function trigger() {
  if (!newQuestion.value.trim()) { message.warning('请输入问题'); return }
  triggering.value = true
  try {
    // 不带 version 参数：Conductor 默认使用该工作流的最新版本（此前硬编码 version=2）
    const r = await adminFetch('/conductor/api/workflow/draft_review', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ question: newQuestion.value })
    })
    if (r.ok) { message.success('已触发：' + (await r.text()).slice(0, 8)); triggerDialog.value = false; setTimeout(load, 3000) }
    else message.error('触发失败 ' + r.status)
  } finally { triggering.value = false }
}

onMounted(load)
</script>
