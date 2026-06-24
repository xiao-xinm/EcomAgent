<script setup lang="ts">
import { computed } from 'vue'
import type { ChatMessage } from '@/types/chat'

const props = defineProps<{
  loading?: boolean
  syncing?: boolean
  lastMessage?: ChatMessage
}>()

function metadataText(message: ChatMessage | undefined, key: string): string {
  const value = message?.metadata?.[key]
  return typeof value === 'string' ? value : ''
}

const status = computed(() => {
  if (props.loading) {
    return { text: '处理中', tone: 'blue' }
  }
  if (props.syncing) {
    return { text: '同步中', tone: 'gray' }
  }

  const message = props.lastMessage
  if (!message) {
    return { text: '在线', tone: 'green' }
  }

  const actionType = metadataText(message, 'actionType')
  if (actionType === 'TAKEOVER_FINISHED' || actionType === 'APPROVAL_APPROVED') {
    return { text: '已完成', tone: 'green' }
  }
  if (actionType === 'APPROVAL_REJECTED') {
    return { text: '审核未通过', tone: 'red' }
  }
  if (actionType === 'TAKEOVER_STARTED' || message.backendRole === 'HUMAN_AGENT') {
    return { text: '人工接管中', tone: 'purple' }
  }

  switch (message.routeDecision) {
    case 'CONFIRM_BEFORE_EXECUTE':
      return { text: '等待确认', tone: 'amber' }
    case 'HUMAN_REVIEW':
      return { text: '人工审核中', tone: 'amber' }
    case 'HUMAN_TAKEOVER':
      return { text: '人工接管中', tone: 'purple' }
    case 'AUTO_REPLY':
    case 'AUTO_EXECUTE':
      return { text: '已回复', tone: 'green' }
    default:
      return { text: '在线', tone: 'green' }
  }
})

const toneClass = computed(() => {
  switch (status.value.tone) {
    case 'blue':
      return 'bg-blue-50 text-blue-700 border-blue-100'
    case 'amber':
      return 'bg-amber-50 text-amber-700 border-amber-100'
    case 'purple':
      return 'bg-violet-50 text-violet-700 border-violet-100'
    case 'red':
      return 'bg-red-50 text-red-700 border-red-100'
    case 'gray':
      return 'bg-gray-50 text-gray-500 border-gray-100'
    default:
      return 'bg-emerald-50 text-emerald-700 border-emerald-100'
  }
})
</script>

<template>
  <div class="flex-shrink-0 border-b border-gray-100 bg-white px-3 py-2">
    <div
      :class="['inline-flex items-center rounded-full border px-2.5 py-1 text-xs', toneClass]"
    >
      <span
        :class="[
          'mr-1.5 h-1.5 w-1.5 rounded-full',
          loading || syncing ? 'animate-pulse bg-current' : 'bg-current',
        ]"
      />
      {{ status.text }}
    </div>
  </div>
</template>
