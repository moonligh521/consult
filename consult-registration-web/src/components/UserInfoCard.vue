<script setup>
import { computed, ref } from 'vue'
import { CONSULT_MINUTES, consultDepartmentLabel, genderLabel, maskPatientName } from '../constants/catalog'
import { useConsultationStore } from '../stores/consultation'

const store = useConsultationStore()
const open = ref(false)

const person = computed(() => {
  const card = store.card
  const gender = genderLabel(card?.gender)
  const age = card?.age
  return [
    maskPatientName(card?.patientName),
    gender !== '—' ? gender : '',
    age ? `${age}岁` : '',
  ].filter(Boolean).join(' ')
})

const chief = computed(() => store.card?.description || '')
const shortChief = computed(() => {
  const text = chief.value.replace(/\s+/g, ' ')
  return text.length > 28 ? `${text.slice(0, 28)}…` : text
})

const remain = computed(() => {
  const total = Math.max(0, Math.ceil(store.remainingMs / 1000))
  const minutes = Math.floor(total / 60)
  const seconds = total % 60
  return `${minutes}:${String(seconds).padStart(2, '0')}`
})

function onMinutes(event) {
  store.changeConsultMinutes(Number(event.target.value))
}
</script>

<template>
  <article class="user-info">
    <header>
      <strong>用户信息</strong>
      <em v-if="store.order?.packageName">{{ store.order.packageName }}</em>
    </header>
    <p><span>姓名：</span>{{ person }}</p>
    <p><span>主诉：</span>{{ open ? chief : shortChief }}</p>
    <div v-if="store.order" class="user-info-session">
      <p v-if="store.consultEnded" class="user-info-ended">问诊已结束</p>
      <template v-else>
        <label class="user-info-minutes">
          <span>时长</span>
          <select :value="store.consultMinutes" :disabled="store.ending" @change="onMinutes">
            <option
              v-for="item in CONSULT_MINUTES.options"
              :key="item.value"
              :value="item.value"
            >
              {{ item.label }}
            </option>
          </select>
        </label>
        <p class="user-info-remain"><span>剩余：</span>{{ remain }}</p>
        <button
          class="user-info-end"
          type="button"
          :disabled="store.ending"
          @click="store.finishConsult('ENDED')"
        >
          {{ store.ending ? '正在结束…' : '结束问诊' }}
        </button>
      </template>
    </div>
    <button class="user-info-go" type="button" @click="open = !open">
      {{ open ? '收起' : '查看/补充' }}
    </button>
    <p v-if="open && consultDepartmentLabel(store.card) !== '—'" class="user-info-dept">
      分诊科室：{{ consultDepartmentLabel(store.card) }}
    </p>
    <footer>平台保障您的咨询隐私安全</footer>
  </article>
</template>
