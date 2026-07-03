import { expect, test, type Route } from '@playwright/test'

type MessageView = {
  messageId: string
  traceId: string
  sessionId: string
  userId: string
  role: 'USER' | 'AGENT'
  messageType: 'TEXT' | 'ACTION'
  content: string
  attachments: unknown[]
  quickActions: unknown[]
  intent?: string
  riskLevel?: string
  routeDecision?: string
  metadata: Record<string, unknown>
  createdAt: string
}

const traceId = 'trace_e2e_client'

function apiResponse<T>(data: T) {
  return {
    code: '0000',
    message: 'success',
    data,
    traceId,
    metadata: {},
    timestamp: new Date().toISOString(),
  }
}

async function fulfillJson(route: Route, body: unknown) {
  await route.fulfill({
    status: 200,
    contentType: 'application/json; charset=utf-8',
    body: JSON.stringify(body),
  })
}

function userMessage(body: Record<string, unknown>, content: string, messageType: 'TEXT' | 'ACTION'): MessageView {
  return {
    messageId: `m_user_${Date.now()}`,
    traceId,
    sessionId: String(body.sessionId || 's_e2e_client'),
    userId: String(body.userId || 'u1001'),
    role: 'USER',
    messageType,
    content,
    attachments: [],
    quickActions: [],
    metadata: {},
    createdAt: new Date().toISOString(),
  }
}

test('client H5 renders order auto reply with mocked Gateway contract', async ({ page }) => {
  const remoteMessages: MessageView[] = []

  await page.route('**/api/chat/sessions/**/messages**', async (route) => {
    await fulfillJson(route, apiResponse(remoteMessages))
  })

  await page.route('**/api/chat/messages', async (route) => {
    const body = route.request().postDataJSON() as Record<string, unknown>
    expect(body.content).toBe('我的订单')

    remoteMessages.push(userMessage(body, '我的订单', 'TEXT'))

    const createdAt = new Date().toISOString()
    const reply = {
      replyId: 'r_e2e_order',
      traceId,
      sessionId: String(body.sessionId || 's_e2e_client'),
      messageType: 'TEXT',
      content: '订单 E2E-ORDER-1001 当前状态：已发货。',
      quickActions: [],
      riskLevel: 'L0',
      routeDecision: 'AUTO_REPLY',
      metadata: {
        intent: 'order.query',
        skillExecutionId: 'skill_e2e_order',
      },
      createdAt,
    }

    remoteMessages.push({
      messageId: reply.replyId,
      traceId,
      sessionId: reply.sessionId,
      userId: String(body.userId || 'u1001'),
      role: 'AGENT',
      messageType: 'TEXT',
      content: reply.content,
      attachments: [],
      quickActions: [],
      intent: 'order.query',
      riskLevel: 'L0',
      routeDecision: 'AUTO_REPLY',
      metadata: reply.metadata,
      createdAt,
    })

    await fulfillJson(route, apiResponse(reply))
  })

  await page.goto('/?sessionKey=e2e_client_order')

  await expect(page.getByRole('heading', { name: '智能客服' })).toBeVisible()
  await expect(page.getByText('你好！我是智能客服助手，有什么可以帮您的吗？')).toBeVisible()

  await page.getByPlaceholder('输入消息…').fill('我的订单')
  await page.getByRole('button', { name: '发送' }).click()

  await expect(page.getByText('我的订单')).toBeVisible()
  await expect(page.getByText('订单 E2E-ORDER-1001 当前状态：已发货。')).toBeVisible()
})

test('client H5 opens address confirm sheet and submits action payload', async ({ page }) => {
  const remoteMessages: MessageView[] = []
  let actionPayload: Record<string, unknown> | undefined

  await page.route('**/api/chat/sessions/**/messages**', async (route) => {
    await fulfillJson(route, apiResponse(remoteMessages))
  })

  await page.route('**/api/chat/messages', async (route) => {
    const body = route.request().postDataJSON() as Record<string, unknown>
    remoteMessages.push(userMessage(body, String(body.content), 'TEXT'))

    const createdAt = new Date().toISOString()
    const reply = {
      replyId: 'r_e2e_modify_address',
      traceId,
      sessionId: String(body.sessionId || 's_e2e_client'),
      messageType: 'TEXT',
      content: '修改地址需要你确认后继续。',
      quickActions: [
        {
          label: '确认继续',
          value: 'confirm_modify_address',
          actionType: 'CONFIRM',
          payload: { orderNo: 'E2E-ORDER-1002' },
        },
      ],
      riskLevel: 'L2',
      routeDecision: 'CONFIRM_BEFORE_EXECUTE',
      metadata: {
        intent: 'order.modify_address',
        orderNo: 'E2E-ORDER-1002',
      },
      createdAt,
    }

    remoteMessages.push({
      messageId: reply.replyId,
      traceId,
      sessionId: reply.sessionId,
      userId: String(body.userId || 'u1001'),
      role: 'AGENT',
      messageType: 'TEXT',
      content: reply.content,
      attachments: [],
      quickActions: reply.quickActions,
      intent: 'order.modify_address',
      riskLevel: 'L2',
      routeDecision: 'CONFIRM_BEFORE_EXECUTE',
      metadata: reply.metadata,
      createdAt,
    })

    await fulfillJson(route, apiResponse(reply))
  })

  await page.route('**/api/chat/actions', async (route) => {
    const body = route.request().postDataJSON() as Record<string, unknown>
    actionPayload = body.payload as Record<string, unknown>
    remoteMessages.push(userMessage(body, '确认修改地址', 'ACTION'))

    const createdAt = new Date().toISOString()
    const reply = {
      replyId: 'r_e2e_modify_address_done',
      traceId,
      sessionId: String(body.sessionId || 's_e2e_client'),
      messageType: 'TEXT',
      content: '已为你提交修改地址请求。',
      quickActions: [],
      riskLevel: 'L1',
      routeDecision: 'AUTO_EXECUTE',
      metadata: {
        intent: 'order.modify_address',
        skillExecutionId: 'skill_e2e_modify_address',
      },
      createdAt,
    }

    remoteMessages.push({
      messageId: reply.replyId,
      traceId,
      sessionId: reply.sessionId,
      userId: String(body.userId || 'u1001'),
      role: 'AGENT',
      messageType: 'TEXT',
      content: reply.content,
      attachments: [],
      quickActions: [],
      intent: 'order.modify_address',
      riskLevel: 'L1',
      routeDecision: 'AUTO_EXECUTE',
      metadata: reply.metadata,
      createdAt,
    })

    await fulfillJson(route, apiResponse(reply))
  })

  await page.goto('/?sessionKey=e2e_client_address')

  await page.getByPlaceholder('输入消息…').fill('我要修改订单 E2E-ORDER-1002 的收货地址')
  await page.getByRole('button', { name: '发送' }).click()
  await page.getByRole('button', { name: '确认继续' }).click()

  await expect(page.getByRole('heading', { name: '确认修改收货地址' })).toBeVisible()
  await expect(page.getByLabel('订单号')).toHaveValue('E2E-ORDER-1002')

  await page.getByLabel('收货人').fill('张三')
  await page.getByLabel('手机号').fill('13800138000')
  await page.getByLabel('省份').fill('浙江省')
  await page.getByLabel('城市').fill('杭州市')
  await page.getByLabel('区县').fill('西湖区')
  await page.getByLabel('详细地址').fill('文三路 100 号 1 幢 101 室')
  await page.getByLabel('邮编').fill('310000')
  await page.getByLabel('修改原因').fill('用户搬家')
  await page.getByRole('button', { name: '确认提交' }).click()

  await expect(page.getByText('已为你提交修改地址请求。')).toBeVisible()
  expect(actionPayload).toMatchObject({
    orderNo: 'E2E-ORDER-1002',
    changeReason: '用户搬家',
    newAddress: {
      consigneeName: '张三',
      consigneePhone: '13800138000',
      province: '浙江省',
      city: '杭州市',
      district: '西湖区',
      addressDetail: '文三路 100 号 1 幢 101 室',
      postalCode: '310000',
    },
  })
})
