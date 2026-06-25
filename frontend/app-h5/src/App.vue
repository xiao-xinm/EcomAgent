<script setup lang="ts">
import ChatView from '@/views/ChatView.vue'
</script>

<template>
  <main class="app-h5-shell">
    <ChatView />
  </main>
</template>

<style scoped>
.app-h5-shell {
  height: 100dvh;
  min-height: 100dvh;
  overflow: hidden;
  background: #f5f5f5;
  padding-top: env(safe-area-inset-top);
  padding-bottom: env(safe-area-inset-bottom);
}
</style>
