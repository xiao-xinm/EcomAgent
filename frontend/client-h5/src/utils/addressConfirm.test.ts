import { describe, expect, it } from 'vitest'
import {
  buildAddressConfirmPayload,
  emptyAddressConfirmForm,
  extractOrderNoFromText,
  hasAddressConfirmErrors,
  isConfirmAction,
  isModifyAddressIntent,
  validateAddressConfirmForm,
} from './addressConfirm'

describe('addressConfirm utils', () => {
  it('builds the backend confirmation payload with trimmed fields', () => {
    const payload = buildAddressConfirmPayload({
      orderNo: ' E2E-ORDER-1002 ',
      consigneeName: ' 测试用户A ',
      consigneePhone: '13800008888',
      province: ' 上海市 ',
      city: ' 上海市 ',
      district: ' 浦东新区 ',
      addressDetail: ' 联调路 8888 号 ',
      postalCode: ' 200120 ',
      changeReason: ' 用户确认修改 ',
    })

    expect(payload).toEqual({
      orderNo: 'E2E-ORDER-1002',
      newAddress: {
        consigneeName: '测试用户A',
        consigneePhone: '13800008888',
        province: '上海市',
        city: '上海市',
        district: '浦东新区',
        addressDetail: '联调路 8888 号',
        postalCode: '200120',
      },
      changeReason: '用户确认修改',
    })
  })

  it('validates required fields and phone format', () => {
    const errors = validateAddressConfirmForm(
      emptyAddressConfirmForm({
        orderNo: 'E2E-ORDER-1002',
        consigneePhone: '123',
      }),
    )

    expect(hasAddressConfirmErrors(errors)).toBe(true)
    expect(errors.consigneeName).toBe('请输入收货人')
    expect(errors.consigneePhone).toBe('请输入 11 位手机号')
    expect(errors.addressDetail).toBe('请输入详细地址')
  })

  it('extracts order number from user text', () => {
    expect(extractOrderNoFromText('我要修改订单 E2E-ORDER-1002 的收货地址')).toBe(
      'E2E-ORDER-1002',
    )
  })

  it('detects modify address confirmation context', () => {
    expect(isConfirmAction({ label: '确认继续', value: 'confirm', actionType: 'CONFIRM' })).toBe(
      true,
    )
    expect(isModifyAddressIntent('order.modify_address')).toBe(true)
    expect(isModifyAddressIntent('order.query')).toBe(false)
  })
})
