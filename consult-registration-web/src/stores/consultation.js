import { defineStore } from 'pinia'
import { bindPatient, confirmCard, endConsult as endConsultApi, getConsultation, listConsultations, listPackages, patchCard, placeOrder, reassignDoctor, saveTranscript, sendMessage, setConsultMinutes, waitAssignment } from '../api/consultations'
import { createPatient, listPatients } from '../api/patients'
import { canShowCard, CONSULT_MINUTES, INQUIRY, isSafetyStop, STATUS } from '../constants/catalog'
import { doctorFromOrder, firstAskAsDoctor, greetAsDoctor, MATCH_ASSIST, replyAsDoctor, waitForDoctor, waitForDoctorAssign, waitForDoctorServe, waitMs } from '../domain/doctorAgent'
import { HUMAN_JOIN, replyAsHuman, waitForHuman } from '../domain/humanAgent'

function loadConsultMinutes() {
  const raw = Number(localStorage.getItem(CONSULT_MINUTES.storageKey))
  return CONSULT_MINUTES.options.some((item) => item.value === raw) ? raw : CONSULT_MINUTES.default
}

const STORAGE_KEY = 'consult-sessions-v1'

function loadSessions() {
  try {
    return JSON.parse(localStorage.getItem(STORAGE_KEY) || '[]')
  } catch {
    return []
  }
}

function persist(sessions) {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(sessions))
}

let persistTimer = 0

function newMessageId() {
  return crypto.randomUUID()
}

function titleOf(text) {
  const value = (text || '新的咨询').replace(/\s+/g, ' ').trim()
  return value.length > 18 ? `${value.slice(0, 18)}…` : value
}

function parseTranscript(detail) {
  if (!detail?.transcript) {
    return null
  }
  try {
    const payload = JSON.parse(detail.transcript)
    return {
      id: detail.view?.consultationId,
      snapshot: payload.snapshot || detail.view || null,
      payOrder: payload.payOrder || detail.view?.order || null,
      doctor: payload.doctor || null,
      doctorOnline: !!payload.doctorOnline,
      matchPhase: payload.matchPhase || null,
      humanOnline: !!payload.humanOnline,
      messages: payload.messages || [],
    }
  } catch {
    return null
  }
}

/**
 * 咨询会话。列表以服务端为准，浏览器里只做缓存。
 */
export const useConsultationStore = defineStore('consultation', {
  state: () => ({
    sessions: loadSessions(),
    activeId: null,
    messages: [],
    latest: null,
    payOrder: null,
    humanOnline: false,
    doctorOnline: false,
    doctor: null,
    sending: false,
    paying: false,
    assigningDoctor: false,
    matchPhase: null,
    error: '',
    patients: [],
    consultMinutes: loadConsultMinutes(),
    ending: false,
    consultTick: 0,
    consultTimer: null,
  }),
  getters: {
    status: (state) => state.latest?.status ?? null,
    card: (state) => state.latest?.card ?? null,
    prompt: (state) => state.latest?.prompt ?? '',
    safetyNotice: (state) => state.latest?.safetyNotice ?? null,
    showCard: (state) => canShowCard(state.latest?.status, state.latest?.card),
    missingSlot: (state) => (state.latest?.status === STATUS.NEED_SLOT ? state.latest?.missingSlot ?? null : null),
    packages: (state) => state.latest?.packages ?? [],
    order: (state) => state.latest?.order ?? state.payOrder,
    inConsult: (state) => state.doctorOnline || state.matchPhase === 'consulting',
    inClinic: (state) => {
      const order = state.latest?.order ?? state.payOrder
      if (order?.endedAt) {
        return false
      }
      return !!order || state.matchPhase != null
    },
    consultEnded: (state) => !!(state.latest?.order ?? state.payOrder)?.endedAt,
    remainingMs: (state) => {
      const order = state.latest?.order ?? state.payOrder
      if (!order?.endsAt || order.endedAt) {
        return 0
      }
      // consultTick 每秒变一次，好让剩余时间刷新。真正扫过期占坑的是 XXL。
      void state.consultTick
      return Math.max(0, new Date(order.endsAt).getTime() - Date.now())
    },
    stopped: (state) => state.latest != null && isSafetyStop(state.latest.status),
  },
  actions: {
    startNew() {
      this.stopConsultClock()
      this.activeId = null
      this.latest = null
      this.payOrder = null
      this.humanOnline = false
      this.doctorOnline = false
      this.doctor = null
      this.paying = false
      this.assigningDoctor = false
      this.matchPhase = null
      this.messages = []
      this.error = ''
      this.ending = false
    },
    hydrate(record) {
      this.activeId = record.id
      this.latest = record.snapshot || null
      this.payOrder = record.payOrder || null
      this.messages = record.messages || []
      this.humanOnline = !!record.humanOnline || this.status === STATUS.NEED_HUMAN
      this.doctor = record.doctor || null
      this.doctorOnline = !!record.doctorOnline
      this.matchPhase = record.matchPhase || (record.doctorOnline ? 'consulting' : null)
      this.assigningDoctor = false
      this.error = ''
    },
    async openSession(id) {
      const found = this.sessions.find((item) => item.id === id)
      if (found) {
        this.hydrate(found)
      }
      try {
        const result = await getConsultation(id)
        if (result.code === 200 && result.data) {
          const remote = parseTranscript(result.data)
          if (remote) {
            this.hydrate(remote)
          } else if (result.data.view) {
            this.activeId = result.data.view.consultationId
            this.latest = result.data.view
            this.payOrder = result.data.view.order || null
          }
        }
      } catch (error) {
        this.error = error.message
      }
      if (this.payOrder && !this.doctorOnline && !this.humanOnline && !this.consultEnded && this.matchPhase !== 'timeout') {
        this.joinDoctor()
      }
      this.startConsultClock()
    },
    async loadRecords() {
      try {
        const result = await listConsultations(1, 50)
        if (result.code !== 200 || !result.data?.items) {
          return
        }
        const cached = new Map(this.sessions.map((item) => [item.id, item]))
        this.sessions = result.data.items.map((item) => {
          const local = cached.get(item.consultationId)
          return {
            id: item.consultationId,
            title: item.title || local?.title || '新的咨询',
            updatedAt: item.updatedAt ? Date.parse(item.updatedAt) : (local?.updatedAt || Date.now()),
            snapshot: local?.snapshot || null,
            payOrder: local?.payOrder || null,
            doctor: local?.doctor || null,
            doctorOnline: !!local?.doctorOnline,
            matchPhase: local?.matchPhase || null,
            humanOnline: !!local?.humanOnline,
            messages: local?.messages || [],
          }
        })
        persist(this.sessions)
      } catch (error) {
        this.error = error.message
      }
    },
    remember(text) {
      if (!this.activeId) {
        return
      }
      const index = this.sessions.findIndex((item) => item.id === this.activeId)
      const record = {
        id: this.activeId,
        title: this.card?.description
          ? titleOf(this.card.description)
          : (this.sessions[index]?.title || titleOf(text)),
        updatedAt: Date.now(),
        snapshot: this.latest,
        payOrder: this.payOrder,
        doctor: this.doctor,
        doctorOnline: this.doctorOnline,
        matchPhase: this.doctorOnline ? 'consulting' : this.matchPhase,
        humanOnline: this.humanOnline,
        messages: this.messages,
      }
      if (index >= 0) {
        this.sessions.splice(index, 1, record)
      } else {
        this.sessions.unshift(record)
      }
      persist(this.sessions)
      this.queuePersist(record)
    },
    queuePersist(record) {
      if (!record?.id) {
        return
      }
      window.clearTimeout(persistTimer)
      persistTimer = window.setTimeout(() => {
        saveTranscript(record.id, record.title, JSON.stringify({
          messages: record.messages,
          snapshot: record.snapshot,
          payOrder: record.payOrder,
          doctor: record.doctor,
          doctorOnline: record.doctorOnline,
          matchPhase: record.matchPhase,
          humanOnline: record.humanOnline,
        })).catch(() => {})
      }, 600)
    },
    applyResult(data, { silent = false } = {}) {
      this.latest = data
      this.activeId = data.consultationId
      if (silent) {
        return
      }
      if (data.status === STATUS.NEED_HUMAN) {
        return
      }
      this.pushPrompt(data)
      if (data.safetyNotice) {
        this.messages.push({ role: 'assistant', text: data.safetyNotice, notice: true })
      }
    },
    pushPrompt(data) {
      if (!data?.prompt) {
        return
      }
      const duplicateUrgent = data.status === STATUS.URGENT
        && this.messages.some((item) => item.status === STATUS.URGENT)
      if (duplicateUrgent) {
        return
      }
      this.messages.push({ role: 'assistant', text: data.prompt, status: data.status })
    },
    async send(text) {
      const sentence = text.trim()
      if (!sentence || this.sending || this.consultEnded) {
        return
      }
      if (this.matchPhase === 'timeout' || this.matchPhase === 'matching') {
        return
      }
      if (this.doctorOnline || this.humanOnline) {
        await this.sendClinic(sentence)
        return
      }
      // 出卡后不能再对话补述；已确认且未进诊室也不再打分诊接口
      if (this.status === STATUS.CARD_READY || this.status === STATUS.CONFIRMED) {
        return
      }
      this.sending = true
      this.error = ''
      this.messages.push({ role: 'user', text: sentence })
      try {
        const result = await sendMessage({
          consultationId: this.activeId,
          messageId: newMessageId(),
          text: sentence,
        })
        if (result.code === 200) {
          const joined = this.humanOnline
          const stopped = isSafetyStop(result.data.status)
          this.applyResult(result.data, {
            silent: result.data.status === STATUS.NEED_HUMAN,
          })
          if (stopped) {
            this.doctorOnline = false
            this.assigningDoctor = false
            this.matchPhase = null
          } else if (result.data.status === STATUS.NEED_HUMAN) {
            this.doctorOnline = false
            this.matchPhase = null
            await waitForHuman()
            if (!joined) {
              this.humanOnline = true
              this.messages.push({ role: 'human', text: HUMAN_JOIN })
            } else {
              this.messages.push({ role: 'human', text: replyAsHuman(sentence, this.card) })
            }
          }
        } else {
          this.messages.push({ role: 'assistant', text: result.message || '请求失败' })
        }
        this.remember(sentence)
      } catch (error) {
        this.error = error.message
        this.messages.push({ role: 'assistant', text: error.message })
      } finally {
        this.sending = false
      }
    },
    async sendClinic(text) {
      const sentence = text.trim()
      if (!sentence || this.consultEnded) {
        return
      }
      this.sending = true
      this.error = ''
      this.messages.push({ role: 'user', text: sentence })
      this.remember(sentence)
      try {
        if (this.humanOnline) {
          await waitMs(420)
          this.messages.push({ role: 'human', text: replyAsHuman(sentence, this.card) })
        } else {
          await waitForDoctor()
          this.messages.push({
            role: 'doctor',
            text: replyAsDoctor(sentence, this.card, this.doctor),
          })
        }
        this.remember(sentence)
      } finally {
        this.sending = false
      }
    },
    async loadPatients() {
      try {
        const result = await listPatients()
        if (result.code === 200) {
          this.patients = result.data || []
        }
      } catch (error) {
        this.error = error.message
      }
    },
    async createPatient(payload) {
      const result = await createPatient(payload)
      if (result.code !== 200) {
        throw new Error(result.message || '新建就诊人失败')
      }
      await this.loadPatients()
      return result.data
    },
    async bindPatient(patientId) {
      if (!this.activeId || !patientId || this.sending) {
        return
      }
      this.sending = true
      this.error = ''
      try {
        const result = await bindPatient(this.activeId, patientId)
        if (result.code === 200) {
          this.applyResult(result.data)
          this.remember()
        } else {
          this.messages.push({ role: 'assistant', text: result.message || '绑定就诊人失败' })
        }
      } catch (error) {
        this.error = error.message
        this.messages.push({ role: 'assistant', text: error.message })
      } finally {
        this.sending = false
      }
    },
    async saveCard(patch) {
      if (!this.activeId || this.sending) {
        return false
      }
      this.sending = true
      this.error = ''
      try {
        const result = await patchCard({ consultationId: this.activeId, ...patch })
        if (result.code === 200) {
          const stopped = isSafetyStop(result.data.status)
          this.applyResult(result.data, { silent: !stopped })
          this.remember()
          return !stopped
        }
        this.messages.push({ role: 'assistant', text: result.message || '改卡失败' })
        return false
      } catch (error) {
        this.error = error.message
        this.messages.push({ role: 'assistant', text: error.message })
        return false
      } finally {
        this.sending = false
      }
    },
    async loadPackages() {
      if (this.packages.length) {
        return
      }
      try {
        const result = await listPackages()
        if (result.code === 200 && this.latest) {
          this.latest = { ...this.latest, packages: result.data || [] }
        } else if (result.code !== 200) {
          this.error = result.message || '档位加载失败'
        }
      } catch (error) {
        this.error = error.message
      }
    },
    async confirm() {
      if (!this.activeId || this.sending) {
        return
      }
      this.sending = true
      this.error = ''
      try {
        const result = await confirmCard(this.activeId)
        if (result.code === 200) {
          this.applyResult(result.data, { silent: true })
          if (this.latest?.status === STATUS.CONFIRMED) {
            this.payOrder = null
          } else {
            this.pushPrompt(this.latest)
          }
        } else {
          this.messages.push({ role: 'assistant', text: result.message || '确认失败' })
        }
        this.remember()
      } catch (error) {
        this.error = error.message
        this.messages.push({ role: 'assistant', text: error.message })
      } finally {
        this.sending = false
      }
    },
    async pay(pkg, channel) {
      if (!pkg || !this.activeId || this.paying || this.order) {
        return false
      }
      if (INQUIRY.closed) {
        this.error = '当前问诊通道正在维护，请稍后再试。'
        return false
      }
      this.paying = true
      this.error = ''
      try {
        const result = await placeOrder(this.activeId, pkg.code, channel, this.consultMinutes)
        if (result.code === 200) {
          this.applyResult(result.data, { silent: true })
          this.payOrder = result.data.order
          this.matchPhase = 'matching'
          this.remember()
          this.joinDoctor()
          this.startConsultClock()
          return true
        }
        this.error = result.message || '支付失败'
        return false
      } catch (error) {
        this.error = error.message
        return false
      } finally {
        this.paying = false
      }
    },
    lastMatchCard() {
      return [...this.messages].reverse().find((item) => item.kind === 'match') || null
    },
    async refreshOrder() {
      if (!this.activeId) {
        return
      }
      try {
        const result = await getConsultation(this.activeId)
        this.applyOrderView(result)
      } catch {
        // 排队轮询失败下一拍再试
      }
    },
    async waitOrder(holdMs) {
      if (!this.activeId || holdMs <= 0) {
        return
      }
      try {
        const result = await waitAssignment(this.activeId, holdMs)
        this.applyOrderView(result)
      } catch {
        await waitMs(Math.min(2000, holdMs))
        await this.refreshOrder()
      }
    },
    applyOrderView(result) {
      const view = result && result.code === 200 ? result.data?.view : null
      if (view) {
        this.latest = view
        this.payOrder = view.order || this.payOrder
      }
    },
    async joinDoctor() {
      if (this.doctorOnline || this.assigningDoctor || !this.order || this.consultEnded) {
        return
      }
      this.assigningDoctor = true
      this.matchPhase = 'matching'
      this.doctor = doctorFromOrder(this.order)
      if (!this.lastMatchCard()) {
        this.messages.push({
          role: 'system',
          kind: 'match',
          phase: 'matching',
          text: MATCH_ASSIST,
        })
      } else {
        const match = this.lastMatchCard()
        match.phase = 'matching'
        match.text = MATCH_ASSIST
      }
      this.remember()
      try {
        const match = this.lastMatchCard()
        const deadline = Date.now() + INQUIRY.matchTimeoutMs
        while (!doctorFromOrder(this.order) && Date.now() < deadline && !this.consultEnded && !this.doctorOnline) {
          await this.waitOrder(Math.min(10000, deadline - Date.now()))
        }
        this.doctor = doctorFromOrder(this.order)
        if (!this.doctor) {
          this.matchPhase = 'timeout'
          if (match) {
            match.phase = 'timeout'
            match.text = '当前暂无合适医生，请重试匹配或转人工。'
          }
          this.remember()
          return
        }
        await waitForDoctorAssign()
        if (!this.doctor) {
          this.matchPhase = 'timeout'
          if (match) {
            match.phase = 'timeout'
            match.text = '匹配超时，您可以重试或转人工。'
          }
          this.remember()
          return
        }
        if (!this.order || this.doctorOnline) {
          return
        }
        this.doctorOnline = true
        this.matchPhase = 'consulting'
        if (match) {
          match.phase = 'served'
          match.text = `${this.doctor.name}医生为您服务`
        }
        this.messages.push({
          role: 'doctor',
          text: greetAsDoctor(this.doctor),
        })
        this.remember()
        await waitForDoctorServe()
        if (!this.order) {
          return
        }
        this.messages.push({
          role: 'doctor',
          text: firstAskAsDoctor(),
        })
        this.remember()
      } finally {
        this.assigningDoctor = false
      }
    },
    async retryMatch() {
      if (this.matchPhase !== 'timeout' || this.doctorOnline || !this.order || this.consultEnded) {
        return
      }
      if (!doctorFromOrder(this.order)) {
        this.error = ''
        try {
          const result = await reassignDoctor(this.activeId)
          if (result.code === 200) {
            this.applyResult(result.data, { silent: true })
            this.payOrder = result.data.order
          } else {
            this.error = result.message || '当前暂无合适医生，请稍后再试。'
            this.messages.push({ role: 'system', text: this.error })
            this.remember()
            return
          }
        } catch (error) {
          this.error = error.message
          this.messages.push({ role: 'system', text: error.message })
          this.remember()
          return
        }
      }
      this.joinDoctor()
    },
    handoffAfterMatch() {
      if (this.consultEnded || this.doctorOnline) {
        return
      }
      this.humanOnline = true
      this.doctorOnline = false
      this.assigningDoctor = false
      this.matchPhase = null
      this.messages.push({ role: 'system', text: '已为您转接人工客服。' })
      this.messages.push({ role: 'human', text: HUMAN_JOIN })
      this.remember()
    },
    startConsultClock() {
      this.stopConsultClock()
      if (this.consultEnded || !this.order?.endsAt) {
        return
      }
      this.consultTick = Date.now()
      this.consultTimer = window.setInterval(() => {
        this.consultTick = Date.now()
        if (this.remainingMs <= 0) {
          // 用户还在页上时主动结束；关页后靠 XXL 释放占坑
          this.finishConsult('TIMEOUT')
        }
      }, 1000)
    },
    stopConsultClock() {
      if (this.consultTimer) {
        window.clearInterval(this.consultTimer)
        this.consultTimer = null
      }
    },
    async finishConsult(reason = 'ENDED') {
      if (!this.activeId || this.ending || this.consultEnded) {
        return
      }
      this.ending = true
      this.error = ''
      try {
        const result = await endConsultApi(this.activeId, reason)
        if (result.code === 200) {
          this.applyResult(result.data, { silent: true })
          this.payOrder = result.data.order
          this.doctorOnline = false
          this.assigningDoctor = false
          this.matchPhase = null
          this.stopConsultClock()
          const endedReason = result.data.order?.endReason || reason
          this.messages.push({
            role: 'system',
            text: endedReason === 'TIMEOUT' ? '问诊时间已到，已自动结束。' : '您已结束问诊。本会话已关闭。',
          })
          this.remember()
        } else {
          this.error = result.message || '结束问诊失败'
        }
      } catch (error) {
        this.error = error.message
      } finally {
        this.ending = false
      }
    },
    async changeConsultMinutes(minutes) {
      this.consultMinutes = minutes
      localStorage.setItem(CONSULT_MINUTES.storageKey, String(minutes))
      if (!this.activeId || this.consultEnded || !this.order) {
        return
      }
      try {
        const result = await setConsultMinutes(this.activeId, minutes)
        if (result.code === 200) {
          this.applyResult(result.data, { silent: true })
          this.payOrder = result.data.order
          this.startConsultClock()
          this.remember()
        } else {
          this.error = result.message || '修改时长失败'
        }
      } catch (error) {
        this.error = error.message
      }
    },
  },
})
