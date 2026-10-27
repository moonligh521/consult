<script setup>
import { computed, h, onMounted, reactive, ref } from 'vue'
import { NButton, NDataTable, NForm, NFormItem, NInput, NModal, NSelect, useMessage } from 'naive-ui'
import { createDoctor, listDepartments, listDoctors, updateDoctor } from '../api/admin'
import { tablePagination } from '../table'

const TITLES = ['住院医师', '主治医师', '副主任医师', '主任医师'].map((value) => ({ label: value, value }))
const GENDERS = [
  { label: '男', value: 1 },
  { label: '女', value: 2 },
]

const message = useMessage()
const rows = ref([])
const catalog = ref([])
const loading = ref(false)
const pagination = tablePagination()
const show = ref(false)
const editingId = ref('')
const saving = ref(false)
const form = reactive({
  name: '',
  gender: 1,
  title: '主治医师',
  hospital: '互联网医院',
  departmentCode: 1,
  specialty: '',
  skills: '',
  intro: '',
  status: 1,
})

const deptOptions = computed(() => catalog.value.map((item) => ({ label: item.name, value: item.code })))
const specialtyOptions = computed(() => {
  const dept = catalog.value.find((item) => item.code === form.departmentCode)
  return (dept?.specialties || []).map((name) => ({ label: name, value: name }))
})

const columns = [
  { title: '姓名', key: 'name', width: 80 },
  { title: '一级科室', key: 'deptName', width: 90 },
  { title: '二级科室', key: 'specialty', width: 100 },
  { title: '擅长', key: 'skills', ellipsis: { tooltip: true }, minWidth: 140 },
  { title: '职称', key: 'title', width: 90 },
  { title: '简介', key: 'intro', ellipsis: { tooltip: true } },
  { title: '状态', key: 'status', width: 70, render: (row) => (row.status === 1 ? '出诊' : '停诊') },
  {
    title: '操作',
    key: 'actions',
    width: 120,
    render: (row) => h('div', { style: 'display:flex;gap:8px' }, [
      h(NButton, { size: 'small', onClick: () => openEdit(row) }, { default: () => '编辑' }),
      h(
        NButton,
        { size: 'small', onClick: () => toggle(row) },
        { default: () => (row.status === 1 ? '停诊' : '恢复') },
      ),
    ]),
  },
]

async function reload() {
  loading.value = true
  try {
    const result = await listDoctors()
    if (result.code !== 200) {
      throw new Error(result.message)
    }
    rows.value = (result.data || []).map((item) => ({
      ...item,
      deptName: catalog.value.find((dept) => dept.code === item.departmentCode)?.name || '',
    }))
  } catch (error) {
    message.error(error.message)
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editingId.value = ''
  Object.assign(form, {
    name: '',
    gender: 1,
    title: '主治医师',
    hospital: '互联网医院',
    departmentCode: catalog.value[0]?.code || 1,
    specialty: catalog.value[0]?.specialties?.[0] || '',
    skills: '',
    intro: '',
    status: 1,
  })
  show.value = true
}

function openEdit(row) {
  editingId.value = row.id
  Object.assign(form, {
    name: row.name,
    gender: row.gender,
    title: row.title,
    hospital: row.hospital,
    departmentCode: row.departmentCode,
    specialty: row.specialty,
    skills: row.skills || '',
    intro: row.intro || '',
    status: row.status,
  })
  show.value = true
}

async function toggle(row) {
  const result = await updateDoctor(row.id, { ...row, status: row.status === 1 ? 0 : 1 })
  if (result.code !== 200) {
    message.error(result.message)
    return
  }
  await reload()
}

async function save() {
  saving.value = true
  try {
    const payload = { ...form }
    const result = editingId.value
      ? await updateDoctor(editingId.value, payload)
      : await createDoctor(payload)
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

onMounted(async () => {
  const depts = await listDepartments()
  if (depts.code === 200) {
    catalog.value = depts.data || []
  }
  await reload()
})
</script>

<template>
  <div class="page-pad">
    <div class="page-head">
      <h2>医生管理</h2>
      <n-button type="primary" @click="openCreate">新增医生</n-button>
    </div>
    <n-data-table
      :columns="columns"
      :data="rows"
      :loading="loading"
      :bordered="false"
      :pagination="pagination"
      :paginate-single-page="true"
    />
    <n-modal v-model:show="show" preset="card" :title="editingId ? '编辑医生' : '新增医生'" style="width: 480px">
      <n-form label-placement="left" label-width="80">
        <n-form-item label="姓名">
          <n-input v-model:value="form.name" maxlength="16" />
        </n-form-item>
        <n-form-item label="性别">
          <n-select v-model:value="form.gender" :options="GENDERS" />
        </n-form-item>
        <n-form-item label="职称">
          <n-select v-model:value="form.title" :options="TITLES" />
        </n-form-item>
        <n-form-item label="医院">
          <n-input v-model:value="form.hospital" />
        </n-form-item>
        <n-form-item label="一级科室">
          <n-select
            v-model:value="form.departmentCode"
            :options="deptOptions"
            @update:value="form.specialty = specialtyOptions[0]?.value || ''"
          />
        </n-form-item>
        <n-form-item label="二级科室">
          <n-select v-model:value="form.specialty" :options="specialtyOptions" />
        </n-form-item>
        <n-form-item label="擅长">
          <n-input v-model:value="form.skills" type="textarea" placeholder="顿号分隔，如 高血压、常见内科随访" />
        </n-form-item>
        <n-form-item label="简介">
          <n-input v-model:value="form.intro" type="textarea" />
        </n-form-item>
      </n-form>
      <template #footer>
        <n-button @click="show = false">取消</n-button>
        <n-button type="primary" :loading="saving" @click="save">保存</n-button>
      </template>
    </n-modal>
  </div>
</template>
