import { describe, expect, it } from "vitest";
import type { FaqItem } from "../../types/knowledge";
import {
  splitFaqKeywords,
  toFaqFormValues,
  toFaqUpsertRequest,
} from "./formUtils";

describe("FAQ form utilities", () => {
  it("splits supported keyword delimiters and removes blank values", () => {
    expect(splitFaqKeywords("退款， 到账;时效\n  ")).toEqual([
      "退款",
      "到账",
      "时效",
    ]);
  });

  it("uses documented defaults when creating a FAQ", () => {
    expect(toFaqFormValues()).toEqual({
      question: "",
      answer: "",
      keywordsText: "",
      category: "general",
      status: "ACTIVE",
      priority: 10,
    });
  });

  it("maps an existing FAQ into editable form values", () => {
    const record: FaqItem = {
      faqId: "faq_refund_arrival",
      question: "退款多久到账",
      answer: "以支付渠道实际到账时间为准。",
      keywords: ["退款", "到账", "多久"],
      category: "after_sale",
      status: "DISABLED",
      priority: 100,
      createdAt: "2026-07-04T03:00:00Z",
      updatedAt: "2026-07-04T03:00:00Z",
    };

    expect(toFaqFormValues(record)).toEqual({
      question: "退款多久到账",
      answer: "以支付渠道实际到账时间为准。",
      keywordsText: "退款，到账，多久",
      category: "after_sale",
      status: "DISABLED",
      priority: 100,
    });
  });

  it("builds a trimmed request that only uses documented API fields", () => {
    expect(
      toFaqUpsertRequest({
        question: "  优惠券过期了还能用吗  ",
        answer: "  优惠券过期后通常不能继续使用。  ",
        keywordsText: "优惠券；过期, 活动",
        category: " promotion ",
        status: "ACTIVE",
        priority: 10,
      }),
    ).toEqual({
      question: "优惠券过期了还能用吗",
      answer: "优惠券过期后通常不能继续使用。",
      keywords: ["优惠券", "过期", "活动"],
      category: "promotion",
      status: "ACTIVE",
      priority: 10,
    });
  });
});
