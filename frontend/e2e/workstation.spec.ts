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

test.beforeEach(async ({ page }) => {
  await page.route('**/api/workbench/me', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json; charset=utf-8',
      body: JSON.stringify(apiResponse({
        operatorId: 'agent001',
        principalType: 'AGENT',
        roles: ['AGENT'],
        authSource: 'DEV_HEADER',
      })),
    })
  })
})

test('workstation renders notification events', async ({ page }) => {
  await page.route('**/api/workbench/notifications/outbox/summary', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json; charset=utf-8',
      body: JSON.stringify(apiResponse({
        enabled: true,
        notificationEnabled: true,
        userMessageDeliveryMode: 'DIRECT',
        pending: 4,
        retryableFailed: 2,
        exhaustedFailed: 1,
        sent: 12,
        due: 3,
        leased: 1,
        oldestDueAt: '2026-07-10T11:50:00Z',
      })),
    })
  })

  await page.route('**/api/notifications/events/delivery-summary', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json; charset=utf-8',
      body: JSON.stringify(apiResponse({
        enabled: true,
        userSessionChannelEnabled: true,
        accepted: 2,
        retryableFailed: 1,
        exhaustedFailed: 0,
        delivered: 9,
        due: 1,
        leased: 1,
        oldestDueAt: '2026-07-10T11:55:00Z',
      })),
    })
  })

  await page.route('**/api/notifications/events?**', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json; charset=utf-8',
      body: JSON.stringify(apiResponse({
        records: [
          {
            eventId: 'ntf_e2e_failed',
            traceId,
            sourceService: 'smartcs-workbench',
            eventType: 'TAKEOVER_MESSAGE_SENT',
            recipientUserId: 'u1001',
            sessionId: 's_e2e_takeover',
            ticketId: 'wo_e2e_takeover',
            operatorId: 'agent001',
            channel: 'USER_SESSION',
            title: '人工消息已发送',
            content: '我正在帮你核实订单状态',
            payload: { takeoverStatus: 'IN_PROGRESS' },
            status: 'FAILED',
            retryCount: 2,
            lastError: '站内信通道暂不可用',
            nextRetryAt: '2026-07-10T13:10:00Z',
            deliveredAt: null,
            occurredAt: '2026-07-10T12:00:00Z',
            acceptedAt: '2026-07-10T12:00:01Z',
            createdAt: '2026-07-10T12:00:01Z',
            updatedAt: '2026-07-10T12:00:01Z',
          },
        ],
        total: 1,
        pageNo: 1,
        pageSize: 20,
      })),
    })
  })

  await page.goto('/notifications/events')

  await expect(page.getByText('通知事件').first()).toBeVisible()
  await expect(page.getByText('通知运行状态')).toBeVisible()
  await expect(page.getByText('Workbench Outbox')).toBeVisible()
  await expect(page.getByText('Notification Delivery')).toBeVisible()
  await expect(page.getByTestId('cutover-state')).toHaveText('存在重试耗尽')
  await expect(page.getByText('Notification 已开启')).toBeVisible()
  await expect(page.getByText('USER_SESSION 已开启')).toBeVisible()
  await expect(page.getByTestId('outbox-due')).toHaveText('3')
  await expect(page.getByTestId('outbox-exhausted')).toHaveText('1')
  await expect(page.getByTestId('delivery-completed')).toHaveText('9')
  await expect(page.getByText('ntf_e2e_failed')).toBeVisible()
  await expect(page.getByText('TAKEOVER_MESSAGE_SENT')).toBeVisible()
  await expect(page.getByText('投递失败')).toBeVisible()
  await expect(page.getByText('站内信通道暂不可用')).toBeVisible()
  await expect(page.getByRole('cell', { name: '2', exact: true })).toBeVisible()
})

test('workstation renders ticket list and can claim a pending ticket', async ({ page }) => {
  let claimCalled = false

  await page.route('**/api/workbench/tickets/stats', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json; charset=utf-8',
      body: JSON.stringify(apiResponse({
        total: 12,
        pending: 4,
        processing: 3,
        completed: 5,
        overdueRisk: 1,
      })),
    })
  })

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
  await expect(page.getByText('总工单')).toBeVisible()
  await expect(page.getByText('待处理').first()).toBeVisible()
  await expect(page.getByText('处理中').first()).toBeVisible()
  await expect(page.getByText('已完成').first()).toBeVisible()
  await expect(page.getByText('超时风险')).toBeVisible()
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
      requestPayload: {
        businessType: 'REFUND',
        orderNo: 'E2E-ORDER-1002',
        refundReason: '商品质量问题',
        refundAmount: 99.5,
        evidencePlaceholders: [
          {
            type: 'IMAGE',
            label: '退款凭证',
            required: false,
            status: 'NOT_PROVIDED',
          },
        ],
        userRequest: '订单 E2E-ORDER-1002 商品破损，我要退款 99.5 元',
        mock: true,
      },
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

  await expect(page.getByText('退款申请')).toBeVisible()
  await expect(page.getByText('E2E-ORDER-1002', { exact: true })).toBeVisible()
  await expect(page.getByText('商品质量问题')).toBeVisible()
  await expect(page.getByText('99.5 元', { exact: true })).toBeVisible()
  await expect(page.getByText('退款凭证（选填，NOT_PROVIDED）')).toBeVisible()
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

test('workstation ticket detail renders exchange request context', async ({ page }) => {
  const ticketDetail = {
    ticket: {
      ticketId: 'wo_e2e_exchange',
      traceId,
      sessionId: 's_e2e_exchange',
      userId: 'u1001',
      intent: 'exchange.apply',
      riskLevel: 'L3',
      routeDecision: 'HUMAN_REVIEW',
      status: 'PROCESSING',
      priority: 'HIGH',
      assignedAgent: 'agent001',
      reason: '换货申请需要人工审核',
      contextSnapshot: {},
      resolution: {},
      slaDeadline: null,
      createdAt: '2026-06-25T10:00:00Z',
      updatedAt: '2026-06-25T10:00:00Z',
      resolvedAt: null,
    },
    approval: {
      approvalId: 'ap_e2e_exchange',
      ticketId: 'wo_e2e_exchange',
      traceId,
      sessionId: 's_e2e_exchange',
      userId: 'u1001',
      intent: 'exchange.apply',
      approvalType: 'EXCHANGE',
      riskLevel: 'L3',
      routeDecision: 'HUMAN_REVIEW',
      status: 'CLAIMED',
      priority: 'HIGH',
      assignedReviewer: 'agent001',
      riskReason: '换货需人工审核',
      requestPayload: {
        businessType: 'EXCHANGE',
        orderNo: 'E2E-ORDER-2002',
        productName: '运动鞋',
        exchangeReason: '尺码不合适',
        expectedHandling: '更换尺码',
        evidencePlaceholders: [
          {
            type: 'IMAGE',
            label: '换货凭证',
            required: false,
            status: 'NOT_PROVIDED',
          },
        ],
        userRequest: '订单 E2E-ORDER-2002 商品 运动鞋 尺码小了，想换大一码',
        mock: true,
      },
      contextSnapshot: {},
      approvalResult: {},
      expireAt: null,
      createdAt: '2026-06-25T10:00:00Z',
      updatedAt: '2026-06-25T10:00:00Z',
      completedAt: null,
    },
    takeover: null,
    messages: [],
    actions: [],
  }

  await page.route('**/api/workbench/tickets/wo_e2e_exchange', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json; charset=utf-8',
      body: JSON.stringify(apiResponse(ticketDetail)),
    })
  })

  await page.goto('/tickets/wo_e2e_exchange')

  await expect(page.getByText('换货申请', { exact: true })).toBeVisible()
  await expect(page.getByText('E2E-ORDER-2002', { exact: true })).toBeVisible()
  await expect(page.getByText('运动鞋', { exact: true })).toBeVisible()
  await expect(page.getByText('尺码不合适')).toBeVisible()
  await expect(page.getByText('更换尺码')).toBeVisible()
  await expect(page.getByText('换货凭证（选填，NOT_PROVIDED）')).toBeVisible()
})

test('workstation ticket detail can request materials and transfer approval to takeover', async ({ page }) => {
  let materialCalled = false
  let transferCalled = false
  let phase: 'open' | 'materials' | 'transfer' = 'open'

  const ticketDetail = () => ({
    ticket: {
      ticketId: 'wo_e2e_decision',
      traceId,
      sessionId: 's_e2e_decision',
      userId: 'u1001',
      intent: 'refund.apply',
      riskLevel: 'L3',
      routeDecision: 'HUMAN_REVIEW',
      status: phase === 'transfer' ? 'ESCALATED' : 'PROCESSING',
      priority: 'HIGH',
      assignedAgent: 'agent001',
      reason: '退款申请需要人工审核',
      contextSnapshot: {},
      resolution: {},
      slaDeadline: null,
      createdAt: '2026-06-25T10:00:00Z',
      updatedAt: '2026-06-25T10:00:00Z',
      resolvedAt: null,
    },
    approval: {
      approvalId: 'ap_e2e_decision',
      ticketId: 'wo_e2e_decision',
      traceId,
      sessionId: 's_e2e_decision',
      userId: 'u1001',
      intent: 'refund.apply',
      approvalType: 'REFUND',
      riskLevel: 'L3',
      routeDecision: 'HUMAN_REVIEW',
      status: phase === 'transfer' ? 'ESCALATED' : 'CLAIMED',
      priority: 'HIGH',
      assignedReviewer: 'agent001',
      riskReason: '退款需人工审核',
      requestPayload: {
        businessType: 'REFUND',
        orderNo: 'E2E-ORDER-3003',
        refundReason: '商品质量问题',
        evidencePlaceholders: [],
        userRequest: '我要退款，需要人工审核',
      },
      contextSnapshot: {},
      approvalResult:
        phase === 'materials'
          ? {
            decisionType: 'REQUEST_MATERIALS',
            comment: '请上传破损照片',
          }
          : phase === 'transfer'
            ? {
              decisionType: 'TRANSFER_TAKEOVER',
              comment: '转人工继续处理',
            }
            : {},
      expireAt: null,
      createdAt: '2026-06-25T10:00:00Z',
      updatedAt: '2026-06-25T10:00:00Z',
      completedAt: phase === 'transfer' ? '2026-06-25T10:10:00Z' : null,
    },
    takeover:
      phase === 'transfer'
        ? {
          takeoverId: 'ht_e2e_decision',
          ticketId: 'wo_e2e_decision',
          traceId,
          sessionId: 's_e2e_decision',
          userId: 'u1001',
          triggerSource: 'HUMAN_ASSIGNMENT',
          status: 'ASSIGNED',
          priority: 'HIGH',
          assignedAgent: 'agent001',
          reason: '转人工继续处理',
          contextSnapshot: {},
          startedAt: null,
          endedAt: null,
          createdAt: '2026-06-25T10:10:00Z',
          updatedAt: '2026-06-25T10:10:00Z',
        }
        : null,
    messages: [],
    actions: [],
  })

  await page.route('**/api/workbench/tickets/wo_e2e_decision', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json; charset=utf-8',
      body: JSON.stringify(apiResponse(ticketDetail())),
    })
  })

  await page.route('**/api/workbench/tickets/wo_e2e_decision/approval/request-materials', async (route) => {
    const body = route.request().postDataJSON() as Record<string, unknown>
    expect(body.decisionType).toBe('REQUEST_MATERIALS')
    expect(body.comment).toBe('请上传破损照片')
    materialCalled = true
    phase = 'materials'

    await route.fulfill({
      status: 200,
      contentType: 'application/json; charset=utf-8',
      body: JSON.stringify(apiResponse({
        ticketId: 'wo_e2e_decision',
        workOrderStatus: 'PROCESSING',
        approvalStatus: 'CLAIMED',
        takeoverStatus: null,
        message: '已要求用户补充材料',
      })),
    })
  })

  await page.route('**/api/workbench/tickets/wo_e2e_decision/approval/transfer-takeover', async (route) => {
    const body = route.request().postDataJSON() as Record<string, unknown>
    expect(body.decisionType).toBe('TRANSFER_TAKEOVER')
    expect(body.comment).toBe('转人工继续处理')
    transferCalled = true
    phase = 'transfer'

    await route.fulfill({
      status: 200,
      contentType: 'application/json; charset=utf-8',
      body: JSON.stringify(apiResponse({
        ticketId: 'wo_e2e_decision',
        workOrderStatus: 'ESCALATED',
        approvalStatus: 'ESCALATED',
        takeoverStatus: 'ASSIGNED',
        message: '已转人工接管',
      })),
    })
  })

  await page.goto('/tickets/wo_e2e_decision')

  await page.getByRole('button', { name: '要求补充材料' }).click()
  await page.getByPlaceholder('备注（可选）').fill('请上传破损照片')
  await page.locator('.ant-modal-footer .ant-btn-primary').click()

  await expect.poll(() => materialCalled).toBe(true)
  await expect(page.getByText('审批结论')).toBeVisible()
  await expect(page.getByText('要求补充材料').first()).toBeVisible()
  await expect(page.getByText('请上传破损照片')).toBeVisible()

  await page.getByRole('button', { name: '转人工接管' }).click()
  await page.getByPlaceholder('备注（可选）').fill('转人工继续处理')
  await page.locator('.ant-modal-footer .ant-btn-primary').click()

  await expect.poll(() => transferCalled).toBe(true)
  await expect(page.getByText('转人工接管').first()).toBeVisible()
  await expect(page.getByRole('button', { name: '开始接管' })).toBeVisible()
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
