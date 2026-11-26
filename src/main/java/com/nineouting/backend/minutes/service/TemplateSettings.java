package com.nineouting.backend.minutes.service;

import com.nineouting.backend.common.web.error.BusinessException;
import com.nineouting.backend.common.web.error.CommonErrorCode;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * 양식의 화면 표시 설정 (Figma 23-2 회의록 양식 편집). 서버는 JSON 그대로 저장하고, 회의록 PDF를 만들 때 일부를 읽는다.
 * <ul>
 *   <li>항목 settings: recordType(FREE / LIST / CHECKLIST / TABLE), titleSize·bodySize(px), color(#rrggbb), margin(px),
 *       table{rows, cols, autoWidth, align(LEFT / CENTER / RIGHT), border(BASIC / NONE / BOLD), headerColor}</li>
 *   <li>layout: showMeetingInfo, sectionGap(px), marginVerticalMm·marginHorizontalMm, align(LEFT / CENTER / RIGHT),
 *       pageNumber, header{enabled, text}, footer{enabled, text}</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class TemplateSettings {

    /**
     * 표 항목의 열 수 상한 (디자인 09 "열 수 고르기" 1~10열, REQ-156, SCRUM-419).
     * AI 초안 요청과 양식 가져오기에서 이 값으로 자른다. ai-service 계약(회의록 초안 5.8 columns)도 같은 상한을 받아야 한다
     */
    public static final int MAX_TABLE_COLUMNS = 10;

    /** 설정 하나가 이보다 길면 거절한다 */
    private static final int MAX_JSON_LENGTH = 4000;
    private static final TypeReference<LinkedHashMap<String, Object>> MAP = new TypeReference<>() {
    };

    private final JsonMapper jsonMapper;

    public String write(Map<String, Object> settings) {
        String json = jsonMapper.writeValueAsString(settings);
        if (json.length() > MAX_JSON_LENGTH) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
        return json;
    }

    public Map<String, Object> read(String json) {
        try {
            return jsonMapper.readValue(json, MAP);
        } catch (JacksonException e) {
            return Map.of();
        }
    }
}
