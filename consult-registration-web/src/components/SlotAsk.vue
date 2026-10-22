<script setup>
import { ref } from 'vue'
import { NButton, NSelect } from 'naive-ui'
import { AGE_OPTIONS, DEFAULT_AGE, GENDER_OPTIONS, MISSING_SLOT } from '../constants/catalog'
import { useConsultationStore } from '../stores/consultation'

const store = useConsultationStore()
const age = ref(DEFAULT_AGE)

function pickGender(label) {
  store.send(label)
}

function pickAge() {
  if (!age.value) {
    return
  }
  store.send(`${age.value}岁`)
}
</script>

<template>
  <div v-if="store.missingSlot === MISSING_SLOT.GENDER" class="slot-ask">
    <div class="slot-label">请选择患者性别</div>
    <div class="slot-chips">
      <button
        v-for="item in GENDER_OPTIONS"
        :key="item.value"
        class="slot-chip"
        type="button"
        :disabled="store.sending"
        @click="pickGender(item.label)"
      >
        {{ item.label }}
      </button>
    </div>
  </div>

  <div v-else-if="store.missingSlot === MISSING_SLOT.AGE" class="slot-ask">
    <div class="slot-label">请选择患者年龄</div>
    <div class="slot-age">
      <n-select
        v-model:value="age"
        :options="AGE_OPTIONS"
        filterable
        placeholder="选择年龄"
        :disabled="store.sending"
      />
      <n-button type="primary" :disabled="!age" :loading="store.sending" @click="pickAge">
        确定
      </n-button>
    </div>
  </div>
</template>
