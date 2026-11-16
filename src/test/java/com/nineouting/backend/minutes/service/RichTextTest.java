package com.nineouting.backend.minutes.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.nineouting.backend.minutes.service.RichText.Block;
import com.nineouting.backend.minutes.service.RichText.Run;
import java.util.List;
import org.junit.jupiter.api.Test;

/** REQ-194 회의록 칸 서식 */
class RichTextTest {

    @Test
    void 허용한_서식과_정렬만_남긴다() {
        String html = "<b onclick=\"x()\">굵게</b><script>alert(1)</script><a href=\"http://x\">링크</a>"
                + "<div style=\"text-align: center; color: red\">가운데</div><span style=\"font-size:40px\">큰 글</span>"
                + "<img src=x onerror=alert(1)><div style=\"background:url(x)\">배경</div>";

        assertThat(RichText.sanitize(html)).isEqualTo(
                "<b>굵게</b>링크<div style=\"text-align: center;\">가운데</div>큰 글<div>배경</div>");
        assertThat(RichText.sanitize("  ")).isNull();
        assertThat(RichText.sanitize("<script>x</script>")).isNull();
    }

    @Test
    void 편집_칸_HTML을_PDF_줄로_나눈다() {
        String html = "첫 줄 <b>굵게</b><div><i>기울임</i> <u>밑줄</u></div><div><br></div>"
                + "<div style=\"text-align: right\">오른쪽</div><ul><li>하나</li><li><b>둘</b></li></ul><ol><li>첫째</li></ol>";

        List<Block> blocks = RichText.blocks(html);

        assertThat(blocks).containsExactly(
                new Block("left", "", List.of(new Run("첫 줄 ", false, false, false), new Run("굵게", true, false, false))),
                new Block("left", "", List.of(new Run("기울임", false, true, false), new Run(" ", false, false, false),
                        new Run("밑줄", false, false, true))),
                new Block("left", "", List.of()),
                new Block("right", "", List.of(new Run("오른쪽", false, false, false))),
                new Block("left", "• ", List.of(new Run("하나", false, false, false))),
                new Block("left", "• ", List.of(new Run("둘", true, false, false))),
                new Block("left", "1. ", List.of(new Run("첫째", false, false, false))));
    }
}
