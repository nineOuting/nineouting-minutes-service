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
 * 검색어는 대소문자를 구분하지 않는 부분 일치(ILIKE)로 찾는다. 글자 3개 단위 인덱스(pg_trgm)가 받쳐 준다.
 * 태그도 대소문자를 구분하지 않는다.
 */
@Repository
@RequiredArgsConstructor
public class MinutesSearchRepository {

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
            sql.append(" and m.search_text ilike :like");
            params.put("like", "%" + query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%");
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
            sql.append(" and exists (select 1 from minutes_tags t where t.minutes_id = m.id and lower(t.tag) = lower(:tag))");
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
