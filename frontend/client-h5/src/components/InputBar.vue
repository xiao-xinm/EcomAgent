<script setup lang="ts">
import { ref } from 'vue'

defineProps<{
  disabled?: boolean
}>()

const emit = defineEmits<{
  send: [content: string]
}>()

const input = ref('')

function handleSend() {
  const trimmed = input.value.trim()
  if (!trimmed) return
  emit('send', trimmed)
  input.value = ''
}

function handleKeydown(e: KeyboardEvent) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    handleSend()
  }
}
</script>

<template>
  <div class="flex-shrink-0 bg-white border-t border-gray-200 px-3 py-2">
    <div class="flex items-end gap-2">
      <textarea
        v-model="input"
        :disabled="disabled"
        rows="1"
        placeholder="输入消息…"
        class="flex-1 resize-none rounded-xl border border-gray-300 px-3 py-2 text-sm leading-relaxed focus:outline-none focus:border-primary disabled:opacity-50 max-h-24"
        @keydown="handleKeydown"
      />
      <button
        :disabled="disabled || !input.trim()"
        class="flex-shrink-0 h-9 px-4 rounded-xl bg-primary text-white text-sm font-medium disabled:opacity-40 hover:bg-blue-600 active:bg-blue-700 transition-colors"
        @click="handleSend"
      >
        发送
      </button>
    </div>
  </div>
</template>
