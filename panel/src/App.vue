<template>
  <n-config-provider :theme-overrides="{ common: { primaryColor: '#18a058' } }">
    <n-layout style="height: 100vh">
      <n-layout-header bordered style="height:52px;display:flex;align-items:center;padding:0 24px;gap:12px">
        <b style="font-size:17px">GateFlow</b>
        <n-tag size="small" type="success" :bordered="false">管理面板</n-tag>
        <n-text depth="3" style="font-size:12px">AI 助手工具网关 · 任务看板 / 审计流水 / 成本统计</n-text>
      </n-layout-header>
      <n-layout has-sider style="height: calc(100vh - 52px)">
        <n-layout-sider bordered :width="160" content-style="padding:12px">
          <n-menu :options="menuOptions" :value="active" @update:value="go" />
        </n-layout-sider>
        <n-layout-content content-style="padding:20px" :native-scrollbar="false">
          <router-view />
        </n-layout-content>
      </n-layout>
    </n-layout>
  </n-config-provider>
</template>

<script setup>
import { computed, h } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { NMenu, NIcon } from 'naive-ui'

const route = useRoute()
const router = useRouter()
const active = computed(() => route.path)

const label = t => () => t
const menuOptions = [
  { label: label('任务看板'), key: '/board' },
  { label: label('审计流水'), key: '/audit' },
  { label: label('成本统计'), key: '/cost' }
]
const go = k => router.push(k)
</script>
