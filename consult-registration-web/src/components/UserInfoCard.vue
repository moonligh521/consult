<script setup>
import { computed, ref } from 'vue'
import { consultDepartmentLabel, genderLabel } from '../constants/catalog'
import { useConsultationStore } from '../stores/consultation'

const store = useConsultationStore()
const open = ref(false)

const person = computed(() => {
  const card = store.card
  const named = [...store.messages].reverse().find((item) => (
    item.role === 'user' && /[男女].*\d+\s*岁/.test(item.text) && item.text.length < 24
  ))
  if (named) {
    return named.text.replace(/，/g, ' ')
  }
  const gender = genderLabel(card?.gender)
  const age = card?.age
  return [gender !== '—' ? gender : '', age ? `${age}岁` : ''].filter(Boolean).join(' ') || '就诊人'
})

const chief = computed(() => store.card?.description || '')
const shortChief = computed(() => {
  const text = chief.value.replace(/\s+/g, ' ')
  return text.length > 28 ? `${text.slice(0, 28)}…` : text
})
</script>

<template>
  <article class="user-info">
    <header>
      <strong>用户信息</strong>
      <em v-if="store.order?.packageName">{{ store.order.packageName }}</em>
    </header>
    <p><span>姓名：</span>{{ person }}</p>
    <p><span>主诉：</span>{{ open ? chief : shortChief }}</p>
    <button class="user-info-go" type="button" @click="open = !open">
      {{ open ? '收起' : '查看/补充' }}
    </button>
    <p v-if="open && consultDepartmentLabel(store.card) !== '—'" class="user-info-dept">
      分诊科室：{{ consultDepartmentLabel(store.card) }}
    </p>
    <footer>平台保障您的咨询隐私安全</footer>
  </article>
</template>
