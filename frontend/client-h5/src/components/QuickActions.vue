<script setup lang="ts">
import type { QuickAction } from '@/types/api'

defineProps<{
  actions: QuickAction[]
}>()

const emit = defineEmits<{
  select: [action: QuickAction]
}>()
</script>

<template>
  <div class="flex flex-wrap gap-1.5 mt-2">
    <button
      v-for="action in actions"
      :key="action.value"
      class="px-3 py-1 text-xs rounded-full border border-primary text-primary bg-primary-light hover:bg-primary hover:text-white transition-colors"
      @click="emit('select', action)"
    >
      {{ action.label }}
    </button>
  </div>
</template>
