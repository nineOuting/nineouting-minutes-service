package com.nineouting.backend.minutes.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** 표 칸은 ai-service와 마크다운 표로 주고받고, 저장은 줄 = 행·탭 = 열 (minutes-draft-contract.md 5.8) */
class DraftTableTest {

    @Test
    void 저장_형식과_마크다운_표를_서로_바꾼다() {
        String saved = "안건\t할 일\t담당\n배포 일정\tQA 일정표 공유\t김철수\n예산\t회의실 예약\t";
        String markdown = "| 안건 | 할 일 | 담당 |\n|---|---|---|\n| 배포 일정 | QA 일정표 공유 | 김철수 |\n| 예산 | 회의실 예약 |  |";

        assertThat(DraftService.toMarkdownTable(saved)).isEqualTo(markdown);
        assertThat(DraftService.fromMarkdownTable(markdown)).isEqualTo(saved);
        assertThat(DraftService.toMarkdownTable("")).isEmpty();
        assertThat(DraftService.fromMarkdownTable("해당 내용 없음")).isEqualTo("해당 내용 없음");
    }
}
