<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { NDrawer, NDrawerContent } from 'naive-ui'
import AppSider from '../components/AppSider.vue'
import CardPanel from '../components/CardPanel.vue'
import PayPanel from '../components/PayPanel.vue'
import SafetyBanner from '../components/SafetyBanner.vue'
import SlotAsk from '../components/SlotAsk.vue'
import ZhiAvatar from '../components/ZhiAvatar.vue'
import { HUMAN_SHORTCUTS } from '../domain/humanAgent'
import { MISSING_SLOT, STATUS } from '../constants/catalog'
import { useConsultationStore } from '../stores/consultation'

const store = useConsultationStore()
const draft = ref('')
const attachHint = ref('')
const feed = ref(null)
const composer = ref(null)
const showRecords = ref(false)
const drawerWidth = ref(360)

function syncDrawer() {
  drawerWidth.value = window.innerWidth < 640 ? Math.round(window.innerWidth * 0.86) : 360
}

onMounted(() => {
  syncDrawer()
  window.addEventListener('resize', syncDrawer)
})

onUnmounted(() => {
  window.removeEventListener('resize', syncDrawer)
})

const placeholder = computed(() => {
  if (store.status === STATUS.URGENT) {
    return '急症已锁定，如需补充请继续输入'
  }
  if (store.status === STATUS.CARD_READY) {
    return '请先确认分诊建议，或补充症状'
  }
  if (store.status === STATUS.NEED_HUMAN) {
    return '正在与人工客服对话，可直接输入'
  }
  if (store.status === STATUS.CONFIRMED) {
    return '如病情有变化，请继续描述'
  }
  if (store.missingSlot === MISSING_SLOT.GENDER) {
    return '请点选上方性别，不要手打'
  }
  if (store.missingSlot === MISSING_SLOT.AGE) {
    return '请点选上方年龄，不要手打'
  }
  if (store.missingSlot === MISSING_SLOT.DURATION) {
    return '请补充病程，例如「半年」「3天」'
  }
  return '可以打字描述不舒服'
})

const lockComposer = computed(() => (
  store.missingSlot === MISSING_SLOT.GENDER || store.missingSlot === MISSING_SLOT.AGE
))

const canSend = computed(() => !lockComposer.value && !!draft.value.trim() && !store.sending)

const showHumanShortcuts = computed(() => store.humanOnline && !store.sending)

watch(draft, async () => {
  await nextTick()
  const el = composer.value
  if (!el) {
    return
  }
  el.style.height = 'auto'
  el.style.height = `${Math.min(el.scrollHeight, 120)}px`
})

watch(
  () => [store.messages.length, store.showCard, store.missingSlot, store.status, store.payOrder, store.sending],
  async () => {
    await nextTick()
    requestAnimationFrame(() => {
      if (!feed.value) {
        return
      }
      const cardRow = feed.value.querySelector('.after-card-row')
      if (cardRow) {
        cardRow.scrollIntoView({ block: 'nearest', behavior: 'smooth' })
        return
      }
      feed.value.scrollTop = feed.value.scrollHeight
    })
  },
)

function submit() {
  if (!canSend.value) {
    return
  }
  const text = draft.value
  draft.value = ''
  store.send(text)
}

function onKeydown(event) {
  if (event.key === 'Enter' && !event.shiftKey) {
    event.preventDefault()
    submit()
  }
}

function useShortcut(text) {
  if (store.sending || lockComposer.value) {
    return
  }
  store.send(text)
}

function goHome() {
  store.startNew()
  showRecords.value = false
}

function onAttach() {
  attachHint.value = '检查单和照片上传即将开放，目前请先用文字描述症状。'
  window.setTimeout(() => {
    attachHint.value = ''
  }, 2600)
}

function focusComposer() {
  nextTick(() => composer.value?.focus())
}

function askHuman() {
  if (store.sending) {
    return
  }
  store.send('转人工')
}
</script>

<template>
  <div class="page">
    <header class="top-bar">
      <button class="icon-btn" type="button" aria-label="返回，开始新的咨询" @click="goHome">
        <svg viewBox="0 0 24 24" width="22" height="22" aria-hidden="true">
          <path fill="currentColor" d="M15.4 5.4 9.8 12l5.6 6.6-1.5 1.3L6.8 12l7.1-8z" />
        </svg>
      </button>
      <h1>智能导诊 | 挂号咨询助手</h1>
      <div class="top-right">
        <button class="nav-link" type="button" @click="showRecords = true">咨询记录</button>
        <button class="user-btn" type="button" aria-label="咨询记录" @click="showRecords = true">
          <svg viewBox="0 0 24 24" width="18" height="18" aria-hidden="true">
            <path
              fill="currentColor"
              d="M12 12.2a4.1 4.1 0 1 0-4.1-4.1A4.1 4.1 0 0 0 12 12.2zm0 1.8c-3.4 0-8 1.7-8 5v1.3h16V19c0-3.3-4.6-5-8-5z"
            />
          </svg>
        </button>
      </div>
    </header>

    <section class="hero" :class="{ human: store.humanOnline }">
      <div v-if="store.humanOnline" class="avatar cs hero-face">慧</div>
      <ZhiAvatar v-else :size="88" />
      <div class="hero-copy">
        <h2>{{ store.humanOnline ? '小慧为您服务' : '小智为您服务' }}</h2>
        <p>{{ store.humanOnline ? '人工客服已接入，协助整理挂号信息' : '描述症状，我帮您推荐科室、完成挂号分诊' }}</p>
        <small>本服务仅作分诊参考，不能替代医师诊断，信息严格保密</small>
      </div>
    </section>

    <SafetyBanner :status="store.status" :prompt="store.prompt" />

    <div ref="feed" class="feed">
      <div v-if="!store.messages.length" class="bubble-row bot">
        <ZhiAvatar :size="40" />
        <div class="bubble bot">您好，我是小智，请问您有什么症状？</div>
      </div>

      <div
        v-for="(item, index) in store.messages"
        :key="index"
        class="bubble-row"
        :class="item.role"
      >
        <div v-if="item.role === 'user'" class="avatar me">我</div>
        <div v-else-if="item.role === 'human'" class="avatar cs">慧</div>
        <ZhiAvatar v-else :size="40" />
        <div class="bubble" :class="item.role === 'user' ? 'me' : 'bot'">
          {{ item.text }}
        </div>
      </div>

      <div v-if="store.sending" class="bubble-row bot">
        <div v-if="store.humanOnline" class="avatar cs">慧</div>
        <ZhiAvatar v-else :size="40" />
        <div class="bubble bot typing">
          <span>{{ store.humanOnline ? '客服正在输入' : '正在帮您分析' }}</span>
          <i /><i /><i />
        </div>
      </div>

      <div v-if="showHumanShortcuts" class="shortcut-wrap">
        <button
          v-for="item in HUMAN_SHORTCUTS"
          :key="item"
          class="shortcut"
          type="button"
          @click="useShortcut(item)"
        >
          {{ item }}
        </button>
      </div>

      <div v-if="store.missingSlot" class="bubble-row bot">
        <ZhiAvatar :size="40" />
        <SlotAsk />
      </div>

      <div v-if="store.showCard" class="bubble-row bot after-card-row">
        <ZhiAvatar :size="40" />
        <div class="after-card">
          <CardPanel @supplement="focusComposer" @human="askHuman" />
          <PayPanel />
        </div>
      </div>
    </div>

    <footer class="dock">
      <p v-if="attachHint" class="attach-hint">{{ attachHint }}</p>
      <div class="composer">
        <button class="attach" type="button" aria-label="上传检查单或照片" @click="onAttach">+</button>
        <textarea
          ref="composer"
          v-model="draft"
          :placeholder="placeholder"
          rows="1"
          :disabled="lockComposer"
          @keydown="onKeydown"
        />
        <button class="send" type="button" :disabled="!canSend" @click="submit">发送</button>
      </div>
    </footer>

    <n-drawer v-model:show="showRecords" :width="drawerWidth" placement="right">
      <n-drawer-content title="咨询记录" closable>
        <AppSider @close="showRecords = false" />
      </n-drawer-content>
    </n-drawer>
  </div>
</template>
