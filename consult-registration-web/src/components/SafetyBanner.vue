<script setup>
import { computed } from 'vue'
import { STATUS, STATUS_LABEL, isSafetyStop } from '../constants/catalog'

const props = defineProps({
  status: { type: Number, default: null },
  prompt: { type: String, default: '' },
})

const kind = computed(() => {
  if (props.status === STATUS.URGENT) {
    return 'urgent'
  }
  if (props.status === STATUS.NEED_HUMAN) {
    return 'human'
  }
  if (props.status === STATUS.UNSUITABLE_FOR_ONLINE) {
    return 'unsuitable'
  }
  if (props.status === STATUS.SAFE_FALLBACK || (props.status != null && isSafetyStop(props.status))) {
    return 'fallback'
  }
  return ''
})

const label = computed(() => STATUS_LABEL[props.status] || '未知状态')
const text = computed(() => {
  if (props.status === STATUS.NEED_HUMAN) {
    return '演示坐席小慧在线，仅协助挂号指引，不能替代医师诊查。'
  }
  return props.prompt
})
</script>

<template>
  <div v-if="kind" class="banner" :class="kind">
    <span class="label">{{ label }}</span>
    {{ text }}
  </div>
</template>
