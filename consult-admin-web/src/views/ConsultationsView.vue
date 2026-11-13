<script setup>
import { h, onMounted, ref } from 'vue'
import { NButton, NDataTable, NDescriptions, NDescriptionsItem, NDrawer, NDrawerContent, useMessage } from 'naive-ui'
import { consultationAgentTrace, listConsultations } from '../api/admin'
import { tablePagination } from '../table'

const STATUS = {
  1: '待描述',
  2: '待补槽',
  3: '急症分流',
  4: '卡片待确认',
  5: '已确认',
  6: '转人工',
  7: '安全分流',
  8: '不适用',
}

const TRACE_REASON = {
  NOT_CONFIGURED: '未配置 Langfuse 地址或项目，无法打开链路。',
  NO_DECISION: '这轮咨询还没有分诊决策，暂无链路。',
  EXPIRED: '链路已超过保留期，不能再打开。',
}

const message = useMessage()
const rows = ref([])
const loading = ref(false)
const pagination = tablePagination()
const current = ref(null)
const showDetail = ref(false)
const opening = ref(false)
const columns = [
  { title: '状态', key: 'status', width: 110, render: (row) => STATUS[row.status] || row.status },
  { title: '就诊人', key: 'patientName', width: 100 },
  { title: '性别', key: 'gender', width: 70, render: (row) => (row.gender === 1 ? '男' : row.gender === 2 ? '女' : '') },
  { title: '年龄', key: 'age', width: 70 },
  { title: '病程', key: 'duration', width: 90 },
  { title: '主诉', key: 'description', ellipsis: { tooltip: true } },
  { title: '更新时间', key: 'updatedAt', width: 180 },
  {
    title: '操作',
    key: 'actions',
    width: 90,
    render: (row) => h(NButton, { size: 'small', onClick: () => openDetail(row) }, { default: () => '详情' }),
  },
]

function openDetail(row) {
  current.value = row
  showDetail.value = true
}

async function openAgentTrace() {
  if (!current.value?.id) {
    return
  }
  opening.value = true
  try {
    const result = await consultationAgentTrace(current.value.id)
    if (result.code !== 200) {
      throw new Error(result.message)
    }
    const data = result.data || {}
    if (!data.available) {
      message.warning(TRACE_REASON[data.reason] || data.reason || '暂时无法查看 Agent 链路')
      return
    }
    window.open(data.url, '_blank', 'noopener')
  } catch (error) {
    message.error(error.message || '没有权限查看 Agent 链路')
  } finally {
    opening.value = false
  }
}

onMounted(async () => {
  loading.value = true
  try {
    const result = await listConsultations()
    if (result.code !== 200) {
      throw new Error(result.message)
    }
    rows.value = result.data || []
  } catch (error) {
    message.error(error.message)
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div class="page-pad">
    <h2>咨询会话</h2>
    <n-data-table
      :columns="columns"
      :data="rows"
      :loading="loading"
      :bordered="false"
      :pagination="pagination"
      :paginate-single-page="true"
    />
    <n-drawer v-model:show="showDetail" :width="480">
      <n-drawer-content title="会话详情">
        <n-descriptions v-if="current" label-placement="left" :column="1">
          <n-descriptions-item label="编号">{{ current.id }}</n-descriptions-item>
          <n-descriptions-item label="状态">{{ STATUS[current.status] || current.status }}</n-descriptions-item>
          <n-descriptions-item label="就诊人">{{ current.patientName }}</n-descriptions-item>
          <n-descriptions-item label="年龄">{{ current.age }}</n-descriptions-item>
          <n-descriptions-item label="主诉">{{ current.description }}</n-descriptions-item>
          <n-descriptions-item label="更新时间">{{ current.updatedAt }}</n-descriptions-item>
        </n-descriptions>
        <n-button class="trace-btn" type="primary" :loading="opening" @click="openAgentTrace">
          查看 Agent 链路
        </n-button>
      </n-drawer-content>
    </n-drawer>
  </div>
</template>

<style scoped>
.trace-btn {
  margin-top: 16px;
}
</style>
