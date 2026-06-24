<script setup lang="ts">
import type { ChatMessage } from '@/types/chat'
import type { QuickAction } from '@/types/api'
import QuickActions from './QuickActions.vue'

defineProps<{
  message: ChatMessage
}>()

const emit = defineEmits<{
  quickAction: [action: QuickAction, message: ChatMessage]
  retry: [messageId: string]
}>()

function formatTime(ts: number): string {
  const d = new Date(ts)
  const hh = String(d.getHours()).padStart(2, '0')
  const mm = String(d.getMinutes()).padStart(2, '0')
  return `${hh}:${mm}`
}

function bubbleClass(message: ChatMessage): string {
  if (message.role === 'user') {
    return 'bg-primary text-white rounded-br-md'
  }
  if (message.backendRole === 'SYSTEM' || message.role === 'system') {
    return 'bg-amber-50 text-amber-800 rounded-md border border-amber-100'
  }
  if (message.backendRole === 'HUMAN_AGENT') {
    return 'bg-violet-50 text-violet-900 rounded-bl-md border border-violet-100'
  }
  return 'bg-white text-gray-800 rounded-bl-md shadow-sm'
}

function displayName(message: ChatMessage): string {
  if (message.role === 'user') return '用户'
  if (message.backendRole === 'HUMAN_AGENT') return '人工坐席'
  if (message.backendRole === 'SYSTEM' || message.role === 'system') return '系统'
  return 'AI Agent'
}
</script>

<template>
  <div :class="['flex', message.role === 'user' ? 'justify-end' : 'justify-start']">
    <div
      :class="['max-w-[80%]', message.role === 'user' ? 'items-end' : 'items-start']"
      class="flex flex-col"
    >
      <!-- Bubble -->
      <div
        :class="[
          'px-3 py-2 rounded-2xl text-sm leading-relaxed break-words whitespace-pre-wrap',
          bubbleClass(message),
        ]"
      >
        <div
          v-if="message.role !== 'user'"
          class="mb-1 text-[11px] font-medium opacity-70"
        >
          {{ displayName(message) }}
        </div>
        {{ message.content }}
      </div>

      <!-- Quick actions (agent only) -->
      <QuickActions
        v-if="message.role === 'agent' && message.quickActions?.length"
        :actions="message.quickActions"
        @select="emit('quickAction', $event, message)"
      />

      <!-- Meta: time + status -->
      <div class="flex items-center gap-1 mt-1 px-1">
        <span class="text-[11px] text-gray-400">{{ formatTime(message.timestamp) }}</span>
        <span
          v-if="message.status === 'sending'"
          class="text-[11px] text-gray-400"
        >
          发送中…
        </span>
        <button
          v-else-if="message.status === 'failed'"
          class="text-[11px] text-red-500 hover:underline"
          @click="emit('retry', message.id)"
        >
          发送失败，点击重试
        </button>
      </div>
    </div>
  </div>
</template>
