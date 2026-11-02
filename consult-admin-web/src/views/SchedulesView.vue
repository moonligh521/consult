<script setup>
import { computed, h, onMounted, reactive, ref } from 'vue'
import { NButton, NDataTable, NDatePicker, NForm, NFormItem, NInputNumber, NModal, NSelect, useMessage } from 'naive-ui'
import { createSchedule, deleteSchedule, listDoctors, listSchedules, stopSchedule } from '../api/admin'
import { tablePagination } from '../table'

const WEEK = ['', '一', '二', '三', '四', '五', '六', '日']
const PERIODS = [
  { label: '上午 08:00-12:00', value: '上午' },
  { label: '下午 14:00-17:30', value: '下午' },
  { label: '晚上 18:00-21:00', value: '晚上' },
]

const message = useMessage()
const doctors = ref([])
const rows = ref([])
const loading = ref(false)
const pagination = tablePagination()
const show = ref(false)
const saving = ref(false)
const filterDoctor = ref(null)
const form = reactive({
  doctorId: null,
  visitDate: null,
  period: '上午',
  visitType: '图文',
  limitNum: 20,
})

const doctorOptions = computed(() => doctors.value.map((item) => ({
  label: `${item.name} · ${item.specialty}`,
  value: item.id,
})))

const columns = [
  { title: '日期', key: 'visitDate', width: 120 },
  { title: '星期', key: 'weekday', width: 70, render: (row) => `周${WEEK[row.weekday] || ''}` },
  { title: '医生', key: 'doctorName', width: 100 },
  { title: '二级科', key: 'specialty', width: 120 },
  { title: '时段', key: 'period', width: 80 },
  { title: '时间', key: 'time', width: 130, render: (row) => `${row.startTime}-${row.endTime}` },
  { title: '类型', key: 'visitType', width: 80 },
  { title: '剩余/限号', key: 'remainNum', width: 110, render: (row) => `${row.remainNum}/${row.limitNum}` },
  { title: '状态', key: 'stopped', width: 90, render: (row) => (row.stopped ? '停诊' : '可约') },
  {
    title: '操作',
    key: 'actions',
    width: 160,
    render: (row) => h('div', { style: 'display:flex;gap:8px' }, [
      h(
        NButton,
        { size: 'small', onClick: () => toggle(row) },
        { default: () => (row.stopped ? '恢复' : '停诊') },
      ),
      h(NButton, { size: 'small', onClick: () => remove(row) }, { default: () => '删除' }),
    ]),
  },
]

async function reload() {
  loading.value = true
  try {
    const result = await listSchedules(filterDoctor.value ? { doctorId: filterDoctor.value } : {})
    if (result.code !== 200) {
      throw new Error(result.message)
    }
    rows.value = result.data || []
  } catch (error) {
    message.error(error.message)
  } finally {
    loading.value = false
  }
}

function openCreate() {
  form.doctorId = filterDoctor.value || doctors.value[0]?.id || null
  form.visitDate = Date.now()
  form.period = '上午'
  form.visitType = '图文'
  form.limitNum = 20
  show.value = true
}

function dateText(value) {
  const date = new Date(value)
  const m = `${date.getMonth() + 1}`.padStart(2, '0')
  const d = `${date.getDate()}`.padStart(2, '0')
  return `${date.getFullYear()}-${m}-${d}`
}

async function save() {
  if (!form.doctorId || !form.visitDate) {
    message.error('请选择医生和日期')
    return
  }
  saving.value = true
  try {
    const result = await createSchedule({
      doctorId: form.doctorId,
      visitDate: dateText(form.visitDate),
      period: form.period,
      visitType: form.visitType,
      limitNum: form.limitNum,
    })
    if (result.code !== 200) {
      throw new Error(result.message)
    }
    show.value = false
    await reload()
  } catch (error) {
    message.error(error.message)
  } finally {
    saving.value = false
  }
}

async function toggle(row) {
  const result = await stopSchedule(row.id, !row.stopped)
  if (result.code !== 200) {
    message.error(result.message)
    return
  }
  await reload()
}

async function remove(row) {
  const result = await deleteSchedule(row.id)
  if (result.code !== 200) {
    message.error(result.message)
    return
  }
  await reload()
}

onMounted(async () => {
  const result = await listDoctors()
  if (result.code === 200) {
    doctors.value = result.data || []
  }
  await reload()
})
</script>

<template>
  <div class="page-pad">
    <div class="page-head">
      <h2>排班管理</h2>
      <n-button type="primary" @click="openCreate">新增排班</n-button>
    </div>
    <div class="toolbar">
      <n-select
        v-model:value="filterDoctor"
        :options="doctorOptions"
        clearable
        placeholder="全部医生"
        style="width: 240px"
        @update:value="() => { pagination.page = 1; reload() }"
      />
    </div>
    <n-data-table
      :columns="columns"
      :data="rows"
      :loading="loading"
      :bordered="false"
      :scroll-x="1200"
      :pagination="pagination"
      :paginate-single-page="true"
    />
    <n-modal v-model:show="show" preset="card" title="新增排班" style="width: 440px">
      <n-form label-placement="left" label-width="80">
        <n-form-item label="医生">
          <n-select v-model:value="form.doctorId" :options="doctorOptions" />
        </n-form-item>
        <n-form-item label="日期">
          <n-date-picker v-model:value="form.visitDate" type="date" />
        </n-form-item>
        <n-form-item label="时段">
          <n-select v-model:value="form.period" :options="PERIODS" />
        </n-form-item>
        <n-form-item label="限号">
          <n-input-number v-model:value="form.limitNum" :min="1" :max="99" />
        </n-form-item>
      </n-form>
      <template #footer>
        <n-button @click="show = false">取消</n-button>
        <n-button type="primary" :loading="saving" @click="save">保存</n-button>
      </template>
    </n-modal>
  </div>
</template>
