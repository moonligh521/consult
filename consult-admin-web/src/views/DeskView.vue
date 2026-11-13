<script setup>
import { onMounted, onUnmounted, ref } from 'vue'
import { NButton, NCard, NInput, useMessage } from 'naive-ui'
import { doctorHeartbeat, doctorInbox, doctorOffline, doctorOnline, doctorPresence, doctorReply } from '../api/admin'
import { useAuthStore } from '../stores/auth'

const message = useMessage()
const auth = useAuthStore()
const rows = ref([])
const loading = ref(false)
const activeId = ref('')
const draft = ref('')
const sending = ref(false)
const online = ref(false)
let timer = 0

onMounted(async () => {
  await auth.loadMe()
  if (!auth.doctorId) {
    return
  }
  await refreshPresence()
  await load()
  const beat = () => {
    if (document.hidden || !auth.doctorId || !online.value) {
      return
    }
    doctorHeartbeat().catch(() => {})
  }
  beat()
  timer = window.setInterval(beat, 10000)
})

onUnmounted(() => {
  if (timer) {
    window.clearInterval(timer)
  }
})

async function refreshPresence() {
  const result = await doctorPresence()
  online.value = result.code === 200 && result.data === true
}

async function setOnline(next) {
  const result = next ? await doctorOnline() : await doctorOffline()
  if (result.code === 200) {
    online.value = next
    message.success(next ? '已上线，可以接新单' : '已下线，不再接新单')
    if (next) {
      doctorHeartbeat().catch(() => {})
    }
    return
  }
  message.error(result.message || '切换失败')
}

async function load() {
  if (!auth.doctorId) {
    return
  }
  loading.value = true
  try {
    const result = await doctorInbox(auth.doctorId)
    rows.value = result.code === 200 ? result.data || [] : []
    if (result.code !== 200) {
      message.error(result.message || '待接箱加载失败')
    }
  } finally {
    loading.value = false
  }
}

function openRow(row) {
  activeId.value = row.consultationId
  draft.value = ''
}

async function send() {
  if (!activeId.value || !auth.doctorId || !draft.value.trim()) {
    return
  }
  sending.value = true
  try {
    const result = await doctorReply(activeId.value, auth.doctorId, draft.value.trim())
    if (result.code === 200) {
      message.success('已回复患者')
      draft.value = ''
    } else {
      message.error(result.message || '回复失败')
    }
  } finally {
    sending.value = false
  }
}
</script>

<template>
  <div style="padding: 20px; display: grid; gap: 16px; max-width: 880px">
    <n-card v-if="!auth.doctorId" size="small">尚未绑定医生，不能回复。</n-card>
    <template v-else>
      <div style="display: flex; gap: 12px; align-items: center">
        <span>当前医生 {{ auth.doctorId }}</span>
        <span>{{ online ? '在线' : '下线' }}</span>
        <n-button type="primary" :disabled="online" @click="setOnline(true)">上线</n-button>
        <n-button :disabled="!online" @click="setOnline(false)">下线</n-button>
        <n-button :loading="loading" @click="load">刷新待接箱</n-button>
      </div>
      <n-card v-if="!rows.length" size="small">这个医生还没有待接咨询。</n-card>
      <n-card
        v-for="row in rows"
        :key="row.orderId"
        size="small"
        :title="row.specialty || '待接咨询'"
        hoverable
        @click="openRow(row)"
      >
        <div>咨询 {{ row.consultationId }}</div>
        <div>订单 {{ row.orderId }}</div>
      </n-card>
      <n-card v-if="activeId" size="small" :title="`回复 ${activeId}`">
        <n-input v-model:value="draft" type="textarea" placeholder="写给患者的回复" :autosize="{ minRows: 3, maxRows: 6 }" />
        <n-button style="margin-top: 12px" type="primary" :loading="sending" @click="send">发送</n-button>
      </n-card>
    </template>
  </div>
</template>
