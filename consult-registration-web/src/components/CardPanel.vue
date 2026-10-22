<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { NButton, NInput, NSelect } from 'naive-ui'
import {
  AGE_OPTIONS,
  DEFAULT_AGE,
  DEPARTMENT_OPTIONS,
  GENDER_OPTIONS,
  STATUS,
  departmentLabel,
  genderLabel,
} from '../constants/catalog'
import { useConsultationStore } from '../stores/consultation'

const emit = defineEmits(['supplement', 'human'])
const store = useConsultationStore()
const editing = ref(false)

const form = reactive({
  gender: null,
  age: null,
  duration: '',
  description: '',
  department: null,
})

watch(
  () => store.card,
  (card) => {
    form.gender = card?.gender ?? null
    form.age = card?.age ?? DEFAULT_AGE
    form.duration = card?.duration ?? ''
    form.description = card?.description ?? ''
    form.department = card?.department ?? null
    if (store.status === STATUS.CONFIRMED) {
      editing.value = false
    }
  },
  { immediate: true },
)

const readonly = computed(() => store.status === STATUS.CONFIRMED)
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
const hint = computed(() => {
  if (store.safetyNotice) {
    return store.safetyNotice
  }
  const dept = departmentLabel(store.card?.department)
  return `建议挂${dept}，具体诊治请以医师面诊为准。`
})

function startEdit() {
  if (readonly.value) {
    return
  }
  editing.value = true
}

async function save() {
  const ok = await store.saveCard({
    gender: form.gender,
    age: form.age,
    duration: form.duration,
    description: form.description,
    department: form.department,
  })
  if (ok) {
    editing.value = false
  }
}

async function confirm() {
  await store.confirm()
  editing.value = false
}
</script>

<template>
  <article v-if="store.showCard" class="consult-card">
    <header class="consult-head">
      <div>
        <div class="consult-title">🩺 分诊建议</div>
        <div class="consult-sub">{{ readonly ? '已确认，可继续选择咨询档位' : '请核对后去挂号，或以医师面诊为准' }}</div>
      </div>
      <button v-if="!readonly" class="consult-edit" type="button" @click="startEdit">修改</button>
      <span v-else class="consult-done">已确认</span>
    </header>

    <div v-if="!editing" class="consult-body">
      <p>✅ 推荐科室：{{ departmentLabel(store.card?.department) }}</p>
      <p><span class="k">咨询人：</span>{{ personText }}</p>
      <p>⚠️ 温馨提示：{{ hint }}</p>
      <p>
        <span class="k">问题描述：</span>{{ store.card?.description }}
      </p>
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
        <n-select v-model:value="form.department" :options="DEPARTMENT_OPTIONS" />
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

    <div v-if="!editing" class="triage-actions">
      <button
        v-if="!readonly"
        class="triage-btn primary"
        type="button"
        :disabled="store.sending"
        @click="confirm"
      >
        去挂号
      </button>
      <button class="triage-btn" type="button" @click="emit('supplement')">补充症状</button>
      <button class="triage-btn" type="button" :disabled="store.sending" @click="emit('human')">转人工</button>
    </div>
  </article>
</template>
