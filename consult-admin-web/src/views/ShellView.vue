<script setup>
import { computed, onMounted } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { NButton, NLayout, NLayoutContent, NLayoutHeader, NLayoutSider } from 'naive-ui'
import { useAuthStore } from '../stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const menus = [
  { label: '患者账号', key: '/accounts' },
  { label: '就诊人', key: '/patients' },
  { label: '咨询会话', key: '/consultations' },
  { label: '科室目录', key: '/departments' },
  { label: '医生管理', key: '/doctors' },
  { label: '排班管理', key: '/schedules' },
]

const active = computed(() => route.path)

onMounted(() => {
  auth.loadMe()
})

function logout() {
  auth.logout()
  router.replace('/login')
}
</script>

<template>
  <n-layout has-sider style="height: 100%">
    <n-layout-sider bordered width="200">
      <div class="brand">问诊 HIS</div>
      <nav class="side-nav">
        <RouterLink
          v-for="item in menus"
          :key="item.key"
          :to="item.key"
          class="side-link"
          :class="{ on: active === item.key }"
        >
          {{ item.label }}
        </RouterLink>
      </nav>
    </n-layout-sider>
    <n-layout style="height: 100%">
      <n-layout-header bordered style="height: 56px; display: flex; align-items: center; justify-content: space-between; padding: 0 20px">
        <span>{{ auth.name || auth.username || '工作人员' }}</span>
        <n-button text @click="logout">退出</n-button>
      </n-layout-header>
      <n-layout-content content-style="padding: 0; overflow: auto; height: calc(100% - 56px)">
        <router-view />
      </n-layout-content>
    </n-layout>
  </n-layout>
</template>
