import { defineStore } from 'pinia'
import { confirmCard, listPackages, patchCard, placeOrder, sendMessage } from '../api/consultations'
import { canShowCard, isSafetyStop, money, STATUS } from '../constants/catalog'
import { HUMAN_JOIN, replyAsHuman, waitForHuman } from '../domain/humanAgent'

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

function newMessageId() {
  return crypto.randomUUID()
}

function titleOf(text) {
  const value = (text || '新的咨询').replace(/\s+/g, ' ').trim()
  return value.length > 18 ? `${value.slice(0, 18)}…` : value
}

/**
 * 咨询会话。列表只存在浏览器里，病情原文以服务端为准。
 */
export const useConsultationStore = defineStore('consultation', {
  state: () => ({
    sessions: loadSessions(),
    activeId: null,
    serviceType: 0,
    messages: [],
    latest: null,
    payOrder: null,
    humanOnline: false,
    sending: false,
    error: '',
  }),
  getters: {
    status: (state) => state.latest?.status ?? null,
    card: (state) => state.latest?.card ?? null,
    prompt: (state) => state.latest?.prompt ?? '',
    safetyNotice: (state) => state.latest?.safetyNotice ?? null,
    handoff: (state) => state.latest?.handoff ?? null,
    showCard: (state) => canShowCard(state.latest?.status, state.latest?.card),
    missingSlot: (state) => (state.latest?.status === STATUS.NEED_SLOT ? state.latest?.missingSlot ?? null : null),
    packages: (state) => state.latest?.packages ?? [],
    order: (state) => state.latest?.order ?? state.payOrder,
    stopped: (state) => state.latest != null && isSafetyStop(state.latest.status),
  },
  actions: {
    startNew() {
      this.activeId = null
      this.latest = null
      this.payOrder = null
      this.humanOnline = false
      this.messages = []
      this.error = ''
    },
    openSession(id) {
      const found = this.sessions.find((item) => item.id === id)
      if (!found) {
        return
      }
      this.activeId = id
      this.serviceType = found.serviceType ?? 0
      this.latest = found.snapshot || null
      this.payOrder = found.payOrder || null
      this.messages = found.messages || []
      this.humanOnline = this.status === STATUS.NEED_HUMAN
      this.error = ''
    },
    remember(text) {
      if (!this.activeId) {
        return
      }
      const index = this.sessions.findIndex((item) => item.id === this.activeId)
      const record = {
        id: this.activeId,
        title: this.sessions[index]?.title || titleOf(text),
        serviceType: this.serviceType,
        updatedAt: Date.now(),
        snapshot: this.latest,
        payOrder: this.payOrder,
        messages: this.messages,
      }
      if (index >= 0) {
        this.sessions.splice(index, 1, record)
      } else {
        this.sessions.unshift(record)
      }
      persist(this.sessions)
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
      if (data.prompt) {
        this.messages.push({ role: 'assistant', text: data.prompt, status: data.status })
      }
      if (data.safetyNotice) {
        this.messages.push({ role: 'assistant', text: data.safetyNotice, notice: true })
      }
    },
    async send(text) {
      const sentence = text.trim()
      if (!sentence || this.sending) {
        return
      }
      this.sending = true
      this.error = ''
      this.messages.push({ role: 'user', text: sentence })
      try {
        const result = await sendMessage({
          consultationId: this.activeId,
          messageId: newMessageId(),
          serviceType: this.serviceType || null,
          text: sentence,
        })
        if (result.code === 200) {
          const joined = this.humanOnline
          this.applyResult(result.data, { silent: result.data.status === STATUS.NEED_HUMAN })
          if (result.data.status === STATUS.NEED_HUMAN) {
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
    async saveCard(patch) {
      if (!this.activeId || this.sending) {
        return false
      }
      this.sending = true
      this.error = ''
      try {
        const result = await patchCard({ consultationId: this.activeId, ...patch })
        if (result.code === 200) {
          this.applyResult(result.data, { silent: true })
          this.remember()
          return true
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
          } else if (this.latest?.prompt) {
            this.messages.push({ role: 'assistant', text: this.latest.prompt, status: this.latest.status })
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
    async pay(pkg) {
      if (!pkg || !this.activeId || this.sending || this.order) {
        return
      }
      this.sending = true
      this.error = ''
      try {
        const result = await placeOrder(this.activeId, pkg.code)
        if (result.code === 200) {
          this.applyResult(result.data, { silent: true })
          this.payOrder = result.data.order
          const name = result.data.order?.packageName || pkg.name
          const amount = result.data.order?.payAmount ?? pkg.payPrice
          this.messages.push({
            role: 'assistant',
            text: `已选择「${name}」，应付 ¥${money(amount)}。当前是演示环境，不会真实扣款，也不会派给真实医生。`,
          })
          this.remember()
        } else {
          this.messages.push({ role: 'assistant', text: result.message || '下单失败' })
        }
      } catch (error) {
        this.error = error.message
        this.messages.push({ role: 'assistant', text: error.message })
      } finally {
        this.sending = false
      }
    },
  },
})
