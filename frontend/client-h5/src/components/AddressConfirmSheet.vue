<script setup lang="ts">
import { reactive, watch } from 'vue'
import {
  emptyAddressConfirmForm,
  hasAddressConfirmErrors,
  validateAddressConfirmForm,
  type AddressConfirmErrors,
  type AddressConfirmForm,
} from '@/utils/addressConfirm'

const props = defineProps<{
  open: boolean
  initialOrderNo?: string
  submitting?: boolean
}>()

const emit = defineEmits<{
  close: []
  submit: [form: AddressConfirmForm]
}>()

const form = reactive<AddressConfirmForm>(emptyAddressConfirmForm())
const errors = reactive<AddressConfirmErrors>({})

function clearErrors() {
  Object.keys(errors).forEach((key) => {
    delete errors[key as keyof AddressConfirmErrors]
  })
}

function resetForm() {
  Object.assign(
    form,
    emptyAddressConfirmForm({
      orderNo: props.initialOrderNo || '',
      changeReason: '用户确认修改收货地址',
    }),
  )
  clearErrors()
}

function fieldClass(field: keyof AddressConfirmForm): string {
  return [
    'w-full rounded-lg border px-3 py-2 text-sm outline-none transition-colors',
    errors[field] ? 'border-red-400 bg-red-50' : 'border-gray-300 focus:border-primary',
  ].join(' ')
}

function applyErrors(nextErrors: AddressConfirmErrors) {
  clearErrors()
  Object.assign(errors, nextErrors)
}

function handleSubmit() {
  const nextErrors = validateAddressConfirmForm(form)
  applyErrors(nextErrors)
  if (hasAddressConfirmErrors(nextErrors)) return
  emit('submit', { ...form })
}

watch(
  () => props.open,
  (open) => {
    if (open) resetForm()
  },
)
</script>

<template>
  <Teleport to="body">
    <div
      v-if="open"
      class="fixed inset-0 z-50 flex items-end bg-black/35"
      @click.self="emit('close')"
    >
      <section class="w-full max-h-[86vh] overflow-y-auto rounded-t-2xl bg-white shadow-xl">
        <div class="sticky top-0 z-10 flex items-center justify-between border-b border-gray-100 bg-white px-4 py-3">
          <h2 class="text-base font-semibold text-gray-900">
            确认修改收货地址
          </h2>
          <button
            type="button"
            class="rounded-full px-3 py-1 text-sm text-gray-500 hover:bg-gray-100"
            :disabled="submitting"
            @click="emit('close')"
          >
            关闭
          </button>
        </div>

        <div class="space-y-3 px-4 py-4">
          <label class="block">
            <span class="mb-1 block text-xs text-gray-500">订单号</span>
            <input
              v-model="form.orderNo"
              :class="fieldClass('orderNo')"
              placeholder="例如 E2E-ORDER-1002"
            >
            <span
              v-if="errors.orderNo"
              class="mt-1 block text-xs text-red-500"
            >
              {{ errors.orderNo }}
            </span>
          </label>

          <div class="grid grid-cols-2 gap-3">
            <label class="block">
              <span class="mb-1 block text-xs text-gray-500">收货人</span>
              <input
                v-model="form.consigneeName"
                :class="fieldClass('consigneeName')"
                placeholder="姓名"
              >
              <span
                v-if="errors.consigneeName"
                class="mt-1 block text-xs text-red-500"
              >
                {{ errors.consigneeName }}
              </span>
            </label>
            <label class="block">
              <span class="mb-1 block text-xs text-gray-500">手机号</span>
              <input
                v-model="form.consigneePhone"
                :class="fieldClass('consigneePhone')"
                inputmode="tel"
                placeholder="11 位手机号"
              >
              <span
                v-if="errors.consigneePhone"
                class="mt-1 block text-xs text-red-500"
              >
                {{ errors.consigneePhone }}
              </span>
            </label>
          </div>

          <div class="grid grid-cols-3 gap-2">
            <label class="block">
              <span class="mb-1 block text-xs text-gray-500">省份</span>
              <input
                v-model="form.province"
                :class="fieldClass('province')"
                placeholder="省"
              >
              <span
                v-if="errors.province"
                class="mt-1 block text-xs text-red-500"
              >
                {{ errors.province }}
              </span>
            </label>
            <label class="block">
              <span class="mb-1 block text-xs text-gray-500">城市</span>
              <input
                v-model="form.city"
                :class="fieldClass('city')"
                placeholder="市"
              >
              <span
                v-if="errors.city"
                class="mt-1 block text-xs text-red-500"
              >
                {{ errors.city }}
              </span>
            </label>
            <label class="block">
              <span class="mb-1 block text-xs text-gray-500">区县</span>
              <input
                v-model="form.district"
                :class="fieldClass('district')"
                placeholder="区"
              >
              <span
                v-if="errors.district"
                class="mt-1 block text-xs text-red-500"
              >
                {{ errors.district }}
              </span>
            </label>
          </div>

          <label class="block">
            <span class="mb-1 block text-xs text-gray-500">详细地址</span>
            <textarea
              v-model="form.addressDetail"
              :class="[fieldClass('addressDetail'), 'min-h-20 resize-none']"
              placeholder="街道、小区、楼栋门牌号"
            />
            <span
              v-if="errors.addressDetail"
              class="mt-1 block text-xs text-red-500"
            >
              {{ errors.addressDetail }}
            </span>
          </label>

          <div class="grid grid-cols-2 gap-3">
            <label class="block">
              <span class="mb-1 block text-xs text-gray-500">邮编</span>
              <input
                v-model="form.postalCode"
                :class="fieldClass('postalCode')"
                inputmode="numeric"
                placeholder="选填"
              >
            </label>
            <label class="block">
              <span class="mb-1 block text-xs text-gray-500">修改原因</span>
              <input
                v-model="form.changeReason"
                :class="fieldClass('changeReason')"
                placeholder="选填"
              >
            </label>
          </div>
        </div>

        <div class="sticky bottom-0 flex gap-2 border-t border-gray-100 bg-white px-4 py-3">
          <button
            type="button"
            class="h-10 flex-1 rounded-xl border border-gray-300 text-sm text-gray-700 disabled:opacity-50"
            :disabled="submitting"
            @click="emit('close')"
          >
            稍后填写
          </button>
          <button
            type="button"
            class="h-10 flex-1 rounded-xl bg-primary text-sm font-medium text-white disabled:opacity-50"
            :disabled="submitting"
            @click="handleSubmit"
          >
            {{ submitting ? '提交中…' : '确认提交' }}
          </button>
        </div>
      </section>
    </div>
  </Teleport>
</template>
