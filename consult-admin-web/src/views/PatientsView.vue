<script setup>
import { onMounted, ref } from 'vue'
import { NDataTable, useMessage } from 'naive-ui'
import { listPatients } from '../api/admin'
import { tablePagination } from '../table'

const message = useMessage()
const rows = ref([])
const loading = ref(false)
const pagination = tablePagination()
const columns = [
  { title: '就诊人', key: 'name', width: 120 },
  { title: '性别', key: 'gender', width: 80, render: (row) => (row.gender === 1 ? '男' : '女') },
  { title: '年龄', key: 'age', width: 80 },
  { title: '关系', key: 'relation', width: 100 },
  { title: '默认', key: 'isDefault', width: 80, render: (row) => (row.isDefault ? '是' : '') },
  { title: '账号手机', key: 'mobile', width: 140 },
  { title: '账号编号', key: 'accountId', ellipsis: true },
]

onMounted(async () => {
  loading.value = true
  try {
    const result = await listPatients()
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
    <h2>就诊人</h2>
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
