package com.nineouting.backend.minutes.dto;

import com.nineouting.backend.minutes.domain.MinutesSection;

/**
 * @param richContent 서식을 담은 HTML (REQ-194). 서식이 없거나 서식 기능을 껐으면 null
 */
public record SectionResponse(Long sectionId, int position, String title, String content, String richContent) {

    public static SectionResponse from(MinutesSection section, boolean richText) {
        return new SectionResponse(section.getId(), section.getPosition(), section.getTitle(), section.getContent(),
                richText ? section.getRichContent() : null);
    }
}
