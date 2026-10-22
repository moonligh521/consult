<script setup>
defineProps({
  size: { type: Number, default: 40 },
})
</script>

<template>
  <span class="zhi-avatar" :style="{ width: `${size}px`, height: `${size}px` }" aria-hidden="true">
    <svg viewBox="0 0 64 64" width="100%" height="100%">
      <circle cx="32" cy="32" r="30" fill="#ffffff" />
      <circle cx="32" cy="32" r="30" fill="none" stroke="#d7e8fb" stroke-width="2" />
      <rect x="29" y="6" width="6" height="8" rx="3" fill="#2488F5" />
      <circle cx="32" cy="6" r="3.2" fill="#7cc4ff" />
      <rect x="14" y="20" width="36" height="24" rx="12" fill="#2488F5" />
      <circle cx="24.5" cy="32" r="4.2" fill="#ffffff" />
      <circle cx="39.5" cy="32" r="4.2" fill="#ffffff" />
      <path d="M26 44c2.4 3 9.6 3 12 0" fill="none" stroke="#2488F5" stroke-width="2.4" stroke-linecap="round" />
    </svg>
  </span>
</template>
