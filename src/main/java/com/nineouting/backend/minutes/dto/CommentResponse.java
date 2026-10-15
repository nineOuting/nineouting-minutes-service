package com.nineouting.backend.minutes.dto;

import java.time.LocalDateTime;

public record CommentResponse(Long commentId, Long sectionId, String quote, String body, PersonResponse author,
                              LocalDateTime createdAt, LocalDateTime resolvedAt) {
}
