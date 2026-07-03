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
  await expect(page.getByText('wo_e2e_refund')).toBeVisible()
  await expect(page.getByText('order.refund')).toBeVisible()
  await expect(page.getByText('人工审核')).toBeVisible()

  await page.getByRole('button', { name: '领取' }).click()
  await expect.poll(() => claimCalled).toBe(true)
})
