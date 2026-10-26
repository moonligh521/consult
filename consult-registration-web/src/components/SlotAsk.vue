<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { NButton, NInput, NSelect } from 'naive-ui'
import {
  AGE_OPTIONS,
  DEFAULT_AGE,
  GENDER_OPTIONS,
  MISSING_SLOT,
  RELATION_OPTIONS,
  genderLabel,
  maskPatientName,
} from '../constants/catalog'
import { useConsultationStore } from '../stores/consultation'

const store = useConsultationStore()
const creating = ref(false)
const formError = ref('')
const form = reactive({
  name: '',
  gender: 1,
  age: DEFAULT_AGE,
  relation: '本人',
})

const askingPatient = computed(() => (
  store.missingSlot === MISSING_SLOT.GENDER || store.missingSlot === MISSING_SLOT.AGE
))

watch(
  askingPatient,
  (on) => {
    if (on) {
      creating.value = false
      formError.value = ''
      store.loadPatients()
    }
  },
  { immediate: true },
)

function pickPatient(patient) {
  if (store.sending) {
    return
  }
  store.bindPatient(patient.id)
}

function startCreate() {
  creating.value = true
  formError.value = ''
}

async function saveCreate() {
  const name = form.name.trim()
  if (!name) {
    formError.value = '请填写就诊人姓名'
    return
  }
  if (store.sending) {
    return
  }
  try {
    const created = await store.createPatient({
      name,
      gender: form.gender,
      age: form.age,
      relation: form.relation,
    })
    creating.value = false
    form.name = ''
    await store.bindPatient(created.id)
  } catch (error) {
    formError.value = error.message
  }
}
</script>

<template>
  <div v-if="askingPatient" class="slot-ask">
    <div class="slot-label">选择患者</div>
    <div v-if="store.patients.length" class="patient-list">
      <button
        v-for="item in store.patients"
        :key="item.id"
        class="patient-card"
        type="button"
        :disabled="store.sending"
        @click="pickPatient(item)"
      >
        <strong>{{ maskPatientName(item.name) }}</strong>
        <span>{{ item.relation }} · {{ genderLabel(item.gender) }} {{ item.age }}岁</span>
      </button>
    </div>
    <p v-else class="slot-hint">这个账号还没有就诊人，请先新建一位。</p>

    <button
      v-if="!creating"
      class="patient-add"
      type="button"
      :disabled="store.sending"
      @click="startCreate"
    >
      新建患者
    </button>

    <div v-else class="patient-form">
      <div class="field">
        <label>姓名</label>
        <n-input v-model:value="form.name" maxlength="16" placeholder="请输入真实姓名" />
      </div>
      <div class="field">
        <label>性别</label>
        <div class="slot-chips">
          <button
            v-for="item in GENDER_OPTIONS"
            :key="item.value"
            class="slot-chip"
            type="button"
            :class="{ on: form.gender === item.value }"
            @click="form.gender = item.value"
          >
            {{ item.label }}
          </button>
        </div>
      </div>
      <div class="field">
        <label>年龄</label>
        <n-select v-model:value="form.age" :options="AGE_OPTIONS" filterable placeholder="选择年龄" />
      </div>
      <div class="field">
        <label>关系</label>
        <n-select v-model:value="form.relation" :options="RELATION_OPTIONS" />
      </div>
      <p v-if="formError" class="patient-form-error">{{ formError }}</p>
      <div class="card-actions">
        <n-button :disabled="store.sending" @click="creating = false">取消</n-button>
        <n-button type="primary" :loading="store.sending" @click="saveCreate">保存并咨询</n-button>
      </div>
    </div>

    <p class="slot-hint">为保护您的隐私，您的姓名已脱敏</p>
  </div>
</template>
