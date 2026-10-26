<script setup>
import { computed, ref, watch } from 'vue'
import { CONSULT_MINUTES, STATUS, money } from '../constants/catalog'
import { useConsultationStore } from '../stores/consultation'

const store = useConsultationStore()
const sheet = ref(null)
const activeCode = ref(null)
const checkoutPkg = ref(null)
const channel = ref('wechat')

const visible = computed(() => (
  (store.status === STATUS.CARD_READY || store.status === STATUS.CONFIRMED)
  && store.showCard
))
const packages = computed(() => store.packages)
const active = computed(() => packages.value.find((item) => item.code === activeCode.value) || null)
const paid = computed(() => store.order)
const catalogOpen = computed(() => visible.value && !paid.value)

watch(
  catalogOpen,
  async (show) => {
    if (show && !store.packages.length) {
      await store.loadPackages()
    }
  },
  { immediate: true },
)

function openDetail(item) {
  if (paid.value) {
    return
  }
  checkoutPkg.value = item
  activeCode.value = item.code
  sheet.value = 'detail'
}

async function openCheckout(item) {
  if (paid.value || store.paying || store.sending) {
    return
  }
  if (store.status === STATUS.CARD_READY) {
    await store.confirm()
    if (store.status !== STATUS.CONFIRMED) {
      return
    }
  }
  checkoutPkg.value = item
  activeCode.value = item.code
  channel.value = 'wechat'
  sheet.value = 'checkout'
}

function openOrder() {
  if (!paid.value) {
    return
  }
  sheet.value = 'order'
}

function close() {
  if (store.paying) {
    return
  }
  sheet.value = null
  activeCode.value = null
  checkoutPkg.value = null
}

async function confirmPay() {
  if (!checkoutPkg.value) {
    return
  }
  const ok = await store.pay(checkoutPkg.value, channel.value)
  if (ok) {
    sheet.value = null
  }
}
</script>

<template>
  <section v-if="catalogOpen" class="pay-wrap">
    <p class="pay-legal">
      请您仔细阅读用户服务相关声明。如非复诊，仅提供就医信息整理，不能替代医师诊查。继续咨询代表您已知悉并同意相关条款。
    </p>

    <article class="pay-board">
      <p v-if="!packages.length" class="pay-done">{{ store.error || '正在加载咨询服务…' }}</p>

      <div
        v-for="item in packages"
        :key="item.code"
        class="pay-row"
      >
        <div class="pay-row-main">
          <button
            class="pay-name"
            type="button"
            @click="openDetail(item)"
          >
            {{ item.name }}
            <em v-if="item.badge">{{ item.badge }}</em>
          </button>
          <div class="pay-summary">{{ item.summary }}</div>
        </div>
        <div class="pay-row-side">
          <div class="pay-price">
            <s>¥{{ money(item.originPrice) }}</s>
            <strong>¥{{ money(item.payPrice) }}</strong>
          </div>
          <button
            class="pay-go"
            type="button"
            :disabled="store.paying"
            @click="openCheckout(item)"
          >
            去咨询
          </button>
        </div>
      </div>
    </article>
  </section>

  <button
    v-else-if="visible && paid"
    class="im-fold"
    type="button"
    @click="openOrder"
  >
    <div>
      <strong>{{ paid.packageName }} · 已支付 ¥{{ money(paid.payAmount) }}</strong>
      <p>订单已完成，点击查看明细</p>
    </div>
    <span>展开查看</span>
  </button>

  <Teleport to="body">
    <div v-if="sheet === 'detail' && active" class="pay-mask" @click.self="close">
      <div class="pay-sheet" role="dialog" aria-labelledby="pay-sheet-title">
        <header class="pay-sheet-head">
          <h2 id="pay-sheet-title">服务详情</h2>
          <button class="pay-sheet-close" type="button" aria-label="关闭" @click="close">×</button>
        </header>
        <div class="pay-sheet-body">
          <h3>{{ active.name }}</h3>
          <ul>
            <li v-for="line in active.highlights" :key="line">{{ line }}</li>
          </ul>
          <p><span>咨询规则：</span>{{ active.rule }}</p>
          <p v-if="active.schedule"><span>出诊时间安排：</span>{{ active.schedule }}</p>
          <p><span>退款规则：</span>{{ active.refund }}</p>
          <div class="pay-bill">
            <h4>价格明细</h4>
            <div class="pay-bill-row">
              <span>{{ active.name }}</span>
              <span>¥{{ money(active.originPrice) }}</span>
            </div>
            <div class="pay-bill-row off">
              <span>平台立减</span>
              <span>- ¥{{ money(active.discount) }}</span>
            </div>
          </div>
        </div>
        <footer class="pay-sheet-foot">
          <div>
            <div class="pay-total">合计：<em>¥{{ money(active.payPrice) }}</em></div>
            <div class="pay-off">已优惠 ¥{{ money(active.discount) }}</div>
          </div>
          <button class="pay-now" type="button" @click="openCheckout(active)">去支付</button>
        </footer>
      </div>
    </div>

    <div v-if="sheet === 'checkout' && checkoutPkg" class="pay-mask" @click.self="close">
      <div class="pay-sheet" role="dialog" aria-labelledby="pay-checkout-title">
        <header class="pay-sheet-head">
          <h2 id="pay-checkout-title">收银台</h2>
          <button class="pay-sheet-close" type="button" aria-label="关闭" :disabled="store.paying" @click="close">×</button>
        </header>
        <div class="pay-sheet-body">
          <div class="checkout-amount">
            <span>应付金额</span>
            <strong>¥{{ money(checkoutPkg.payPrice) }}</strong>
          </div>
          <p class="checkout-item">{{ checkoutPkg.name }}</p>
          <label class="checkout-minutes">
            <span>问诊时长</span>
            <select
              :value="store.consultMinutes"
              :disabled="store.paying"
              @change="store.changeConsultMinutes(Number($event.target.value))"
            >
              <option
                v-for="item in CONSULT_MINUTES.options"
                :key="item.value"
                :value="item.value"
              >
                {{ item.label }}
              </option>
            </select>
          </label>
          <h4>支付方式</h4>
          <div class="pay-channels">
            <button
              class="pay-channel"
              type="button"
              :class="{ on: channel === 'wechat' }"
              :disabled="store.paying"
              @click="channel = 'wechat'"
            >
              微信支付
            </button>
            <button
              class="pay-channel"
              type="button"
              :class="{ on: channel === 'alipay' }"
              :disabled="store.paying"
              @click="channel = 'alipay'"
            >
              支付宝
            </button>
          </div>
          <p v-if="store.error" class="checkout-error">{{ store.error }}</p>
        </div>
        <footer class="pay-sheet-foot">
          <div class="pay-total">合计：<em>¥{{ money(checkoutPkg.payPrice) }}</em></div>
          <button class="pay-now" type="button" :disabled="store.paying" @click="confirmPay">
            {{ store.paying ? '支付中…' : '确认支付' }}
          </button>
        </footer>
      </div>
    </div>

    <div v-if="sheet === 'order' && paid" class="pay-mask" @click.self="close">
      <div class="pay-sheet" role="dialog" aria-labelledby="pay-order-title">
        <header class="pay-sheet-head">
          <h2 id="pay-order-title">订单详情</h2>
          <button class="pay-sheet-close" type="button" aria-label="关闭" @click="close">×</button>
        </header>
        <div class="pay-sheet-body">
          <h3>{{ paid.packageName }}</h3>
          <div class="pay-bill">
            <div class="pay-bill-row">
              <span>订单号</span>
              <span>{{ paid.orderId }}</span>
            </div>
            <div class="pay-bill-row">
              <span>划线价</span>
              <span>¥{{ money(paid.originAmount) }}</span>
            </div>
            <div class="pay-bill-row off">
              <span>优惠</span>
              <span>- ¥{{ money(paid.discountAmount) }}</span>
            </div>
            <div class="pay-bill-row">
              <span>实付</span>
              <span>¥{{ money(paid.payAmount) }}</span>
            </div>
            <div class="pay-bill-row">
              <span>状态</span>
              <span>已支付</span>
            </div>
          </div>
        </div>
        <footer class="pay-sheet-foot">
          <button class="pay-now" type="button" @click="close">收起</button>
        </footer>
      </div>
    </div>
  </Teleport>
</template>
