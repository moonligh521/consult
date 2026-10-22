<script setup>
import { computed, ref, watch } from 'vue'
import { STATUS, money } from '../constants/catalog'
import { useConsultationStore } from '../stores/consultation'

const store = useConsultationStore()
const activeCode = ref(null)

const visible = computed(() => store.status === STATUS.CONFIRMED && store.showCard)
const packages = computed(() => store.packages)
const active = computed(() => packages.value.find((item) => item.code === activeCode.value) || null)
const paid = computed(() => store.order)

watch(
  visible,
  async (show) => {
    if (show && !store.packages.length) {
      await store.loadPackages()
    }
  },
  { immediate: true },
)

function open(item) {
  if (paid.value) {
    return
  }
  activeCode.value = item.code
}

function close() {
  activeCode.value = null
}

async function pay() {
  if (!active.value || store.sending) {
    return
  }
  await store.pay(active.value)
  close()
}
</script>

<template>
  <section v-if="visible" class="pay-wrap">
    <p class="pay-legal">
      请您仔细阅读用户服务相关声明。如非复诊，仅提供就医信息整理，不能替代医师诊查。继续咨询代表您已知悉并同意相关条款。
    </p>

    <article class="pay-board">
      <header class="pay-board-head">
        <span class="pay-flag">咨询档位</span>
        <span>价格与档位来自服务端 · 不真实扣款</span>
      </header>

      <p v-if="!packages.length" class="pay-done">{{ store.error || '正在从服务端拉取档位…' }}</p>

      <div v-if="paid" class="pay-done">
        已选择「{{ paid.packageName }}」，应付 ¥{{ money(paid.payAmount) }}。演示环境未扣款。
      </div>

      <div
        v-for="item in packages"
        :key="item.code"
        class="pay-row"
      >
        <div class="pay-row-main">
          <div class="pay-name">
            {{ item.name }}
            <em v-if="item.badge">{{ item.badge }}</em>
          </div>
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
            :disabled="!!paid"
            @click="open(item)"
          >
            {{ paid?.packageCode === item.code ? '已选择' : '去咨询' }}
          </button>
        </div>
      </div>
    </article>
  </section>

  <Teleport to="body">
    <div v-if="active" class="pay-mask" @click.self="close">
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
          <button class="pay-now" type="button" :disabled="store.sending" @click="pay">立即咨询</button>
        </footer>
      </div>
    </div>
  </Teleport>
</template>
