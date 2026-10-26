<script setup>
import { onMounted, ref } from 'vue'
import { NDataTable, useMessage } from 'naive-ui'
import { listConsultations } from '../api/admin'
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

const message = useMessage()
const rows = ref([])
const loading = ref(false)
const pagination = tablePagination()
const columns = [
  { title: '状态', key: 'status', width: 110, render: (row) => STATUS[row.status] || row.status },
  { title: '就诊人', key: 'patientName', width: 100 },
  { title: '性别', key: 'gender', width: 70, render: (row) => (row.gender === 1 ? '男' : row.gender === 2 ? '女' : '') },
  { title: '年龄', key: 'age', width: 70 },
  { title: '病程', key: 'duration', width: 90 },
  { title: '主诉', key: 'description', ellipsis: { tooltip: true } },
  { title: '更新时间', key: 'updatedAt', width: 180 },
]

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
  </div>
</template>
