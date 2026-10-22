<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { NButton, NCascader, NInput, NSelect } from 'naive-ui'
import {
  AGE_OPTIONS,
  DEFAULT_AGE,
  GENDER_OPTIONS,
  SPECIALTY_CASCADE,
  STATUS,
  consultDepartmentLabel,
  genderLabel,
  parentDepartmentCode,
  resolveConsultSpecialty,
} from '../constants/catalog'
import { useConsultationStore } from '../stores/consultation'

const store = useConsultationStore()
const editing = ref(false)
const openFold = ref(false)

const form = reactive({
  gender: null,
  age: null,
  duration: '',
  description: '',
  department: null,
  specialty: null,
})

watch(
  () => store.order,
  (order) => {
    if (order) {
      openFold.value = false
    }
  },
)

function fillForm(card) {
  const specialty = resolveConsultSpecialty(card)
  form.gender = card?.gender ?? null
  form.age = card?.age ?? DEFAULT_AGE
  form.duration = card?.duration ?? ''
  form.description = card?.description ?? ''
  form.department = parentDepartmentCode(specialty) || card?.department || null
  form.specialty = specialty
}

watch(
  () => store.card,
  (card) => {
    fillForm(card)
    if (store.status === STATUS.CONFIRMED) {
      editing.value = false
    }
  },
  { immediate: true },
)

const readonly = computed(() => store.status === STATUS.CONFIRMED)
const folded = computed(() => !!store.order && !editing.value && !openFold.value)
const personText = computed(() => {
  const gender = genderLabel(store.card?.gender)
  const age = store.card?.age
  const duration = store.card?.duration
  const parts = ['匿名']
  if (gender !== '—') {
    parts.push(gender)
  }
  if (age) {
    parts.push(`${age}岁`)
  }
  if (duration) {
    parts.push(duration)
  }
  return parts.join(' ')
})

function startEdit() {
  if (readonly.value) {
    return
  }
  fillForm(store.card)
  editing.value = true
}

async function save() {
  const specialty = resolveConsultSpecialty({
    specialty: form.specialty,
    department: form.department,
  })
  const department = parentDepartmentCode(specialty)
  if (!department) {
    return
  }
  const ok = await store.saveCard({
    gender: form.gender,
    age: form.age,
    duration: form.duration,
    description: form.description,
    department,
    specialty,
  })
  if (ok) {
    editing.value = false
  }
}
</script>

<template>
  <button
    v-if="store.showCard && folded"
    class="im-fold"
    type="button"
    @click="openFold = true"
  >
    <div>
      <strong>咨询信息</strong>
      <p>{{ personText }} · {{ consultDepartmentLabel(store.card) }}</p>
    </div>
    <span>展开查看</span>
  </button>

  <article v-else-if="store.showCard" class="consult-card">
    <header class="consult-head">
      <div>
        <div class="consult-title">咨询信息</div>
        <div class="consult-sub">{{ readonly ? '已确认，后续以卡片信息为准' : '可修改后选择下方档位咨询' }}</div>
      </div>
      <button v-if="store.order" class="consult-edit" type="button" @click="openFold = false">收起</button>
      <button v-else-if="!readonly" class="consult-edit" type="button" @click="startEdit">修改</button>
      <span v-else class="consult-done">已确认</span>
    </header>

    <div v-if="!editing" class="consult-body">
      <p><span class="k">咨询人：</span>{{ personText }}</p>
      <p><span class="k">咨询科室：</span>{{ consultDepartmentLabel(store.card) }}</p>
      <p v-if="store.card?.departmentEvidence"><span class="k">分诊依据：</span>{{ store.card.departmentEvidence }}</p>
      <p><span class="k">问题描述：</span>{{ store.card?.description }}</p>
      <p v-if="store.card?.allergy"><span class="k">过敏：</span>{{ store.card.allergy }}</p>
      <p v-if="store.card?.medications"><span class="k">用药：</span>{{ store.card.medications }}</p>
    </div>

    <div v-else class="consult-form">
      <div class="field">
        <label>性别</label>
        <n-select v-model:value="form.gender" :options="GENDER_OPTIONS" />
      </div>
      <div class="field">
        <label>年龄</label>
        <n-select v-model:value="form.age" :options="AGE_OPTIONS" filterable placeholder="选择年龄" />
      </div>
      <div class="field">
        <label>病程</label>
        <n-input v-model:value="form.duration" />
      </div>
      <div class="field">
        <label>咨询科室</label>
        <n-cascader
          v-model:value="form.specialty"
          :options="SPECIALTY_CASCADE"
          filterable
          show-path
          leaf-only
          placeholder="选择二级科室"
        />
      </div>
      <div class="field">
        <label>问题描述</label>
        <n-input v-model:value="form.description" type="textarea" :autosize="{ minRows: 3, maxRows: 6 }" />
      </div>
      <div class="card-actions">
        <n-button :loading="store.sending" @click="editing = false">取消</n-button>
        <n-button type="primary" :loading="store.sending" @click="save">保存</n-button>
      </div>
    </div>

    <footer class="consult-foot">平台保障您的咨询隐私安全</footer>
  </article>
</template>
