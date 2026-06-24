<script setup lang="ts">
import { ref, nextTick, watch, onMounted, onBeforeUnmount } from 'vue'
import { useChatStore } from '@/stores/chat'
import type { QuickAction } from '@/types/api'
import MessageBubble from '@/components/MessageBubble.vue'
import InputBar from '@/components/InputBar.vue'
import TypingIndicator from '@/components/TypingIndicator.vue'

const store = useChatStore()
const messageListRef = ref<HTMLElement | null>(null)

function scrollToBottom() {
  nextTick(() => {
    if (messageListRef.value) {
      messageListRef.value.scrollTop = messageListRef.value.scrollHeight
    }
  })
}

watch(() => store.messages.length, scrollToBottom)
watch(() => store.loading, scrollToBottom)

function handleSend(content: string) {
  store.send(content)
}

function handleQuickAction(action: QuickAction) {
  store.handleAction(action)
}

function handleRetry(messageId: string) {
  store.retry(messageId)
}

const WELCOME_MESSAGE = {
  id: 'welcome',
  role: 'agent' as const,
  content: '你好！我是智能客服助手，有什么可以帮您的吗？',
  timestamp: Date.now(),
  status: 'sent' as const,
}

onMounted(async () => {
  if (store.messages.length === 0) {
    const hasHistory = await store.loadHistory()
    if (!hasHistory) {
      store.messages.push(WELCOME_MESSAGE)
    }
  }
  store.startPolling()
})

onBeforeUnmount(() => {
  store.stopPolling()
})
</script>

<template>
  <div class="flex flex-col h-full bg-gray-100">
    <!-- Header -->
    <header class="flex-shrink-0 bg-white border-b border-gray-200 px-4 py-3 flex items-center justify-center">
      <h1 class="text-base font-semibold text-gray-800">
        智能客服
      </h1>
    </header>

    <!-- Message list -->
    <div
      ref="messageListRef"
      class="flex-1 overflow-y-auto px-3 py-4 space-y-3"
    >
      <MessageBubble
        v-for="msg in store.messages"
        :key="msg.id"
        :message="msg"
        @quick-action="handleQuickAction"
        @retry="handleRetry"
      />

      <TypingIndicator v-if="store.loading" />
    </div>

    <!-- Error banner -->
    <div
      v-if="store.error"
      class="flex-shrink-0 bg-red-50 border-t border-red-200 px-4 py-2 text-center text-sm text-red-600"
    >
      {{ store.error }}
    </div>

    <!-- Input -->
    <InputBar
      :disabled="store.loading"
      @send="handleSend"
    />
  </div>
</template>
