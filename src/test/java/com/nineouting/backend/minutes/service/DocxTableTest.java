package com.nineouting.backend.minutes.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 회의록 Word 다운로드의 표 칸 (SCRUM-433). PDF 표와 같은 규칙을 따른다 */
class DocxTableTest {

    private static final String CONTENT = "안건\t담당\n배포\tjjinsword";

    @Test
    void 열_너비를_본문_폭_비율대로_나눈다() {
        String xml = table(CONTENT, Map.of("cols", 2, "columnWidths", List.of(30, 70)));
        assertThat(xml).startsWith("<w:tbl>")
                .contains("<w:tblW w:w=\"5000\" w:type=\"pct\"/>", "<w:tblLayout w:type=\"fixed\"/>")
                .contains("<w:gridCol w:w=\"2891\"/><w:gridCol w:w=\"6747\"/>")
                .contains("<w:tcW w:w=\"2891\" w:type=\"dxa\"/>", "<w:tcW w:w=\"6747\" w:type=\"dxa\"/>");
    }

    @Test
    void 반올림_차이는_마지막_열에_더해_합을_본문_폭에_맞춘다() {
        int[] twips = MinutesExportService.docxColumnTwips(new float[] {10, 30, 45, 15}, 4);
        assertThat(twips).containsExactly(964, 2891, 4337, 1446);
        assertThat(Arrays.stream(twips).sum()).isEqualTo(9638);
        int[] even = MinutesExportService.docxColumnTwips(null, 3);
        assertThat(even).containsExactly(3213, 3213, 3212);
    }

    @Test
    void 너비가_맞지_않으면_같은_너비다() {
        assertThat(table(CONTENT, Map.of("cols", 2))).contains("<w:gridCol w:w=\"4819\"/><w:gridCol w:w=\"4819\"/>");
        assertThat(table(CONTENT, Map.of("cols", 2, "columnWidths", List.of(30, 0))))
                .contains("<w:gridCol w:w=\"4819\"/><w:gridCol w:w=\"4819\"/>");
    }

    @Test
    void 줄은_행_탭은_칸이고_모자란_칸은_빈_칸으로_채운다() {
        String xml = table("가\t나\t다\n라", Map.of("cols", 2));
        assertThat(count(xml, "<w:tr>")).isEqualTo(2);
        assertThat(count(xml, "<w:tc>")).isEqualTo(6);
        assertThat(count(xml, "<w:gridCol ")).isEqualTo(3);
        // 칸마다 문단이 하나 있어야 Word가 연다
        assertThat(count(xml, "<w:p>")).isEqualTo(6);
    }

    @Test
    void 테두리는_PDF와_같은_규칙으로_칸마다_그린다() {
        String basic = table(CONTENT, Map.of("cols", 2, "border", "BASIC"));
        assertThat(sides(basic)).containsExactly(4, 4, 4, 4);

        String horizontal = table(CONTENT, Map.of("cols", 2, "border", "HORIZONTAL"));
        assertThat(sides(horizontal)).containsExactly(4, 0, 4, 0);

        // 2×2 표의 바깥선: 칸마다 바깥쪽 두 변
        String outer = table(CONTENT, Map.of("cols", 2, "border", "OUTER"));
        assertThat(sides(outer)).containsExactly(2, 2, 2, 2);
        String firstCell = outer.substring(outer.indexOf("<w:tc>"), outer.indexOf("</w:tc>"));
        assertThat(firstCell).contains("<w:top ", "<w:left ").doesNotContain("<w:bottom ", "<w:right ");

        String none = table(CONTENT, Map.of("cols", 2, "border", "NONE"));
        assertThat(none).doesNotContain("<w:tcBorders>");

        assertThat(table(CONTENT, Map.of("cols", 2))).isEqualTo(basic);
    }

    @Test
    void 머리_행은_양식_색_배경에_굵은_글자다() {
        String xml = table(CONTENT, Map.of("cols", 2, "headerColor", "#e9e8f2"));
        assertThat(count(xml, "w:fill=\"E9E8F2\"")).isEqualTo(2);
        assertThat(count(xml, "<w:shd ")).isEqualTo(2);
        assertThat(count(xml, "<w:b/>")).isEqualTo(2);
        String headerRow = xml.substring(xml.indexOf("<w:tr>"), xml.indexOf("</w:tr>"));
        assertThat(headerRow).contains("안건", "담당", "<w:b/>");

        assertThat(table(CONTENT, Map.of("cols", 2))).contains("w:fill=\"F4F4F4\"");
        assertThat(table(CONTENT, Map.of("cols", 2, "headerColor", "red\"/><w:x"))).contains("w:fill=\"F4F4F4\"")
                .doesNotContain("<w:x");
    }

    @Test
    void 정렬을_칸_문단에_넣는다() {
        assertThat(count(table(CONTENT, Map.of("cols", 2, "align", "CENTER")), "<w:jc w:val=\"center\"/>")).isEqualTo(4);
        assertThat(count(table(CONTENT, Map.of("cols", 2, "align", "RIGHT")), "<w:jc w:val=\"right\"/>")).isEqualTo(4);
        assertThat(count(table(CONTENT, Map.of("cols", 2)), "<w:jc w:val=\"left\"/>")).isEqualTo(4);
        assertThat(count(table(CONTENT, Map.of("cols", 2, "align", "모르는 값")), "<w:jc w:val=\"left\"/>")).isEqualTo(4);
    }

    @Test
    void 칸_글자의_XML_특수문자를_바꾼다() {
        assertThat(table("<API & 웹>\t\"따옴표\"", Map.of("cols", 2)))
                .contains("&lt;API &amp; 웹&gt;", "&quot;따옴표&quot;");
    }

    private static String table(String content, Map<String, Object> settings) {
        return MinutesExportService.docxTable(content, new MinutesExportService.Settings(new HashMap<>(settings)));
    }

    /** 표 전체에서 위·왼쪽·아래·오른쪽 선의 개수 */
    private static int[] sides(String xml) {
        return new int[] {count(xml, "<w:top w:val"), count(xml, "<w:left w:val"), count(xml, "<w:bottom w:val"),
                count(xml, "<w:right w:val")};
    }

    private static int count(String text, String part) {
        int count = 0;
        for (int at = text.indexOf(part); at >= 0; at = text.indexOf(part, at + part.length())) {
            count++;
        }
        return count;
    }
}
