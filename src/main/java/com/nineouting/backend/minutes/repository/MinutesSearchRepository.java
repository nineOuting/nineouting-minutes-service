package com.nineouting.backend.minutes.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * 아카이브 검색·필터 (REQ-051~054). 확정된 회의록만, 회의 날짜 최신순.
 * 검색어는 FULLTEXT(ngram, 2글자 단위) 인덱스로 찾고, 1글자면 LIKE로 찾는다.
 */
@Repository
@RequiredArgsConstructor
public class MinutesSearchRepository {

    /** ngram 파서가 자르는 글자 수 (MySQL 기본값) */
    private static final int NGRAM_TOKEN_SIZE = 2;

    private final EntityManager entityManager;

    /**
     * @param mark FAVORITE / IMPORTANT면 viewerId가 표시한 회의록만. null이면 전체
     */
    public record Filter(Long folderId, String query, LocalDate from, LocalDate to, String tag, Long attendeeId,
                         String mark, Long viewerId) {
    }

    public record Page(List<Long> ids, long totalCount) {
    }

    public Page search(Filter filter, int page, int size) {
        Map<String, Object> params = new HashMap<>();
        String where = where(filter, params);

        Query select = entityManager.createNativeQuery("select m.id from minutes m " + where
                + " order by m.meeting_start_at desc, m.id desc limit :limit offset :offset", Long.class);
        Query count = entityManager.createNativeQuery("select count(*) from minutes m " + where, Long.class);
        params.forEach((name, value) -> {
            select.setParameter(name, value);
            count.setParameter(name, value);
        });
        select.setParameter("limit", size);
        select.setParameter("offset", (long) page * size);

        @SuppressWarnings("unchecked")
        List<Long> ids = select.getResultList();
        return new Page(ids, ((Number) count.getSingleResult()).longValue());
    }

    private static String where(Filter filter, Map<String, Object> params) {
        StringBuilder sql = new StringBuilder("where m.folder_id = :folderId and m.status = 'CONFIRMED'");
        params.put("folderId", filter.folderId());
        if (filter.query() != null && !filter.query().isBlank()) {
            String query = filter.query().trim();
            if (query.length() < NGRAM_TOKEN_SIZE) {
                sql.append(" and m.search_text like :like");
                params.put("like", "%" + query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%");
            } else {
                // 따옴표로 감싸 한 덩어리(구문)로 찾는다. 검색어 안의 따옴표는 지운다
                sql.append(" and match(m.search_text) against (:query in boolean mode)");
                params.put("query", "\"" + query.replace("\"", "") + "\"");
            }
        }
        if (filter.from() != null) {
            sql.append(" and m.meeting_start_at >= :from");
            params.put("from", filter.from().atStartOfDay());
        }
        if (filter.to() != null) {
            sql.append(" and m.meeting_start_at < :to");
            params.put("to", filter.to().plusDays(1).atStartOfDay());
        }
        if (filter.tag() != null && !filter.tag().isBlank()) {
            sql.append(" and exists (select 1 from minutes_tags t where t.minutes_id = m.id and t.tag = :tag)");
            params.put("tag", filter.tag().trim());
        }
        if (filter.attendeeId() != null) {
            sql.append(" and exists (select 1 from minutes_attendees a where a.minutes_id = m.id and a.user_id = :attendeeId)");
            params.put("attendeeId", filter.attendeeId());
        }
        if ("FAVORITE".equals(filter.mark()) || "IMPORTANT".equals(filter.mark())) {
            String column = "FAVORITE".equals(filter.mark()) ? "favorite" : "important";
            sql.append(" and exists (select 1 from minutes_marks k where k.minutes_id = m.id and k.user_id = :viewerId and k.")
                    .append(column).append(" = true)");
            params.put("viewerId", filter.viewerId());
        }
        return sql.toString();
    }
}
