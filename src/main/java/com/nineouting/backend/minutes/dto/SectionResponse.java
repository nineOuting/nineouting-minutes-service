package com.nineouting.backend.minutes.dto;

import com.nineouting.backend.minutes.domain.MinutesSection;

public record SectionResponse(Long sectionId, int position, String title, String content) {

    public static SectionResponse from(MinutesSection section) {
        return new SectionResponse(section.getId(), section.getPosition(), section.getTitle(), section.getContent());
    }
}
