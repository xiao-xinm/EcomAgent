package com.smartcs.agent.knowledge.service;

import com.smartcs.agent.knowledge.dto.FaqQueryDtos.FaqQueryRequest;
import com.smartcs.agent.knowledge.dto.FaqQueryDtos.FaqQueryResponse;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * 最小 FAQ 知识服务。
 *
 * 当前阶段只做内存关键词匹配，用来打通 Agent Core 到 Knowledge 的调用链。
 * 后续接入数据库、ES、Milvus 或 RAG 时，可以保持 Controller 契约不变，只替换这里的检索实现。
 */
@Service
public class FaqKnowledgeService {

    private static final String SOURCE = "faq-keyword-v1";

    private static final List<FaqEntry> FAQS = List.of(
            new FaqEntry(
                    "退款多久到账",
                    "退款到账时间取决于支付渠道。一般情况下，平台审核通过后会在 1-3 个工作日内原路退回；银行卡或部分第三方渠道可能需要 3-7 个工作日。实际结果以坐席审核和支付渠道回执为准。",
                    List.of("退款", "到账", "多久", "几天", "退钱")),
            new FaqEntry(
                    "退货规则",
                    "普通商品支持在售后期内发起退货申请。退款、退货、换货属于敏感售后操作，系统会先创建人工审核工单，由坐席确认订单状态、商品状态和售后原因后再处理。",
                    List.of("退货", "规则", "政策", "售后", "条件")),
            new FaqEntry(
                    "换货规则",
                    "换货申请需要人工确认库存、订单状态和商品状态。你可以先描述换货原因，系统会创建人工审核工单，坐席审核通过后再推进后续处理。",
                    List.of("换货", "规则", "政策", "售后", "条件")),
            new FaqEntry(
                    "修改地址规则",
                    "订单未发货前通常可以修改收货地址；已出库、运输中或已签收的订单需要结合物流状态判断。系统会在自动执行前要求你确认完整的新地址信息。",
                    List.of("地址", "收货地址", "修改", "规则", "能不能")),
            new FaqEntry(
                    "发票怎么开",
                    "发票通常需要在订单完成后申请。请准备订单号、发票抬头、税号和接收邮箱；后续可接入订单服务后自动查询可开票订单。",
                    List.of("发票", "开票", "抬头", "税号")),
            new FaqEntry(
                    "运费规则",
                    "运费会根据商品、地址、活动和配送方式计算。当前客服系统先提供规则说明；真实运费查询后续会接入订单和物流能力。",
                    List.of("运费", "配送费", "邮费", "怎么算", "规则")),
            new FaqEntry(
                    "价格保护规则",
                    "如商品支持价格保护，通常需要在价保期内提交申请，并以订单实付金额、活动规则和商品当前价格为准。具体是否可保价需要人工或后续业务系统确认。",
                    List.of("保价", "价保", "价格保护", "降价")));

    public FaqQueryResponse query(FaqQueryRequest request) {
        String question = textOr(request.question(), "");
        String normalizedQuestion = normalize(question);
        FaqMatch bestMatch = FAQS.stream()
                .map(entry -> match(entry, normalizedQuestion))
                .max(Comparator.comparingInt(FaqMatch::score))
                .orElse(FaqMatch.unmatched());

        if (bestMatch.score() <= 0) {
            return new FaqQueryResponse(
                    "faq_" + UUID.randomUUID(),
                    question,
                    "这个问题我暂时没有在 FAQ 中找到稳定答案。你可以换一种说法继续问，或发送“人工客服”转坐席处理。",
                    false,
                    0.0D,
                    List.of(),
                    SOURCE,
                    Instant.now());
        }

        double confidence = Math.min(0.95D, 0.58D + bestMatch.matchedKeywords().size() * 0.10D);
        return new FaqQueryResponse(
                "faq_" + UUID.randomUUID(),
                question,
                bestMatch.entry().answer(),
                true,
                confidence,
                bestMatch.matchedKeywords(),
                SOURCE,
                Instant.now());
    }

    private FaqMatch match(FaqEntry entry, String normalizedQuestion) {
        List<String> matchedKeywords = entry.keywords().stream()
                .map(this::normalize)
                .filter(keyword -> !keyword.isBlank())
                .filter(normalizedQuestion::contains)
                .toList();
        int score = matchedKeywords.stream().mapToInt(String::length).sum() + matchedKeywords.size() * 2;
        return new FaqMatch(entry, matchedKeywords, score);
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase().replaceAll("\\s+", "");
    }

    private String textOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private record FaqEntry(String title, String answer, List<String> keywords) {
    }

    private record FaqMatch(FaqEntry entry, List<String> matchedKeywords, int score) {

        private static FaqMatch unmatched() {
            return new FaqMatch(null, List.of(), 0);
        }
    }
}
