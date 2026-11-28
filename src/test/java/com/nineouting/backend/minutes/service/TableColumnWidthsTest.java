package com.nineouting.backend.minutes.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.lowagie.text.Font;
import com.lowagie.text.pdf.PdfPTable;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 회의록 다운로드 표 열 너비 (양식 table.columnWidths, SCRUM-433) */
class TableColumnWidthsTest {

    @Test
    void 합이_100인_너비는_그대로_쓴다() {
        assertThat(MinutesExportService.columnWidths(List.of(10, 30, 45, 15), 4)).containsExactly(10f, 30f, 45f, 15f);
        assertThat(MinutesExportService.columnWidths(List.of(12.5, 87.5), 2)).containsExactly(12.5f, 87.5f);
    }

    @Test
    void 합이_100이_아니면_비율을_지켜_합_100으로_고친다() {
        assertThat(MinutesExportService.columnWidths(List.of(1, 1, 2), 3)).containsExactly(25f, 25f, 50f);
        assertThat(MinutesExportService.columnWidths(List.of(30, 30, 30), 3))
                .containsExactly(new float[] {33.333f, 33.333f, 33.333f}, within(0.001f));
    }

    @Test
    void 쓸_수_없는_값이면_같은_너비로_그린다() {
        assertThat(MinutesExportService.columnWidths(null, 3)).isNull();
        assertThat(MinutesExportService.columnWidths("10,30,60", 3)).isNull();
        assertThat(MinutesExportService.columnWidths(List.of(50, 50), 3)).isNull();
        assertThat(MinutesExportService.columnWidths(List.of(25, 25, 25, 25), 3)).isNull();
        assertThat(MinutesExportService.columnWidths(List.of(50, 0, 50), 3)).isNull();
        assertThat(MinutesExportService.columnWidths(List.of(50, -10, 60), 3)).isNull();
        assertThat(MinutesExportService.columnWidths(List.of(50, "30", 20), 3)).isNull();
        assertThat(MinutesExportService.columnWidths(Arrays.asList(50, null, 50), 3)).isNull();
    }

    @Test
    void 열_수_상한을_넘으면_같은_너비로_그린다() {
        List<Integer> ten = List.of(10, 10, 10, 10, 10, 10, 10, 10, 10, 10);
        assertThat(MinutesExportService.columnWidths(ten, 10)).hasSize(10);
        List<Integer> eleven = List.of(9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 10);
        assertThat(MinutesExportService.columnWidths(eleven, 11)).isNull();
    }

    @Test
    void PDF_표에_양식의_열_너비를_넣는다() {
        PdfPTable table = pdfTable("일시\t안건\t내용\t담당\n10:00\t배포\t일정 확정\tjjinsword", List.of(10, 30, 45, 15), 4);
        assertThat(table.getNumberOfColumns()).isEqualTo(4);
        assertThat(table.getAbsoluteWidths()).containsExactly(new float[] {10f, 30f, 45f, 15f}, within(0.01f));
    }

    @Test
    void PDF_표는_너비가_없거나_맞지_않으면_같은_너비다() {
        assertThat(pdfTable("가\t나\n다\t라", null, 2).getAbsoluteWidths())
                .containsExactly(new float[] {50f, 50f}, within(0.01f));
        // 내용 칸 수(3)가 양식 열 수(2)보다 많으면 너비 배열 길이가 맞지 않는다
        assertThat(pdfTable("가\t나\t다", List.of(30, 70), 2).getAbsoluteWidths())
                .containsExactly(new float[] {33.33f, 33.33f, 33.33f}, within(0.01f));
    }

    /** 표 폭을 100으로 두고 만들어, 열 너비를 비율 그대로 읽는다 */
    private static PdfPTable pdfTable(String content, List<Integer> widths, int cols) {
        Map<String, Object> settings = new HashMap<>();
        settings.put("cols", cols);
        if (widths != null) {
            settings.put("columnWidths", widths);
        }
        PdfPTable table = MinutesExportService.table(content, new MinutesExportService.Settings(settings), new Font());
        table.setTotalWidth(100f);
        return table;
    }
}
