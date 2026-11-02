<script setup>
import { NConfigProvider, NMessageProvider, zhCN, dateZhCN } from 'naive-ui'

/** 主色对齐智能导诊医疗蓝。 */
const themeOverrides = {
  common: {
    primaryColor: '#2488F5',
    primaryColorHover: '#1b78e0',
    primaryColorPressed: '#1669c7',
    primaryColorSuppl: '#2488F5',
    borderRadius: '8px',
  },
}
</script>

<template>
  <n-config-provider :locale="zhCN" :date-locale="dateZhCN" :theme-overrides="themeOverrides">
    <n-message-provider>
      <router-view />
    </n-message-provider>
  </n-config-provider>
</template>
