package com.nineouting.backend.minutes.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * 아카이브 검색·필터 (REQ-053~056). 작성 중·검토 중·확정 모두, 회의 날짜 최신순(또는 오래된순).
 * 검색어는 대소문자를 구분하지 않는 부분 일치(ILIKE)로 찾는다. 글자 3개 단위 인덱스(pg_trgm)가 받쳐 준다.
 * 태그도 대소문자를 구분하지 않는다.
 */
@Repository
@RequiredArgsConstructor
public class MinutesSearchRepository {

    private final EntityManager entityManager;

    /**
     * @param location    장소 일부만 맞아도 된다 (폴더 아카이브)
     * @param locations   장소가 이 중 하나와 같아야 한다 (워크스페이스 아카이브 장소 필터). 비었으면 전체
     * @param attendeeIds 이 중 한 명이라도 참석했어야 한다. 비었으면 전체
     * @param mark        FAVORITE / IMPORTANT면 viewerId가 표시한 회의록만. null이면 전체
     * @param oldestFirst true면 회의 날짜 오래된순
     */
    public record Filter(Collection<Long> folderIds, String query, LocalDate from, LocalDate to, String location,
                         Collection<String> locations, Collection<Long> attendeeIds, String mark, Long viewerId,
                         boolean oldestFirst) {
    }

    public record Page(List<Long> ids, long totalCount) {
    }

    public Page search(Filter filter, int page, int size) {
        Map<String, Object> params = new HashMap<>();
        String where = where(filter, params);

        Query select = entityManager.createNativeQuery("select m.id from minutes m " + where
                + (filter.oldestFirst() ? " order by m.meeting_start_at, m.id" : " order by m.meeting_start_at desc, m.id desc")
                + " limit :limit offset :offset", Long.class);
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
        StringBuilder sql = new StringBuilder("where m.folder_id in (:folderIds)");
        params.put("folderIds", filter.folderIds());
        if (filter.query() != null && !filter.query().isBlank()) {
            sql.append(" and m.search_text ilike :like");
            params.put("like", "%" + escapeLike(filter.query().trim()) + "%");
        }
        if (filter.from() != null) {
            sql.append(" and m.meeting_start_at >= :from");
            params.put("from", filter.from().atStartOfDay());
        }
        if (filter.to() != null) {
            sql.append(" and m.meeting_start_at < :to");
            params.put("to", filter.to().plusDays(1).atStartOfDay());
        }
        if (filter.location() != null && !filter.location().isBlank()) {
            sql.append(" and m.location ilike :location");
            params.put("location", "%" + escapeLike(filter.location().trim()) + "%");
        }
        if (filter.locations() != null && !filter.locations().isEmpty()) {
            sql.append(" and m.location in (:locations)");
            params.put("locations", filter.locations());
        }
        if (filter.attendeeIds() != null && !filter.attendeeIds().isEmpty()) {
            sql.append(" and exists (select 1 from minutes_attendees a where a.minutes_id = m.id and a.user_id in (:attendeeIds))");
            params.put("attendeeIds", filter.attendeeIds());
        }
        if ("FAVORITE".equals(filter.mark()) || "IMPORTANT".equals(filter.mark())) {
            String column = "FAVORITE".equals(filter.mark()) ? "favorite" : "important";
            sql.append(" and exists (select 1 from minutes_marks k where k.minutes_id = m.id and k.user_id = :viewerId and k.")
                    .append(column).append(" = true)");
            params.put("viewerId", filter.viewerId());
        }
        return sql.toString();
    }

    /** LIKE 검색어의 \, %, _ 를 글자 그대로 찾게 한다 */
    private static String escapeLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
