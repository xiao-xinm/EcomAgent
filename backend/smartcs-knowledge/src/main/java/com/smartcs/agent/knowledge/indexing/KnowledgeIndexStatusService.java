package com.smartcs.agent.knowledge.indexing;

import com.smartcs.agent.knowledge.dto.KnowledgeIndexStatusDtos.KnowledgeIndexStatus;
import com.smartcs.agent.knowledge.dto.KnowledgeIndexStatusDtos.KnowledgeStoreStatus;
import com.smartcs.agent.knowledge.indexing.KnowledgeIndexProbe.ProbeResult;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** 汇总 MySQL 权威 FAQ 与可重建检索索引的一致性状态。 */
@Service
public class KnowledgeIndexStatusService {

    private static final Logger LOGGER = LoggerFactory.getLogger(KnowledgeIndexStatusService.class);
    private static final List<String> REQUIRED_HYBRID_INDEXES = List.of("elasticsearch", "pgvector");

    private final JdbcTemplate jdbcTemplate;
    private final List<KnowledgeIndexProbe> probes;
    private final String mode;

    @Autowired
    public KnowledgeIndexStatusService(
            JdbcTemplate jdbcTemplate,
            ObjectProvider<KnowledgeIndexProbe> probeProvider,
            @Value("${smartcs.knowledge.retrieval.mode:keyword}") String mode) {
        this(jdbcTemplate, probeProvider.orderedStream().toList(), mode);
    }

    KnowledgeIndexStatusService(
            JdbcTemplate jdbcTemplate,
            List<KnowledgeIndexProbe> probes,
            String mode) {
        this.jdbcTemplate = jdbcTemplate;
        this.probes = List.copyOf(probes);
        this.mode = normalizeMode(mode);
    }

    public KnowledgeIndexStatus inspect() {
        KnowledgeStoreStatus source = inspectSource();
        boolean enabled = "hybrid".equals(mode);
        if (!enabled) {
            return new KnowledgeIndexStatus(
                    mode,
                    false,
                    source.available(),
                    source.available(),
                    source,
                    List.of(),
                    Instant.now());
        }

        Map<String, ProbeResult> results = new LinkedHashMap<>();
        for (KnowledgeIndexProbe probe : probes) {
            results.put(probe.name(), safeProbe(probe));
        }
        List<KnowledgeStoreStatus> indexes = REQUIRED_HYBRID_INDEXES.stream()
                .map(name -> toStoreStatus(results.getOrDefault(
                        name,
                        ProbeResult.unavailable(name, "probe unavailable"))))
                .toList();
        boolean consistent = source.available()
                && source.documentCount() != null
                && indexes.stream().allMatch(index -> index.available()
                        && source.documentCount().equals(index.documentCount()));
        return new KnowledgeIndexStatus(
                mode,
                true,
                consistent,
                consistent,
                source,
                indexes,
                Instant.now());
    }

    private KnowledgeStoreStatus inspectSource() {
        try {
            Long count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM knowledge_faq WHERE status = 'ACTIVE'",
                    Long.class);
            return new KnowledgeStoreStatus("mysql", true, count == null ? 0L : count, "ready");
        } catch (DataAccessException exception) {
            LOGGER.warn("MySQL FAQ 权威数据源状态检查失败 reason={}", exception.getMessage());
            return new KnowledgeStoreStatus("mysql", false, null, safeMessage(exception));
        }
    }

    private ProbeResult safeProbe(KnowledgeIndexProbe probe) {
        try {
            ProbeResult result = probe.probe();
            return result == null
                    ? ProbeResult.unavailable(probe.name(), "probe returned no result")
                    : result;
        } catch (RuntimeException exception) {
            LOGGER.warn("知识索引状态探针异常 index={} reason={}", probe.name(), exception.getMessage());
            return ProbeResult.unavailable(probe.name(), safeMessage(exception));
        }
    }

    private KnowledgeStoreStatus toStoreStatus(ProbeResult result) {
        return new KnowledgeStoreStatus(
                result.name(),
                result.available(),
                result.documentCount(),
                result.message());
    }

    private String normalizeMode(String value) {
        return value == null || value.isBlank() ? "keyword" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String safeMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }
}
