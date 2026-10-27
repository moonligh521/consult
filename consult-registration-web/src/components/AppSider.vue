<script setup>
import { NButton } from 'naive-ui'
import { useConsultationStore } from '../stores/consultation'

const emit = defineEmits(['close'])
const store = useConsultationStore()

function createChat() {
  store.startNew()
  emit('close')
}

function open(id) {
  store.openSession(id)
  emit('close')
}
</script>

<template>
  <div class="record-panel">
    <n-button class="sider-new" type="primary" round @click="createChat">
      + 新的咨询
    </n-button>
    <div class="session-list">
      <p v-if="!store.sessions.length" class="record-empty">还没有咨询记录</p>
      <button
        v-for="item in store.sessions"
        :key="item.id"
        class="session-item"
        :class="{ active: item.id === store.activeId }"
        type="button"
        @click="open(item.id)"
      >
        <span class="session-title">{{ item.title }}</span>
      </button>
    </div>
    <div class="sider-foot">本页只做智能挂号分流，不能代替医师诊查。紧急情况请拨打 120。</div>
  </div>
</template>
