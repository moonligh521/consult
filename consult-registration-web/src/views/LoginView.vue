<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { NButton, NInput, useMessage } from 'naive-ui'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const auth = useAuthStore()
const message = useMessage()
const mobile = ref('')
const captcha = ref('')
const sending = ref(false)
const logging = ref(false)

async function sendCode() {
  sending.value = true
  try {
    await auth.sendCode(mobile.value)
    message.success('验证码已发送。演示环境可填 000000')
  } catch (error) {
    message.error(error.message)
  } finally {
    sending.value = false
  }
}

async function submit() {
  logging.value = true
  try {
    await auth.login(mobile.value, captcha.value)
    await router.replace('/')
  } catch (error) {
    message.error(error.message)
  } finally {
    logging.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <form class="login-card" @submit.prevent="submit">
      <h1>极速问诊</h1>
      <p>登录后选择就诊人、描述主诉、完成分诊。</p>
      <label>
        手机号
        <n-input v-model:value="mobile" maxlength="11" placeholder="11 位手机号" />
      </label>
      <label class="captcha-row">
        验证码
        <div class="captcha-fields">
          <n-input v-model:value="captcha" maxlength="6" placeholder="6 位验证码" />
          <n-button attr-type="button" :loading="sending" @click="sendCode">获取验证码</n-button>
        </div>
      </label>
      <n-button type="primary" attr-type="submit" :loading="logging" block>
        登录并进入咨询
      </n-button>
    </form>
  </div>
</template>
