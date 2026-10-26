<script setup>
import { onMounted, ref } from 'vue'
import { NDataTable, useMessage } from 'naive-ui'
import { listDepartments } from '../api/admin'
import { tablePagination } from '../table'

const message = useMessage()
const rows = ref([])
const loading = ref(false)
const pagination = tablePagination()
const columns = [
  { title: '一级码', key: 'deptCode', width: 90 },
  { title: '一级类', key: 'deptName', width: 180 },
  { title: '二级数', key: 'specialtyCount', width: 90 },
  { title: '二级科', key: 'specialtyText', ellipsis: { tooltip: true } },
]

onMounted(async () => {
  loading.value = true
  try {
    const result = await listDepartments()
    if (result.code !== 200) {
      throw new Error(result.message)
    }
    rows.value = (result.data || []).map((item) => ({
      deptCode: item.code,
      deptName: item.name,
      specialtyCount: item.specialtyCount,
      specialtyText: (item.specialties || []).join('、'),
    }))
  } catch (error) {
    message.error(error.message)
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div class="page-pad">
    <div class="page-head">
      <h2>科室目录</h2>
      <span class="hint">共 {{ rows.length }} 个一级类，挂二级科</span>
    </div>
    <n-data-table
      :columns="columns"
      :data="rows"
      :loading="loading"
      :bordered="false"
      :scroll-x="960"
      :pagination="pagination"
      :paginate-single-page="true"
    />
  </div>
</template>
