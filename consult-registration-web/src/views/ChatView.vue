<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { NDrawer, NDrawerContent } from 'naive-ui'
import AppSider from '../components/AppSider.vue'
import CardPanel from '../components/CardPanel.vue'
import PayPanel from '../components/PayPanel.vue'
import SafetyBanner from '../components/SafetyBanner.vue'
import SlotAsk from '../components/SlotAsk.vue'
import UserInfoCard from '../components/UserInfoCard.vue'
import ZhiAvatar from '../components/ZhiAvatar.vue'
import { HUMAN_SHORTCUTS } from '../domain/humanAgent'
import { DESCRIPTION_EXAMPLES, hydrateDepartmentTree, MISSING_SLOT, STATUS } from '../constants/catalog'
import { listPatientDepartments } from '../api/departments'
import { useConsultationStore } from '../stores/consultation'
import { useAuthStore } from '../stores/auth'

const store = useConsultationStore()
const auth = useAuthStore()
const router = useRouter()
const draft = ref('')
const attachHint = ref('')
const feed = ref(null)
const composer = ref(null)
const showRecords = ref(false)
const drawerWidth = ref(360)

function syncDrawer() {
  drawerWidth.value = window.innerWidth < 640 ? Math.round(window.innerWidth * 0.86) : 360
}

onMounted(async () => {
  syncDrawer()
  window.addEventListener('resize', syncDrawer)
  store.startConsultClock()
  store.loadRecords()
  try {
    const result = await listPatientDepartments()
    if (result?.code === 200) {
      hydrateDepartmentTree(result.data)
    }
  } catch {
    // 接口失败时沿用本地种子，确认时服务端仍会拒已停科
  }
})

onUnmounted(() => {
  window.removeEventListener('resize', syncDrawer)
  store.stopConsultClock()
})

const matching = computed(() => store.matchPhase === 'matching')

const pageTitle = computed(() => {
  if (store.consultEnded) {
    return '问诊已结束'
  }
  if (store.doctorOnline && store.doctor) {
    return `${store.doctor.name}医生 | 图文问诊`
  }
  if (store.matchPhase === 'timeout') {
    return '匹配超时'
  }
  if (matching.value) {
    return '正在接入问诊'
  }
  if (store.humanOnline) {
    return '人工客服 | 挂号咨询'
  }
  return '智能导诊 | 挂号咨询助手'
})

const placeholder = computed(() => {
  if (matching.value) {
    return '正在为您匹配医生'
  }
  if (store.doctorOnline) {
    return '向医生补充病情或提问'
  }
  if (store.status === STATUS.URGENT) {
    return '急症已锁定，如需补充请继续输入'
  }
  if (store.status === STATUS.NEED_HUMAN) {
    return '正在与人工客服对话，可直接输入'
  }
  if (store.missingSlot === MISSING_SLOT.GENDER || store.missingSlot === MISSING_SLOT.AGE) {
    return '请选择就诊人或新建患者'
  }
  if (store.missingSlot === MISSING_SLOT.DURATION) {
    return '也可直接输入，例如「4天」「半个月」'
  }
  if (store.status === STATUS.NEED_DESCRIPTION && store.messages.some((item) => item.role === 'user')) {
    return '请详细描述病情'
  }
  return '可以打字描述不舒服'
})

const hideComposer = computed(() => (
  store.consultEnded
  || matching.value
  || (
    // 出卡后到诊室前藏输入，改信息只走卡片；诊室/人工再开
    !store.humanOnline
    && !store.doctorOnline
    && (store.status === STATUS.CARD_READY || store.status === STATUS.CONFIRMED)
    && !store.stopped
  )
))

const lockComposer = computed(() => (
  hideComposer.value
  || store.missingSlot === MISSING_SLOT.GENDER
  || store.missingSlot === MISSING_SLOT.AGE
  || matching.value
  || store.paying
))

const canSend = computed(() => !lockComposer.value && !!draft.value.trim() && !store.sending)

const showHumanShortcuts = computed(() => store.humanOnline && !store.sending && !store.doctorOnline)

const showDescriptionExamples = computed(() => (
  store.status === STATUS.NEED_DESCRIPTION
  && !store.sending
  && !store.inClinic
  && store.messages.some((item) => item.role === 'user')
))

const consultStart = computed(() => store.messages.findIndex((item) => item.role === 'system' || item.role === 'doctor'))
const triageMessages = computed(() => {
  const start = consultStart.value
  return start < 0 ? store.messages : store.messages.slice(0, start)
})
const consultMessages = computed(() => {
  const start = consultStart.value
  return start < 0 ? [] : store.messages.slice(start)
})

const lastUserIndex = computed(() => {
  let last = -1
  triageMessages.value.forEach((item, index) => {
    if (item.role === 'user') {
      last = index
    }
  })
  return last
})

const typingText = computed(() => {
  if (store.doctorOnline) {
    return '医生正在输入'
  }
  if (store.humanOnline) {
    return '客服正在输入'
  }
  return '正在帮您分析'
})

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
  () => [
    store.messages.length,
    store.showCard,
    store.missingSlot,
    store.status,
    store.payOrder,
    store.sending,
    store.matchPhase,
    store.doctorOnline,
  ],
  async () => {
    await nextTick()
    requestAnimationFrame(() => {
      if (!feed.value) {
        return
      }
      const cardRow = feed.value.querySelector('.after-card-row')
      if (cardRow && !store.doctorOnline) {
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

function editLastUser() {
  const item = triageMessages.value[lastUserIndex.value]
  if (!item || store.sending) {
    return
  }
  draft.value = item.text
  nextTick(() => composer.value?.focus())
}

function goHome() {
  store.startNew()
  showRecords.value = false
}

function logout() {
  auth.logout()
  store.startNew()
  router.replace('/login')
}

function onAttach() {
  attachHint.value = '检查单和照片上传即将开放，目前请先用文字描述症状。'
  window.setTimeout(() => {
    attachHint.value = ''
  }, 2600)
}

function initialOf(name) {
  return name ? name.slice(0, 1) : '医'
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
      <h1>{{ pageTitle }}</h1>
      <div class="top-right">
        <button class="nav-link" type="button" @click="showRecords = true">咨询记录</button>
        <button class="nav-link" type="button" @click="logout">退出</button>
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

    <SafetyBanner :status="store.status" :prompt="store.prompt" />

    <div ref="feed" class="feed">
      <UserInfoCard v-if="store.inClinic || store.consultEnded" />
      <section v-if="store.doctorOnline && store.doctor" class="hero doctor">
        <div class="avatar md hero-face">{{ initialOf(store.doctor.name) }}</div>
        <div class="hero-copy">
          <h2>{{ store.doctor.name }}医生为您服务</h2>
          <p>{{ store.doctor.hospital }} · {{ store.doctor.dept }} · {{ store.doctor.title }}</p>
          <small>在线咨询不能替代面诊，信息严格保密</small>
        </div>
      </section>
      <section v-else-if="!store.inClinic" class="hero" :class="{ human: store.humanOnline }">
        <div v-if="store.humanOnline" class="avatar cs hero-face">慧</div>
        <ZhiAvatar v-else :size="88" />
        <div class="hero-copy">
          <h2>{{ store.humanOnline ? '小慧为您服务' : '小智为您服务' }}</h2>
          <p>{{ store.humanOnline ? '人工客服已接入，协助整理挂号信息' : '描述症状，我帮您推荐科室、完成挂号分诊' }}</p>
          <small>本服务仅作分诊参考，不能替代医师诊断，信息严格保密</small>
        </div>
      </section>

      <div v-if="!store.messages.length" class="bubble-row bot">
        <ZhiAvatar :size="40" />
        <div class="bubble bot">您好，我是小智，请问您有什么症状？</div>
      </div>

      <template v-for="(item, index) in triageMessages" :key="`t-${index}`">
        <div class="bubble-row" :class="item.role">
          <div v-if="item.role === 'user'" class="avatar me">我</div>
          <div v-else-if="item.role === 'human'" class="avatar cs">慧</div>
          <ZhiAvatar v-else :size="40" />
          <div class="bubble-col">
            <div class="bubble" :class="item.role === 'user' ? 'me' : 'bot'">
              {{ item.text }}
            </div>
            <button
              v-if="item.role === 'user' && index === lastUserIndex && store.status === STATUS.NEED_DESCRIPTION"
              class="bubble-edit"
              type="button"
              @click="editLastUser"
            >
              点击修改
            </button>
          </div>
        </div>
      </template>

      <div
        v-if="store.missingSlot === MISSING_SLOT.GENDER
          || store.missingSlot === MISSING_SLOT.AGE
          || store.missingSlot === MISSING_SLOT.DURATION"
        class="bubble-row bot"
      >
        <ZhiAvatar :size="40" />
        <SlotAsk />
      </div>

      <div v-if="store.showCard" class="bubble-row bot after-card-row">
        <ZhiAvatar v-if="!store.inClinic" :size="40" />
        <div class="after-card">
          <CardPanel />
          <PayPanel />
        </div>
      </div>

      <template v-for="(item, index) in consultMessages" :key="`c-${index}`">
        <article v-if="item.kind === 'match'" class="match-inline">
          <div v-if="item.phase === 'matching'" class="match-spin" />
          <div v-else-if="item.phase === 'served' && store.doctor" class="avatar md match-face">{{ initialOf(store.doctor.name) }}</div>
          <h2>{{ item.phase === 'timeout' ? '匹配超时' : item.phase === 'served' ? `${store.doctor?.name || ''}医生为您服务` : '正在为您匹配医生' }}</h2>
          <p>{{ item.text }}</p>
          <div v-if="item.phase === 'timeout'" class="match-actions">
            <button class="match-retry" type="button" @click="store.retryMatch()">重试匹配</button>
            <button class="match-human" type="button" @click="store.handoffAfterMatch()">转人工</button>
          </div>
        </article>
        <div v-else-if="item.role === 'system'" class="sys-line">{{ item.text }}</div>
        <div
          v-else
          class="bubble-row"
          :class="item.role"
        >
          <div v-if="item.role === 'user'" class="avatar me">我</div>
          <div v-else-if="item.role === 'human'" class="avatar cs">慧</div>
          <div v-else-if="item.role === 'doctor'" class="avatar md">{{ initialOf(store.doctor?.name) }}</div>
          <ZhiAvatar v-else :size="40" />
          <div class="bubble" :class="item.role === 'user' ? 'me' : 'bot'">
            {{ item.text }}
          </div>
        </div>
      </template>

      <div v-if="store.sending" class="bubble-row bot">
        <div v-if="store.doctorOnline" class="avatar md">{{ initialOf(store.doctor?.name) }}</div>
        <div v-else-if="store.humanOnline" class="avatar cs">慧</div>
        <ZhiAvatar v-else :size="40" />
        <div class="bubble bot typing">
          <span>{{ typingText }}</span>
          <i /><i /><i />
        </div>
      </div>

      <div v-if="showDescriptionExamples" class="example-wrap">
        <span class="example-label">示例</span>
        <button
          v-for="item in DESCRIPTION_EXAMPLES"
          :key="item"
          class="example-chip"
          type="button"
          @click="useShortcut(item)"
        >
          {{ item }}
        </button>
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
    </div>

    <footer v-if="store.consultEnded" class="dock dock-ended">
      <p class="session-ended">问诊已结束，本会话不可再发送消息</p>
    </footer>
    <footer v-else-if="!hideComposer" class="dock">
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
