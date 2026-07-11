package com.smartcs.agent.knowledge.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartcs.agent.common.dto.PageResult;
import com.smartcs.agent.knowledge.dto.FaqAdminDtos.FaqItem;
import com.smartcs.agent.knowledge.dto.FaqAdminDtos.FaqStatusRequest;
import com.smartcs.agent.knowledge.dto.FaqAdminDtos.FaqUpsertRequest;
import com.smartcs.agent.knowledge.dto.FaqQueryDtos.FaqQueryRequest;
import com.smartcs.agent.knowledge.dto.FaqQueryDtos.FaqQueryResponse;
import com.smartcs.agent.knowledge.indexing.KnowledgeIndexSynchronizer;
import com.smartcs.agent.knowledge.indexing.KnowledgeIndexSynchronizer.IndexSyncSummary;
import com.smartcs.agent.knowledge.retrieval.HybridKnowledgeRetriever;
import com.smartcs.agent.knowledge.retrieval.HybridKnowledgeRetriever.HybridRetrievalResult;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * FAQ 知识服务。
 *
 * <p>hybrid 模式优先使用 Elasticsearch + pgvector 融合结果；无候选或任一依赖异常时，
 * 继续读取 MySQL ACTIVE FAQ，最后回退内置 FAQ，保证用户聊天主链路稳定。
 */
@Service
public class FaqKnowledgeService {

    private static final Logger LOGGER = LoggerFactory.getLogger(FaqKnowledgeService.class);
    private static final String SOURCE = "faq-keyword-v1";
    private static final TypeReference<List<String>> KEYWORDS_TYPE = new TypeReference<>() {
    };

    private static final List<FaqEntry> BUILTIN_FAQS = List.of(
            new FaqEntry(
                    "faq_builtin_refund_arrival",
                    "退款多久到账",
                    "退款到账时间取决于支付渠道。一般情况下，平台审核通过后会在 1-3 个工作日内原路退回；银行卡或部分第三方渠道可能需要 3-7 个工作日。实际结果以坐席审核和支付渠道回执为准。",
                    List.of("退款", "到账", "多久", "几天", "退钱"),
                    100),
            new FaqEntry(
                    "faq_builtin_return_policy",
                    "退货规则",
                    "普通商品支持在售后期内发起退货申请。退款、退货、换货属于敏感售后操作，系统会先创建人工审核工单，由坐席确认订单状态、商品状态和售后原因后再处理。",
                    List.of("退货", "规则", "政策", "售后", "条件"),
                    90),
            new FaqEntry(
                    "faq_builtin_exchange_policy",
                    "换货规则",
                    "换货申请需要人工确认库存、订单状态和商品状态。你可以先描述换货原因，系统会创建人工审核工单，坐席审核通过后再推进后续处理。",
                    List.of("换货", "规则", "政策", "售后", "条件"),
                    80),
            new FaqEntry(
                    "faq_builtin_address_policy",
                    "修改地址规则",
                    "订单未发货前通常可以修改收货地址；已出库、运输中或已签收的订单需要结合物流状态判断。系统会在自动执行前要求你确认完整的新地址信息。",
                    List.of("地址", "收货地址", "修改", "规则", "能不能"),
                    70),
            new FaqEntry(
                    "faq_builtin_invoice",
                    "发票怎么开",
                    "发票通常需要在订单完成后申请。请准备订单号、发票抬头、税号和接收邮箱；后续接入订单服务后可自动查询可开票订单。",
                    List.of("发票", "开票", "抬头", "税号"),
                    60),
            new FaqEntry(
                    "faq_builtin_freight",
                    "运费规则",
                    "运费会根据商品、地址、活动和配送方式计算。当前客服系统先提供规则说明；真实运费查询后续会接入订单和物流能力。",
                    List.of("运费", "配送费", "邮费", "怎么算", "规则"),
                    50),
            new FaqEntry(
                    "faq_builtin_price_protection",
                    "价格保护规则",
                    "如商品支持价格保护，通常需要在价保期内提交申请，并以订单实付金额、活动规则和商品当前价格为准。具体是否可保价需要人工或后续业务系统确认。",
                    List.of("保价", "价保", "价格保护", "降价"),
                    40));

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final HybridKnowledgeRetriever hybridKnowledgeRetriever;
    private final KnowledgeIndexSynchronizer indexSynchronizer;

    public FaqKnowledgeService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this(jdbcTemplate, objectMapper, null, null);
    }

    public FaqKnowledgeService(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            HybridKnowledgeRetriever hybridKnowledgeRetriever) {
        this(jdbcTemplate, objectMapper, hybridKnowledgeRetriever, null);
    }

    @Autowired
    public FaqKnowledgeService(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            HybridKnowledgeRetriever hybridKnowledgeRetriever,
            KnowledgeIndexSynchronizer indexSynchronizer) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.hybridKnowledgeRetriever = hybridKnowledgeRetriever;
        this.indexSynchronizer = indexSynchronizer;
    }

    public FaqQueryResponse query(FaqQueryRequest request) {
        String question = textOr(request.question(), "");
        Optional<HybridRetrievalResult> hybridResult = hybridKnowledgeRetriever == null
                ? Optional.empty()
                : hybridKnowledgeRetriever.retrieve(question);
        if (hybridResult.isPresent()) {
            HybridRetrievalResult result = hybridResult.orElseThrow();
            return new FaqQueryResponse(
                    result.candidate().faqId(),
                    question,
                    result.candidate().answer(),
                    true,
                    result.confidence(),
                    List.of(),
                    result.source(),
                    Instant.now());
        }
        String normalizedQuestion = normalize(question);
        List<FaqEntry> faqEntries = activeFaqEntries();
        FaqMatch bestMatch = faqEntries.stream()
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
        FaqEntry entry = bestMatch.entry();
        return new FaqQueryResponse(
                entry.faqId(),
                question,
                entry.answer(),
                true,
                confidence,
                bestMatch.matchedKeywords(),
                SOURCE,
                Instant.now());
    }

    public PageResult<FaqItem> listFaqs(String status, String keyword, int pageNo, int pageSize) {
        int normalizedPageNo = Math.max(1, pageNo);
        int normalizedPageSize = Math.min(100, Math.max(1, pageSize));
        int offset = (normalizedPageNo - 1) * normalizedPageSize;

        List<Object> params = new ArrayList<>();
        String whereClause = buildWhereClause(status, keyword, params);
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM knowledge_faq" + whereClause, Long.class, params.toArray());
        List<Object> queryParams = new ArrayList<>(params);
        queryParams.add(normalizedPageSize);
        queryParams.add(offset);

        List<FaqItem> records = jdbcTemplate.query(
                """
                SELECT faq_id, question, answer, keywords, category, status, priority, created_at, updated_at
                FROM knowledge_faq
                """
                        + whereClause
                        + """

                ORDER BY priority DESC, updated_at DESC, faq_id ASC
                LIMIT ? OFFSET ?
                """,
                (rs, rowNum) -> mapFaqItem(rs),
                queryParams.toArray());
        return new PageResult<>(records, total == null ? 0L : total, normalizedPageNo, normalizedPageSize);
    }

    public FaqItem createFaq(FaqUpsertRequest request) {
        NormalizedFaqRequest normalized = normalizeRequest(request);
        String faqId = "faq_" + UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO knowledge_faq
                    (faq_id, question, answer, keywords, category, status, priority, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, NOW(3), NOW(3))
                """,
                faqId,
                normalized.question(),
                normalized.answer(),
                toKeywordsJson(normalized.keywords()),
                normalized.category(),
                normalized.status(),
                normalized.priority());
        FaqItem result = getFaqOrThrow(faqId);
        synchronizeIndex(result);
        return result;
    }

    public FaqItem updateFaq(String faqId, FaqUpsertRequest request) {
        String normalizedFaqId = requireText(faqId, "faqId");
        NormalizedFaqRequest normalized = normalizeRequest(request);
        int updated = jdbcTemplate.update(
                """
                UPDATE knowledge_faq
                SET question = ?,
                    answer = ?,
                    keywords = ?,
                    category = ?,
                    status = ?,
                    priority = ?,
                    updated_at = NOW(3)
                WHERE faq_id = ?
                """,
                normalized.question(),
                normalized.answer(),
                toKeywordsJson(normalized.keywords()),
                normalized.category(),
                normalized.status(),
                normalized.priority(),
                normalizedFaqId);
        if (updated <= 0) {
            throw new IllegalArgumentException("FAQ 不存在: " + normalizedFaqId);
        }
        FaqItem result = getFaqOrThrow(normalizedFaqId);
        synchronizeIndex(result);
        return result;
    }

    public FaqItem updateStatus(String faqId, FaqStatusRequest request) {
        String normalizedFaqId = requireText(faqId, "faqId");
        String status = normalizeStatus(request == null ? null : request.status());
        int updated = jdbcTemplate.update(
                """
                UPDATE knowledge_faq
                SET status = ?, updated_at = NOW(3)
                WHERE faq_id = ?
                """,
                status,
                normalizedFaqId);
        if (updated <= 0) {
            throw new IllegalArgumentException("FAQ 不存在: " + normalizedFaqId);
        }
        FaqItem result = getFaqOrThrow(normalizedFaqId);
        synchronizeIndex(result);
        return result;
    }

    public IndexSyncSummary rebuildIndexes() {
        if (indexSynchronizer == null) {
            return IndexSyncSummary.disabled();
        }
        List<FaqItem> faqs = jdbcTemplate.query(
                """
                SELECT faq_id, question, answer, keywords, category, status, priority, created_at, updated_at
                FROM knowledge_faq
                ORDER BY priority DESC, updated_at DESC, faq_id ASC
                """,
                (rs, rowNum) -> mapFaqItem(rs));
        return indexSynchronizer.rebuild(faqs);
    }

    private List<FaqEntry> activeFaqEntries() {
        try {
            List<FaqEntry> dbFaqs = jdbcTemplate.query(
                    """
                    SELECT faq_id, question, answer, keywords, priority
                    FROM knowledge_faq
                    WHERE status = 'ACTIVE'
                    ORDER BY priority DESC, updated_at DESC, faq_id ASC
                    """,
                    (rs, rowNum) -> new FaqEntry(
                            rs.getString("faq_id"),
                            rs.getString("question"),
                            rs.getString("answer"),
                            readKeywords(rs.getString("keywords")),
                            rs.getInt("priority")));
            if (!dbFaqs.isEmpty()) {
                return dbFaqs;
            }
            LOGGER.warn("knowledge_faq has no ACTIVE rows, fallback to built-in FAQs");
        } catch (DataAccessException exception) {
            LOGGER.warn("knowledge_faq is unavailable, fallback to built-in FAQs. reason={}", exception.getMessage());
        }
        return BUILTIN_FAQS;
    }

    private FaqMatch match(FaqEntry entry, String normalizedQuestion) {
        List<String> matchedKeywords = entry.keywords().stream()
                .map(this::normalize)
                .filter(keyword -> !keyword.isBlank())
                .filter(normalizedQuestion::contains)
                .toList();
        int titleScore = normalizedQuestion.contains(normalize(entry.question())) ? normalize(entry.question()).length() : 0;
        int keywordScore = matchedKeywords.stream().mapToInt(String::length).sum() + matchedKeywords.size() * 2;
        return new FaqMatch(entry, matchedKeywords, titleScore + keywordScore);
    }

    private String buildWhereClause(String status, String keyword, List<Object> params) {
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        if (status != null && !status.isBlank()) {
            where.append(" AND status = ?");
            params.add(normalizeStatus(status));
        }
        if (keyword != null && !keyword.isBlank()) {
            String like = "%" + keyword.trim() + "%";
            where.append(" AND (question LIKE ? OR answer LIKE ? OR CAST(keywords AS CHAR) LIKE ?)");
            params.add(like);
            params.add(like);
            params.add(like);
        }
        return where.toString();
    }

    private FaqItem getFaqOrThrow(String faqId) {
        List<FaqItem> rows = jdbcTemplate.query(
                """
                SELECT faq_id, question, answer, keywords, category, status, priority, created_at, updated_at
                FROM knowledge_faq
                WHERE faq_id = ?
                """,
                (rs, rowNum) -> mapFaqItem(rs),
                faqId);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("FAQ 不存在: " + faqId);
        }
        return rows.get(0);
    }

    private void synchronizeIndex(FaqItem faq) {
        if (indexSynchronizer == null) {
            return;
        }
        IndexSyncSummary summary = indexSynchronizer.synchronize(faq);
        if (summary.enabled() && !summary.allSucceeded()) {
            LOGGER.warn(
                    "FAQ 检索索引增量同步部分失败 faqId={} successCount={} failureCount={}",
                    faq.faqId(),
                    summary.successCount(),
                    summary.failureCount());
        }
    }

    private FaqItem mapFaqItem(ResultSet rs) throws SQLException {
        return new FaqItem(
                rs.getString("faq_id"),
                rs.getString("question"),
                rs.getString("answer"),
                readKeywords(rs.getString("keywords")),
                rs.getString("category"),
                rs.getString("status"),
                rs.getInt("priority"),
                toInstant(rs.getTimestamp("created_at")),
                toInstant(rs.getTimestamp("updated_at")));
    }

    private NormalizedFaqRequest normalizeRequest(FaqUpsertRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("FAQ 请求不能为空");
        }
        String question = requireText(request.question(), "question");
        String answer = requireText(request.answer(), "answer");
        List<String> keywords = normalizeKeywords(request.keywords());
        if (keywords.isEmpty()) {
            throw new IllegalArgumentException("keywords 至少需要 1 个关键词");
        }
        String category = textOr(request.category(), "general").trim();
        String status = normalizeStatus(request.status() == null ? "ACTIVE" : request.status());
        int priority = request.priority() == null ? 0 : request.priority();
        return new NormalizedFaqRequest(question, answer, keywords, category, status, priority);
    }

    private List<String> normalizeKeywords(List<String> keywords) {
        if (keywords == null) {
            return List.of();
        }
        return keywords.stream()
                .map(keyword -> textOr(keyword, "").trim())
                .filter(keyword -> !keyword.isBlank())
                .distinct()
                .toList();
    }

    private String normalizeStatus(String status) {
        String normalized = requireText(status, "status").trim().toUpperCase(Locale.ROOT);
        if (!List.of("DRAFT", "ACTIVE", "DISABLED").contains(normalized)) {
            throw new IllegalArgumentException("FAQ 状态只允许 DRAFT、ACTIVE、DISABLED");
        }
        return normalized;
    }

    private String toKeywordsJson(List<String> keywords) {
        try {
            return objectMapper.writeValueAsString(keywords);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("keywords 序列化失败", exception);
        }
    }

    private List<String> readKeywords(String keywordsJson) {
        if (keywordsJson == null || keywordsJson.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(keywordsJson, KEYWORDS_TYPE);
        } catch (JsonProcessingException exception) {
            LOGGER.warn("FAQ keywords JSON parse failed, fallback to empty keywords. value={}", keywordsJson);
            return List.of();
        }
    }

    private Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
    }

    private String textOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " 不能为空");
        }
        return value.trim();
    }

    private record FaqEntry(String faqId, String question, String answer, List<String> keywords, int priority) {
    }

    private record FaqMatch(FaqEntry entry, List<String> matchedKeywords, int score) {

        private static FaqMatch unmatched() {
            return new FaqMatch(null, List.of(), 0);
        }
    }

    private record NormalizedFaqRequest(
            String question,
            String answer,
            List<String> keywords,
            String category,
            String status,
            int priority
    ) {
    }
}
