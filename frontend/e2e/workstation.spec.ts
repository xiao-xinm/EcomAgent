import { expect, test } from '@playwright/test'

const traceId = 'trace_e2e_workstation'

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

test('workstation renders ticket list and can claim a pending ticket', async ({ page }) => {
  let claimCalled = false

  await page.route('**/api/workbench/tickets?**', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json; charset=utf-8',
      body: JSON.stringify(apiResponse({
        records: [
          {
            ticketId: 'wo_e2e_refund',
            traceId,
            sessionId: 's_e2e_refund',
            userId: 'u1001',
            intent: 'order.refund',
            riskLevel: 'L3',
            routeDecision: 'HUMAN_REVIEW',
            status: 'PENDING',
            priority: 'HIGH',
            assignedAgent: null,
            reason: '用户申请退款',
            slaDeadline: null,
            createdAt: '2026-06-25T10:00:00Z',
            updatedAt: '2026-06-25T10:00:00Z',
            approvalId: 'ap_e2e_refund',
            approvalType: 'REFUND',
            approvalStatus: 'PENDING',
            takeoverId: null,
            takeoverStatus: null,
          },
        ],
        total: 1,
        pageNo: 1,
        pageSize: 20,
      })),
    })
  })

  await page.route('**/api/workbench/tickets/wo_e2e_refund/claim', async (route) => {
    const body = route.request().postDataJSON() as Record<string, unknown>
    expect(body.operatorId).toBeTruthy()
    claimCalled = true

    await route.fulfill({
      status: 200,
      contentType: 'application/json; charset=utf-8',
      body: JSON.stringify(apiResponse({
        ticketId: 'wo_e2e_refund',
        workOrderStatus: 'ASSIGNED',
        approvalStatus: 'CLAIMED',
        takeoverStatus: null,
        message: '领取成功',
      })),
    })
  })

  await page.goto('/tickets')

  await expect(page.getByText('人工坐席工作台')).toBeVisible()
  await expect(page.getByRole('main').getByText('工单列表')).toBeVisible()
  await expect(page.getByText('风险等级').first()).toBeVisible()
  await expect(page.getByText('优先级').first()).toBeVisible()
  await expect(page.getByText('坐席').first()).toBeVisible()
  await expect(page.getByText('wo_e2e_refund')).toBeVisible()
  await expect(page.getByText('order.refund')).toBeVisible()
  await expect(page.getByText('人工审核')).toBeVisible()

  await page.getByRole('button', { name: '领取' }).click()
  await expect.poll(() => claimCalled).toBe(true)
})

test('workstation ticket detail can add an internal note', async ({ page }) => {
  let noteCalled = false
  let noteCreated = false

  const ticketDetail = () => ({
    ticket: {
      ticketId: 'wo_e2e_refund',
      traceId,
      sessionId: 's_e2e_refund',
      userId: 'u1001',
      intent: 'refund.apply',
      riskLevel: 'L3',
      routeDecision: 'HUMAN_REVIEW',
      status: 'PROCESSING',
      priority: 'HIGH',
      assignedAgent: 'agent001',
      reason: '用户申请退款',
      contextSnapshot: {},
      resolution: {},
      slaDeadline: null,
      createdAt: '2026-06-25T10:00:00Z',
      updatedAt: '2026-06-25T10:00:00Z',
      resolvedAt: null,
    },
    approval: {
      approvalId: 'ap_e2e_refund',
      ticketId: 'wo_e2e_refund',
      traceId,
      sessionId: 's_e2e_refund',
      userId: 'u1001',
      intent: 'refund.apply',
      approvalType: 'REFUND',
      riskLevel: 'L3',
      routeDecision: 'HUMAN_REVIEW',
      status: 'CLAIMED',
      priority: 'HIGH',
      assignedReviewer: 'agent001',
      riskReason: '退款需人工审核',
      requestPayload: {},
      contextSnapshot: {},
      approvalResult: {},
      expireAt: null,
      createdAt: '2026-06-25T10:00:00Z',
      updatedAt: '2026-06-25T10:00:00Z',
      completedAt: null,
    },
    takeover: null,
    messages: [],
    actions: [
      {
        actionId: 'wa_claim',
        source: 'WORK_ORDER',
        ticketId: 'wo_e2e_refund',
        traceId,
        operatorId: 'agent001',
        actionType: 'ASSIGN',
        beforeStatus: 'PENDING',
        afterStatus: 'PROCESSING',
        comment: '开始处理',
        actionData: { beforeStatus: 'PENDING', afterStatus: 'PROCESSING' },
        createdAt: '2026-06-25T10:01:00Z',
      },
      ...(noteCreated
        ? [
          {
            actionId: 'wa_note',
            source: 'WORK_ORDER',
            ticketId: 'wo_e2e_refund',
            traceId,
            operatorId: 'agent001',
            actionType: 'INTERNAL_NOTE',
            beforeStatus: null,
            afterStatus: null,
            comment: '需要主管复核退款凭证',
            actionData: { noteType: 'INTERNAL' },
            createdAt: '2026-06-25T10:05:00Z',
          },
        ]
        : []),
    ],
  })

  await page.route('**/api/workbench/tickets/wo_e2e_refund', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json; charset=utf-8',
      body: JSON.stringify(apiResponse(ticketDetail())),
    })
  })

  await page.route('**/api/workbench/tickets/wo_e2e_refund/notes', async (route) => {
    const body = route.request().postDataJSON() as Record<string, unknown>
    expect(body.operatorId).toBeTruthy()
    expect(body.comment).toBe('需要主管复核退款凭证')
    noteCalled = true
    noteCreated = true

    await route.fulfill({
      status: 200,
      contentType: 'application/json; charset=utf-8',
      body: JSON.stringify(apiResponse({
        ticketId: 'wo_e2e_refund',
        workOrderStatus: 'PROCESSING',
        approvalStatus: 'CLAIMED',
        takeoverStatus: null,
        message: '内部备注已记录',
      })),
    })
  })

  await page.goto('/tickets/wo_e2e_refund')

  await expect(page.getByText('内部协作')).toBeVisible()
  await page.getByPlaceholder('记录仅坐席可见的处理备注、协作信息或后续跟进点').fill('需要主管复核退款凭证')
  await page.getByRole('button', { name: '添加内部备注' }).click()

  await expect.poll(() => noteCalled).toBe(true)
  await expect(page.getByText('审计时间线')).toBeVisible()
  await expect(page.getByText('工单').first()).toBeVisible()
  await expect(page.getByText('领取工单')).toBeVisible()
  await expect(page.getByText('PENDING → PROCESSING')).toBeVisible()
  await expect(page.getByText('内部备注').first()).toBeVisible()
  await expect(page.getByText('需要主管复核退款凭证').first()).toBeVisible()
  await page.getByText('查看动作数据').nth(1).click()
  await expect(page.getByText(/"noteType":\s+"INTERNAL"/)).toBeVisible()
})

test('workstation ticket detail can send takeover message to user', async ({ page }) => {
  let messageCalled = false
  let messageCreated = false

  const ticketDetail = () => ({
    ticket: {
      ticketId: 'wo_e2e_takeover',
      traceId,
      sessionId: 's_e2e_takeover',
      userId: 'u1001',
      intent: 'human.takeover',
      riskLevel: 'L3',
      routeDecision: 'HUMAN_TAKEOVER',
      status: 'PROCESSING',
      priority: 'HIGH',
      assignedAgent: 'agent001',
      reason: '用户要求人工客服',
      contextSnapshot: {},
      resolution: {},
      slaDeadline: null,
      createdAt: '2026-06-25T11:00:00Z',
      updatedAt: '2026-06-25T11:00:00Z',
      resolvedAt: null,
    },
    approval: null,
    takeover: {
      takeoverId: 'ht_e2e_takeover',
      ticketId: 'wo_e2e_takeover',
      traceId,
      sessionId: 's_e2e_takeover',
      userId: 'u1001',
      triggerSource: 'USER_REQUEST',
      status: 'IN_PROGRESS',
      priority: 'HIGH',
      assignedAgent: 'agent001',
      reason: '用户要求人工客服',
      contextSnapshot: {},
      startedAt: '2026-06-25T11:01:00Z',
      endedAt: null,
      createdAt: '2026-06-25T11:00:00Z',
      updatedAt: '2026-06-25T11:01:00Z',
    },
    messages: messageCreated
      ? [
          {
            messageId: 'm_takeover_reply',
            traceId,
            sessionId: 's_e2e_takeover',
            userId: 'u1001',
            role: 'HUMAN_AGENT',
            messageType: 'TEXT',
            content: '我正在帮你核实订单状态',
            quickActions: [],
            intent: 'human.takeover',
            riskLevel: 'L3',
            routeDecision: 'HUMAN_TAKEOVER',
            metadata: { source: 'workbench' },
            createdAt: '2026-06-25T11:02:00Z',
          },
        ]
      : [],
    actions: messageCreated
      ? [
          {
            actionId: 'wa_takeover_message',
            source: 'WORK_ORDER',
            ticketId: 'wo_e2e_takeover',
            traceId,
            operatorId: 'agent001',
            actionType: 'TAKEOVER',
            beforeStatus: null,
            afterStatus: null,
            comment: '我正在帮你核实订单状态',
            actionData: { subAction: 'MESSAGE_SENT' },
            createdAt: '2026-06-25T11:02:00Z',
          },
        ]
      : [],
  })

  await page.route('**/api/workbench/tickets/wo_e2e_takeover', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json; charset=utf-8',
      body: JSON.stringify(apiResponse(ticketDetail())),
    })
  })

  await page.route('**/api/workbench/tickets/wo_e2e_takeover/takeover/messages', async (route) => {
    const body = route.request().postDataJSON() as Record<string, unknown>
    expect(body.operatorId).toBeTruthy()
    expect(body.content).toBe('我正在帮你核实订单状态')
    messageCalled = true
    messageCreated = true

    await route.fulfill({
      status: 200,
      contentType: 'application/json; charset=utf-8',
      body: JSON.stringify(apiResponse({
        ticketId: 'wo_e2e_takeover',
        workOrderStatus: 'PROCESSING',
        approvalStatus: null,
        takeoverStatus: 'IN_PROGRESS',
        message: '人工消息已发送',
      })),
    })
  })

  await page.goto('/tickets/wo_e2e_takeover')

  await expect(page.getByText('人工消息')).toBeVisible()
  await page.getByPlaceholder('输入要发送给用户的人工客服消息').fill('我正在帮你核实订单状态')
  await page.getByRole('button', { name: '发送给用户' }).click()

  await expect.poll(() => messageCalled).toBe(true)
  await expect(page.getByText('发送人工消息')).toBeVisible()
  await expect(page.getByText('我正在帮你核实订单状态').first()).toBeVisible()
})
