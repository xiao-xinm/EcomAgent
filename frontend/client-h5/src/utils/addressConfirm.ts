import type { QuickAction } from '@/types/api'

export interface AddressConfirmForm {
  orderNo: string
  consigneeName: string
  consigneePhone: string
  province: string
  city: string
  district: string
  addressDetail: string
  postalCode: string
  changeReason: string
}

export interface AddressConfirmPayload {
  orderNo: string
  newAddress: {
    consigneeName: string
    consigneePhone: string
    province: string
    city: string
    district: string
    addressDetail: string
    postalCode?: string
  }
  changeReason?: string
}

export type AddressConfirmErrors = Partial<Record<keyof AddressConfirmForm, string>>

export function emptyAddressConfirmForm(
  initial: Partial<AddressConfirmForm> = {},
): AddressConfirmForm {
  return {
    orderNo: initial.orderNo || '',
    consigneeName: initial.consigneeName || '',
    consigneePhone: initial.consigneePhone || '',
    province: initial.province || '',
    city: initial.city || '',
    district: initial.district || '',
    addressDetail: initial.addressDetail || '',
    postalCode: initial.postalCode || '',
    changeReason: initial.changeReason || '',
  }
}

export function trimAddressConfirmForm(form: AddressConfirmForm): AddressConfirmForm {
  return {
    orderNo: form.orderNo.trim(),
    consigneeName: form.consigneeName.trim(),
    consigneePhone: form.consigneePhone.trim(),
    province: form.province.trim(),
    city: form.city.trim(),
    district: form.district.trim(),
    addressDetail: form.addressDetail.trim(),
    postalCode: form.postalCode.trim(),
    changeReason: form.changeReason.trim(),
  }
}

export function validateAddressConfirmForm(form: AddressConfirmForm): AddressConfirmErrors {
  const value = trimAddressConfirmForm(form)
  const errors: AddressConfirmErrors = {}

  if (!value.orderNo) errors.orderNo = '请输入订单号'
  if (!value.consigneeName) errors.consigneeName = '请输入收货人'
  if (!value.consigneePhone) {
    errors.consigneePhone = '请输入手机号'
  } else if (!/^1\d{10}$/.test(value.consigneePhone)) {
    errors.consigneePhone = '请输入 11 位手机号'
  }
  if (!value.province) errors.province = '请输入省份'
  if (!value.city) errors.city = '请输入城市'
  if (!value.district) errors.district = '请输入区县'
  if (!value.addressDetail) errors.addressDetail = '请输入详细地址'

  return errors
}

export function hasAddressConfirmErrors(errors: AddressConfirmErrors): boolean {
  return Object.keys(errors).length > 0
}

export function buildAddressConfirmPayload(form: AddressConfirmForm): AddressConfirmPayload {
  const value = trimAddressConfirmForm(form)
  const payload: AddressConfirmPayload = {
    orderNo: value.orderNo,
    newAddress: {
      consigneeName: value.consigneeName,
      consigneePhone: value.consigneePhone,
      province: value.province,
      city: value.city,
      district: value.district,
      addressDetail: value.addressDetail,
    },
  }

  if (value.postalCode) {
    payload.newAddress.postalCode = value.postalCode
  }
  if (value.changeReason) {
    payload.changeReason = value.changeReason
  }

  return payload
}

export function extractOrderNoFromText(text?: string | null): string {
  const matcher = (text || '').match(/\b[A-Z0-9]+(?:-[A-Z0-9]+)+\b/i)
  return matcher?.[0] || ''
}

export function isConfirmAction(action: QuickAction): boolean {
  const actionType = (action.actionType || action.value || '').toUpperCase()
  return actionType === 'CONFIRM'
}

export function isModifyAddressIntent(intent?: string | null): boolean {
  return intent === 'order.modify_address'
}
