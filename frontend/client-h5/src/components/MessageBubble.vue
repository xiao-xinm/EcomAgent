<script setup lang="ts">
import type { ChatMessage } from '@/types/chat'
import type { QuickAction } from '@/types/api'
import QuickActions from './QuickActions.vue'

defineProps<{
  message: ChatMessage
}>()

const emit = defineEmits<{
  quickAction: [action: QuickAction]
  retry: [messageId: string]
}>()

function formatTime(ts: number): string {
  const d = new Date(ts)
  const hh = String(d.getHours()).padStart(2, '0')
  const mm = String(d.getMinutes()).padStart(2, '0')
  return `${hh}:${mm}`
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
          message.role === 'user'
            ? 'bg-primary text-white rounded-br-md'
            : 'bg-white text-gray-800 rounded-bl-md shadow-sm',
        ]"
      >
        {{ message.content }}
      </div>

      <!-- Quick actions (agent only) -->
      <QuickActions
        v-if="message.role === 'agent' && message.quickActions?.length"
        :actions="message.quickActions"
        @select="emit('quickAction', $event)"
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
