<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { NButton, NInput, useMessage } from 'naive-ui'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const auth = useAuthStore()
const message = useMessage()
const username = ref('admin')
const password = ref('')
const loading = ref(false)

async function submit() {
  loading.value = true
  try {
    await auth.login(username.value, password.value)
    await router.replace('/accounts')
  } catch (error) {
    message.error(error.message)
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <form class="login-card" @submit.prevent="submit">
      <h1>问诊管理后台</h1>
      <p>工作人员登录后管理账号、就诊人、咨询、科室、医生与排班。</p>
      <n-input v-model:value="username" placeholder="用户名" />
      <n-input v-model:value="password" type="password" show-password-on="mousedown" placeholder="密码" />
      <n-button type="primary" attr-type="submit" :loading="loading" block>登录</n-button>
      <p class="hint">演示账号 admin / admin1234</p>
    </form>
  </div>
</template>
