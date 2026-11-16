package com.nineouting.backend.minutes.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.safety.Safelist;

/**
 * REQ-194 회의록 칸 서식. 화면 서식 막대가 만드는 HTML 중 굵게, 기울임, 밑줄, 글머리 목록, 정렬만 남기고,
 * PDF가 그릴 수 있게 줄(문단) 단위로 나눈다.
 */
final class RichText {

    private static final Safelist ALLOWED = new Safelist()
            .addTags("b", "strong", "i", "em", "u", "ul", "ol", "li", "div", "p", "br")
            .addAttributes("div", "style")
            .addAttributes("p", "style")
            .addAttributes("li", "style");
    private static final Pattern ALIGN = Pattern.compile("text-align\\s*:\\s*(left|center|right)", Pattern.CASE_INSENSITIVE);

    private RichText() {
    }

    /** 허용한 태그와 정렬만 남긴다. 비었으면 null */
    static String sanitize(String html) {
        if (html == null || html.isBlank()) {
            return null;
        }
        Document.OutputSettings output = new Document.OutputSettings().prettyPrint(false);
        Document document = Jsoup.parseBodyFragment(Jsoup.clean(html, "", ALLOWED, output));
        document.outputSettings(output);
        for (Element element : document.body().select("[style]")) {
            String align = align(element);
            if (align == null) {
                element.removeAttr("style");
            } else {
                element.attr("style", "text-align: " + align + ";");  // 브라우저 편집 칸과 같은 표기 (저장 뒤 다시 그리지 않게)
            }
        }
        String clean = document.body().html();
        return clean.isBlank() ? null : clean;
    }

    /** 글자 한 묶음과 그 서식 */
    record Run(String text, boolean bold, boolean italic, boolean underline) {
    }

    /**
     * PDF의 문단 한 줄.
     *
     * @param align  left, center, right
     * @param marker 목록이면 "• " 또는 "1. ", 아니면 빈 글
     */
    record Block(String align, String marker, List<Run> runs) {
    }

    static List<Block> blocks(String html) {
        Walker walker = new Walker();
        walker.walk(Jsoup.parseBodyFragment(html).body(), false, false, false);
        walker.flush(false);
        return walker.blocks;
    }

    private static String align(Element element) {
        Matcher matcher = ALIGN.matcher(element.attr("style"));
        return matcher.find() ? matcher.group(1).toLowerCase(Locale.ROOT) : null;
    }

    /** 브라우저 편집 칸은 첫 줄을 글자 그대로, 다음 줄부터 div로 감싸고, 빈 줄은 &lt;div&gt;&lt;br&gt;&lt;/div&gt;로 둔다 */
    private static final class Walker {

        private final List<Block> blocks = new ArrayList<>();
        private List<Run> runs = new ArrayList<>();
        private String align = "left";
        private String marker = "";

        /** 모인 글자를 한 줄로 끝낸다. keepEmpty면 글자가 없어도 빈 줄을 남긴다 */
        void flush(boolean keepEmpty) {
            if (!runs.isEmpty() || keepEmpty) {
                blocks.add(new Block(align, marker, List.copyOf(runs)));
            }
            runs = new ArrayList<>();
        }

        void walk(Element parent, boolean bold, boolean italic, boolean underline) {
            for (Node node : parent.childNodes()) {
                if (node instanceof TextNode text) {
                    String value = text.getWholeText().replace(' ', ' ');
                    if (!value.isEmpty()) {
                        runs.add(new Run(value, bold, italic, underline));
                    }
                } else if (node instanceof Element element) {
                    switch (element.normalName()) {
                        case "br" -> flush(true);
                        case "b", "strong" -> walk(element, true, italic, underline);
                        case "i", "em" -> walk(element, bold, true, underline);
                        case "u" -> walk(element, bold, italic, true);
                        case "ul", "ol" -> list(element, bold, italic, underline);
                        default -> block(element, bold, italic, underline);
                    }
                }
            }
        }

        private void list(Element list, boolean bold, boolean italic, boolean underline) {
            flush(false);
            String saved = marker;
            int number = 1;
            for (Element item : list.children()) {
                marker = "ol".equals(list.normalName()) ? (number++) + ". " : "• ";
                block(item, bold, italic, underline);
            }
            marker = saved;
        }

        private void block(Element element, boolean bold, boolean italic, boolean underline) {
            flush(false);
            String saved = align;
            String own = align(element);
            if (own != null) {
                align = own;
            }
            walk(element, bold, italic, underline);
            flush(false);
            align = saved;
        }
    }
}
