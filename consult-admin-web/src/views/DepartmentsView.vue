<script setup>
import { h, onMounted, ref } from 'vue'
import { NButton, NDataTable, NSelect, useMessage } from 'naive-ui'
import { departmentImpact, listDepartments, patchDepartment, patchSpecialty } from '../api/admin'
import { tablePagination } from '../table'

const message = useMessage()
const audienceOptions = [
  { label: '不限', value: 0 },
  { label: '儿童', value: 1 },
  { label: '成人', value: 2 },
]
const rows = ref([])
const loading = ref(false)
const pagination = tablePagination()
const columns = [
  { title: '一级码', key: 'deptCode', width: 80 },
  { title: '一级类', key: 'deptName', width: 140 },
  {
    title: '一级',
    key: 'deptEnabled',
    width: 80,
    render: (row) => (row.deptEnabled ? '开通' : '停用'),
  },
  { title: '二级科', key: 'specName', width: 140 },
  {
    title: '二级',
    key: 'specEnabled',
    width: 80,
    render: (row) => (row.specEnabled ? '开通' : '停用'),
  },
  {
    title: '适用人群',
    key: 'audience',
    width: 120,
    render: (row) => (row.specId
      ? h(NSelect, {
        size: 'small',
        value: row.audience,
        options: audienceOptions,
        onUpdateValue: (value) => changeAudience(row, value),
      })
      : '—'),
  },
  {
    title: '操作',
    key: 'actions',
    width: 160,
    render: (row) => h('div', { style: 'display:flex;gap:8px' }, [
      h(NButton, { size: 'small', onClick: () => toggleDept(row) }, { default: () => (row.deptEnabled ? '停一级' : '开一级') }),
      h(NButton, { size: 'small', onClick: () => toggleSpec(row) }, { default: () => (row.specEnabled ? '停二级' : '开二级') }),
    ]),
  },
]

function flatten(tree) {
  const out = []
  for (const dept of tree || []) {
    const specs = dept.specialties || []
    if (specs.length === 0) {
      out.push({
        key: `d-${dept.code}`,
        deptCode: dept.code,
        deptName: dept.name,
        deptEnabled: dept.enabled,
        fallback: dept.fallback,
        specId: null,
        specName: '—',
        specEnabled: false,
        audience: 2,
      })
      continue
    }
    for (const spec of specs) {
      const name = typeof spec === 'string' ? spec : spec.name
      out.push({
        key: `s-${dept.code}-${spec.id || name}`,
        deptCode: dept.code,
        deptName: dept.name,
        deptEnabled: dept.enabled,
        fallback: dept.fallback,
        specId: spec.id,
        specName: name,
        specEnabled: spec.enabled !== false,
        audience: typeof spec === 'string' ? 2 : Number(spec.audience ?? 2),
      })
    }
  }
  return out
}

async function reload() {
  loading.value = true
  try {
    const result = await listDepartments()
    if (result.code !== 200) {
      throw new Error(result.message)
    }
    rows.value = flatten(result.data)
  } catch (error) {
    message.error(error.message)
  } finally {
    loading.value = false
  }
}

async function toggleDept(row) {
  const enable = !row.deptEnabled
  if (!enable) {
    const impact = await departmentImpact(row.deptCode)
    if (impact.code === 200 && impact.data?.warning) {
      message.warning(impact.data.warning)
    }
  }
  const result = await patchDepartment(row.deptCode, { enabled: enable })
  if (result.code !== 200) {
    message.error(result.message)
    return
  }
  await reload()
}

async function changeAudience(row, audience) {
  if (!row.specId || audience === row.audience) {
    return
  }
  const result = await patchSpecialty(row.deptCode, row.specId, { audience })
  if (result.code !== 200) {
    message.error(result.message)
    return
  }
  await reload()
}

async function toggleSpec(row) {
  if (!row.specId) {
    return
  }
  const enable = !row.specEnabled
  if (!enable) {
    const impact = await departmentImpact(row.deptCode, row.specId)
    if (impact.code === 200 && impact.data) {
      message.info(`未确认卡 ${impact.data.cardReady}，未支付已确认 ${impact.data.confirmedUnpaid}，今日可约医生 ${impact.data.onDutyToday}`)
    }
  }
  const result = await patchSpecialty(row.deptCode, row.specId, { enabled: enable })
  if (result.code !== 200) {
    message.error(result.message)
    return
  }
  await reload()
}

onMounted(reload)
</script>

<template>
  <div class="page-pad">
    <div class="page-head">
      <h2>科室目录</h2>
      <span class="hint">共 {{ rows.length }} 条二级；停用不打断已支付问诊</span>
    </div>
    <n-data-table
      :columns="columns"
      :data="rows"
      :loading="loading"
      :bordered="false"
      :row-key="(row) => row.key"
      :scroll-x="960"
      :pagination="pagination"
      :paginate-single-page="true"
    />
  </div>
</template>
