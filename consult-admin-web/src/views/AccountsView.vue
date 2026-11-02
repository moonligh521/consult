<script setup>
import { onMounted, ref } from 'vue'
import { NDataTable, useMessage } from 'naive-ui'
import { listAccounts } from '../api/admin'
import { tablePagination } from '../table'

const message = useMessage()
const rows = ref([])
const loading = ref(false)
const pagination = tablePagination()
const columns = [
  { title: '账号编号', key: 'id', ellipsis: true },
  { title: '手机号', key: 'mobile', width: 140 },
  { title: '状态', key: 'status', width: 80, render: (row) => (row.status === 1 ? '正常' : '停用') },
  { title: '就诊人数', key: 'personCount', width: 100 },
  { title: '创建时间', key: 'createdAt', width: 180 },
]

onMounted(async () => {
  loading.value = true
  try {
    const result = await listAccounts()
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
    <h2>患者账号</h2>
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
