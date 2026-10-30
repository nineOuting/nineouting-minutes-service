package com.nineouting.backend.minutes.dto;

import com.nineouting.backend.minutes.domain.ReviewStatus;
import java.time.LocalDateTime;

/** 참석자별 검토 현황 (REQ-109 레거시) */
public record ReviewResponse(PersonResponse reviewer, ReviewStatus status, LocalDateTime decidedAt) {
}
