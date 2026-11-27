package com.nineouting.backend.minutes.service;

import static com.lowagie.text.Rectangle.BOTTOM;
import static com.lowagie.text.Rectangle.BOX;
import static com.lowagie.text.Rectangle.LEFT;
import static com.lowagie.text.Rectangle.NO_BORDER;
import static com.lowagie.text.Rectangle.RIGHT;
import static com.lowagie.text.Rectangle.TOP;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** 회의록 PDF 표 테두리 (디자인 09 "테두리 고르기", REQ-156, SCRUM-419). 3행 3열 표로 확인한다 */
class TableBorderTest {

    private static int border(String value, int row, int col) {
        return MinutesExportService.cellBorder(value, row, col, 3, 3);
    }

    @Test
    void 기본은_모든_칸에_네_변을_그린다() {
        assertThat(border("BASIC", 1, 1)).isEqualTo(BOX);
        assertThat(border("BOLD", 0, 0)).isEqualTo(BOX);
        assertThat(border("모르는 값", 2, 2)).isEqualTo(BOX);
    }

    @Test
    void 가로선만은_칸마다_위아래만_그린다() {
        assertThat(border("HORIZONTAL", 0, 0)).isEqualTo(TOP | BOTTOM);
        assertThat(border("HORIZONTAL", 1, 1)).isEqualTo(TOP | BOTTOM);
        assertThat(border("HORIZONTAL", 2, 2)).isEqualTo(TOP | BOTTOM);
    }

    @Test
    void 바깥선만은_표_가장자리_변만_그린다() {
        assertThat(border("OUTER", 0, 0)).isEqualTo(TOP | LEFT);
        assertThat(border("OUTER", 0, 1)).isEqualTo(TOP);
        assertThat(border("OUTER", 0, 2)).isEqualTo(TOP | RIGHT);
        assertThat(border("OUTER", 1, 0)).isEqualTo(LEFT);
        assertThat(border("OUTER", 1, 1)).isEqualTo(NO_BORDER);
        assertThat(border("OUTER", 2, 2)).isEqualTo(BOTTOM | RIGHT);
        assertThat(MinutesExportService.cellBorder("OUTER", 0, 0, 1, 1)).isEqualTo(BOX);
    }

    @Test
    void 없음은_변을_그리지_않는다() {
        assertThat(border("NONE", 1, 1)).isEqualTo(NO_BORDER);
    }
}
