<script setup>
import { computed } from 'vue'
import { NSelect } from 'naive-ui'
import {
  AGE_OPTIONS,
  GENDER_OPTIONS,
  MISSING_SLOT,
  genderLabel,
  patientsOfAccount,
} from '../constants/catalog'
import { useConsultationStore } from '../stores/consultation'

const store = useConsultationStore()
const patients = computed(() => patientsOfAccount())

function pickGender(label) {
  store.send(label)
}

function pickAge(value) {
  if (!value || store.sending) {
    return
  }
  store.send(`${value}岁`)
}

/** 选档案就诊人：一句带入姓名、性别、年龄，空槽才写入。 */
function pickPatient(patient) {
  if (store.sending) {
    return
  }
  const gender = genderLabel(patient.gender)
  store.send(`${patient.name}，${gender}，${patient.age}岁`)
}
</script>

<template>
  <div
    v-if="store.missingSlot === MISSING_SLOT.GENDER || store.missingSlot === MISSING_SLOT.AGE"
    class="slot-ask"
  >
    <template v-if="patients.length">
      <div class="slot-label">选择就诊人</div>
      <div class="patient-list">
        <button
          v-for="item in patients"
          :key="item.id"
          class="patient-card"
          type="button"
          :disabled="store.sending"
          @click="pickPatient(item)"
        >
          <strong>{{ item.name }}</strong>
          <span>{{ item.relation }} · {{ genderLabel(item.gender) }} {{ item.age }}岁</span>
        </button>
      </div>
      <div class="slot-divider">不是以上就诊人，直接选性别、年龄</div>
    </template>

    <template v-if="store.missingSlot === MISSING_SLOT.GENDER">
      <div class="slot-label">请选择患者性别</div>
      <div class="slot-chips">
        <button
          v-for="item in GENDER_OPTIONS"
          :key="item.value"
          class="slot-chip"
          type="button"
          :disabled="store.sending"
          @click="pickGender(item.label)"
        >
          {{ item.label }}
        </button>
      </div>
    </template>

    <template v-else>
      <div class="slot-label">请选择患者年龄</div>
      <div class="slot-age">
        <n-select
          :options="AGE_OPTIONS"
          filterable
          placeholder="选择年龄"
          :disabled="store.sending"
          @update:value="pickAge"
        />
      </div>
    </template>
  </div>
</template>
