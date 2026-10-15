package com.nineouting.backend.minutes.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * 회의록 내용 저장 (REQ-093, 110).
 *
 * @param version  불러온 회의록의 version. 그사이 다른 곳에서 저장했으면 409
 * @param sections 순서 = 배열 순서
 */
public record SaveSectionsRequest(
        @NotNull(message = "version이 없습니다") Integer version,
        @NotNull(message = "내용이 없습니다") List<@Valid SectionRequest> sections
) {
}
