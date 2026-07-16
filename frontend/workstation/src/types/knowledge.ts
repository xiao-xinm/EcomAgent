import type { PageResult } from "./workbench";

// Types derived from docs/knowledge-api.md.
// Keep this file aligned with the documented FAQ management contract.

export type FaqStatus = "DRAFT" | "ACTIVE" | "DISABLED";

export interface FaqItem {
  faqId: string;
  question: string;
  answer: string;
  keywords: string[];
  category: string;
  status: FaqStatus;
  priority: number;
  createdAt: string;
  updatedAt: string;
}

export interface FaqQueryParams {
  pageNo?: number;
  pageSize?: number;
  status?: FaqStatus;
  keyword?: string;
}

export interface FaqUpsertRequest {
  question: string;
  answer: string;
  keywords: string[];
  category: string;
  status: FaqStatus;
  priority: number;
}

export interface FaqStatusRequest {
  status: FaqStatus;
}

export interface IndexRepairRequest {
  faqIds?: string[];
}

export interface IndexOperationResult {
  writer: string;
  success: boolean;
  message: string;
}

export interface IndexSyncSummary {
  enabled: boolean;
  documentCount: number;
  successCount: number;
  failureCount: number;
  operations: IndexOperationResult[];
}

export type FaqPageResult = PageResult<FaqItem>;
