package com.nineouting.backend.minutes.service;

import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfWriter;
import com.nineouting.backend.minutes.domain.TemplateItem;
import com.nineouting.backend.minutes.dto.ExportFormat;
import com.nineouting.backend.minutes.dto.MinutesDetailResponse;
import com.nineouting.backend.minutes.dto.PersonResponse;
import com.nineouting.backend.minutes.dto.SectionResponse;
import com.nineouting.backend.minutes.repository.TemplateItemRepository;
import com.nineouting.backend.minutes.repository.TemplateRepository;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

/**
 * REQ-137 회의록 다운로드 (PDF / .docx). 회의록을 볼 수 있는 사람이면 받을 수 있다.
 * PDF는 한글이 깨지지 않게 나눔고딕(OFL, resources/fonts)을 넣고, 양식 설정(TemplateSettings)의 글자 크기·색·간격·여백·
 * 머릿글·바닥글·쪽 번호와 기록 방식(목록·체크리스트·표)을 따른다. 줄글 칸에 서식(REQ-194)이 있으면 PDF에 굵게, 기울임,
 * 밑줄, 목록, 정렬을 그린다. .docx는 Word가 읽는 최소 XML 묶음을 직접 만든다. 표 칸은 Word 표로 만들어 PDF와 같은
 * 열 너비·테두리·머리 행 색·정렬을 따르고, 나머지는 서식 없이 글만 쓴다.
 */
@Service
@RequiredArgsConstructor
public class MinutesExportService {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm");
    private static final String UNKNOWN_NAME = "알 수 없는 사용자";
    /** 화면 px → PDF pt */
    private static final float PX_TO_PT = 0.75f;
    private static final float MM_TO_PT = 72f / 25.4f;
    private static final float DEFAULT_MARGIN_MM = 20f;
    private static final float TITLE_SIZE = 18f;
    private static final float DEFAULT_HEADING_PX = 16f;
    private static final float DEFAULT_BODY_PX = 14f;
    private static final float DEFAULT_GAP_PX = 16f;
    private static final float META_SIZE = 9.5f;
    private static final float EDGE_SIZE = 8.5f;
    private static final String CELL_SEPARATOR = "\t";
    /** Word 본문 폭: A4 너비 11906 − 좌우 여백 1134×2 (twip, 1/20pt) */
    private static final int DOCX_BODY_TWIP = 11906 - 1134 * 2;

    private final MinutesService minutesService;
    private final TemplateRepository templateRepository;
    private final TemplateItemRepository templateItemRepository;
    private final TemplateSettings templateSettings;

    public record ExportFile(byte[] content, String fileName, String contentType) {
    }

    public ExportFile export(Long minutesId, Long userId, ExportFormat format) {
        MinutesDetailResponse minutes = minutesService.get(minutesId, userId);
        String baseName = minutes.title().replaceAll("[\\\\/:*?\"<>|]", "_");
        Style style = style(minutes);
        return format == ExportFormat.PDF
                ? new ExportFile(pdf(minutes, style), baseName + ".pdf", "application/pdf")
                : new ExportFile(docx(minutes, style), baseName + ".docx",
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
    }

    /** 일시·장소·작성자·참석자 */
    private static List<String> metaLines(MinutesDetailResponse minutes) {
        List<String> lines = new ArrayList<>();
        lines.add("일시: " + minutes.meetingStartAt().format(DATE_TIME));
        if (!minutes.location().isBlank()) {
            lines.add("장소: " + minutes.location());
        }
        lines.add("작성자: " + nameOf(minutes.writer()));
        lines.add("참석자: " + String.join(", ", minutes.attendees().stream().map(MinutesExportService::nameOf).toList()));
        return lines;
    }

    private static String nameOf(PersonResponse person) {
        return Objects.requireNonNullElse(person.name(), UNKNOWN_NAME);
    }

    /** 회의록이 쓰는 양식의 레이아웃과 항목 제목별 설정. 양식이 없거나 지워졌으면 비어 있다 */
    private record Style(Settings layout, Map<String, Settings> sections) {

        Settings section(String title) {
            return sections.getOrDefault(title, new Settings(Map.of()));
        }
    }

    /** 설정 JSON에서 값을 꺼낸다. 없거나 형식이 다르면 기본값 */
    record Settings(Map<String, Object> values) {

        /** 형식을 정하지 않고 그대로 꺼낸다. 없으면 null */
        Object raw(String key) {
            return values.get(key);
        }

        float number(String key, float fallback) {
            return values.get(key) instanceof Number number ? number.floatValue() : fallback;
        }

        boolean flag(String key, boolean fallback) {
            return values.get(key) instanceof Boolean flag ? flag : fallback;
        }

        String text(String key, String fallback) {
            return values.get(key) instanceof String text ? text : fallback;
        }

        Settings child(String key) {
            if (values.get(key) instanceof Map<?, ?> map) {
                Map<String, Object> copy = new HashMap<>();
                map.forEach((name, value) -> copy.put(String.valueOf(name), value));
                return new Settings(copy);
            }
            return new Settings(Map.of());
        }
    }

    private Style style(MinutesDetailResponse minutes) {
        if (minutes.templateId() == null) {
            return new Style(new Settings(Map.of()), Map.of());
        }
        Settings layout = templateRepository.findById(minutes.templateId())
                .map(template -> new Settings(templateSettings.read(template.getLayout())))
                .orElse(new Settings(Map.of()));
        Map<String, Settings> sections = new HashMap<>();
        for (TemplateItem item : templateItemRepository.findByTemplateIdOrderByPosition(minutes.templateId())) {
            sections.putIfAbsent(item.getTitle(), new Settings(templateSettings.read(item.getSettings())));
        }
        return new Style(layout, sections);
    }

    private static Color color(String hex) {
        try {
            return Color.decode(hex);
        } catch (NumberFormatException e) {
            return Color.BLACK;
        }
    }

    private static int align(String value) {
        return switch (value) {
            case "CENTER" -> Element.ALIGN_CENTER;
            case "RIGHT" -> Element.ALIGN_RIGHT;
            default -> Element.ALIGN_LEFT;
        };
    }

    private static byte[] pdf(MinutesDetailResponse minutes, Style style) {
        Settings layout = style.layout();
        BaseFont regular = font("fonts/NanumGothic-Regular.ttf");
        BaseFont bold = font("fonts/NanumGothic-Bold.ttf");
        float vertical = layout.number("marginVerticalMm", DEFAULT_MARGIN_MM) * MM_TO_PT;
        float horizontal = layout.number("marginHorizontalMm", DEFAULT_MARGIN_MM) * MM_TO_PT;

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, horizontal, horizontal, vertical, vertical);
        PdfWriter writer = PdfWriter.getInstance(document, out);
        writer.setPageEvent(new PageEdges(layout, new Font(regular, EDGE_SIZE, Font.NORMAL, Color.GRAY)));
        document.open();

        Paragraph title = new Paragraph(minutes.title(), new Font(bold, TITLE_SIZE));
        title.setAlignment(align(layout.text("align", "LEFT")));
        title.setSpacingAfter(8f);
        document.add(title);
        if (layout.flag("showMeetingInfo", true)) {
            Font meta = new Font(regular, META_SIZE);
            for (String line : metaLines(minutes)) {
                document.add(new Paragraph(line, meta));
            }
        }
        float gap = layout.number("sectionGap", DEFAULT_GAP_PX) * PX_TO_PT;
        for (SectionResponse section : minutes.sections()) {
            Settings settings = style.section(section.title());
            Color textColor = color(settings.text("color", "#222222"));
            float bodySize = settings.number("bodySize", DEFAULT_BODY_PX) * PX_TO_PT;
            Paragraph sectionTitle = new Paragraph(section.title(),
                    new Font(bold, settings.number("titleSize", DEFAULT_HEADING_PX) * PX_TO_PT, Font.NORMAL, textColor));
            sectionTitle.setSpacingBefore(gap + settings.number("margin", 0) * PX_TO_PT);
            sectionTitle.setSpacingAfter(4f);
            document.add(sectionTitle);
            Font body = new Font(regular, bodySize, Font.NORMAL, textColor);
            switch (settings.text("recordType", "FREE")) {
                case "TABLE" -> document.add(table(section.content(), settings.child("table"), body));
                case "LIST" -> addLines(document, section.content(), "• ", body);
                case "CHECKLIST" -> addLines(document, section.content(), "□ ", body);
                default -> {
                    if (section.richContent() != null) {
                        addRich(document, section.richContent(), regular, bold, bodySize, textColor);
                    } else {
                        Paragraph content = new Paragraph(section.content(), body);
                        content.setLeading(bodySize * 1.6f);
                        document.add(content);
                    }
                }
            }
        }
        document.close();
        return out.toByteArray();
    }

    /** 서식 있는 줄글 칸 (REQ-194). 기울임은 PDF가 글자를 눕혀 흉내 낸다 (나눔고딕에 기울임 글꼴이 없다) */
    private static void addRich(Document document, String html, BaseFont regular, BaseFont bold, float size, Color color) {
        for (RichText.Block block : RichText.blocks(html)) {
            Paragraph paragraph = new Paragraph();
            paragraph.setLeading(size * 1.6f);
            paragraph.setAlignment(align(block.align().toUpperCase(java.util.Locale.ROOT)));
            Font plain = new Font(regular, size, Font.NORMAL, color);
            paragraph.add(new Chunk(block.marker().isEmpty() && block.runs().isEmpty() ? " " : block.marker(), plain));
            for (RichText.Run run : block.runs()) {
                int fontStyle = (run.italic() ? Font.ITALIC : Font.NORMAL) | (run.underline() ? Font.UNDERLINE : Font.NORMAL);
                paragraph.add(new Chunk(run.text(), new Font(run.bold() ? bold : regular, size, fontStyle, color)));
            }
            document.add(paragraph);
        }
    }

    private static void addLines(Document document, String content, String marker, Font body) {
        for (String line : content.split("\n")) {
            if (!line.isBlank()) {
                Paragraph paragraph = new Paragraph(marker + line.strip(), body);
                paragraph.setLeading(body.getSize() * 1.6f);
                document.add(paragraph);
            }
        }
    }

    /** 표 칸: 줄 = 행, 탭 = 열 (회의록 작성 화면의 표 편집과 같은 형식). 첫 행은 머리 행 */
    private static List<String[]> tableRows(String content) {
        List<String[]> rows = new ArrayList<>();
        for (String line : content.split("\n")) {
            rows.add(line.split(CELL_SEPARATOR, -1));
        }
        return rows;
    }

    /** 그릴 열 수: 양식의 열 수와 내용에서 가장 긴 행의 칸 수 중 큰 쪽 */
    private static int tableCols(List<String[]> rows, Settings settings) {
        return Math.max(1, Math.max((int) settings.number("cols", 1),
                rows.stream().mapToInt(row -> row.length).max().orElse(1)));
    }

    /**
     * 양식의 표 열 너비 (settings table.columnWidths, SCRUM-430·433). 열 순서대로 표 폭 대비 비율이고, 합이 100이 되게 고쳐
     * 돌려준다. 배열이 아니거나, 길이가 그릴 열 수와 다르거나, 열 수가 상한을 넘거나, 0 이하·숫자가 아닌 값이 있으면 null
     * (같은 너비로 그린다). 예전 양식과, 내용 칸 수가 양식 열 수보다 많은 표가 여기에 해당한다. PDF와 Word가 같이 쓴다
     */
    static float[] columnWidths(Object raw, int cols) {
        if (!(raw instanceof List<?> list) || list.size() != cols || cols > TemplateSettings.MAX_TABLE_COLUMNS) {
            return null;
        }
        double[] values = new double[cols];
        double sum = 0;
        for (int c = 0; c < cols; c++) {
            if (!(list.get(c) instanceof Number number) || !(number.doubleValue() > 0)
                    || !Double.isFinite(number.doubleValue())) {
                return null;
            }
            values[c] = number.doubleValue();
            sum += values[c];
        }
        if (!Double.isFinite(sum)) {
            return null;
        }
        float[] widths = new float[cols];
        for (int c = 0; c < cols; c++) {
            widths[c] = (float) (values[c] / sum * 100);
        }
        return widths;
    }

    static PdfPTable table(String content, Settings settings, Font body) {
        List<String[]> rows = tableRows(content);
        int cols = tableCols(rows, settings);
        PdfPTable table = new PdfPTable(cols);
        table.setWidthPercentage(100f);
        float[] widths = columnWidths(settings.raw("columnWidths"), cols);
        if (widths != null) {
            try {
                table.setWidths(widths);
            } catch (DocumentException e) {
                // columnWidths()가 길이를 맞춰 두므로 생기지 않는다. 생기면 같은 너비 그대로 둔다
            }
        }
        table.setSpacingBefore(2f);
        String border = settings.text("border", "BASIC");
        Color header = color(settings.text("headerColor", "#f4f4f4"));
        for (int r = 0; r < rows.size(); r++) {
            for (int c = 0; c < cols; c++) {
                String text = c < rows.get(r).length ? rows.get(r)[c] : "";
                PdfPCell cell = new PdfPCell(new Phrase(text, body));
                cell.setHorizontalAlignment(align(settings.text("align", "LEFT")));
                cell.setPadding(4f);
                cell.setBorder(cellBorder(border, r, c, rows.size(), cols));
                cell.setBorderWidth(0.5f);
                cell.setBorderColor(Color.LIGHT_GRAY);
                if (r == 0) {
                    cell.setBackgroundColor(header);
                }
                table.addCell(cell);
            }
        }
        return table;
    }

    /**
     * 표 칸 하나에 그릴 변 (디자인 09 "테두리 고르기", REQ-156, SCRUM-419).
     * BASIC 은 네 변, HORIZONTAL 은 위·아래(표 맨 위·맨 아래 선 포함), OUTER 는 표 바깥쪽 변, NONE 은 없음.
     * 모르는 값은 BASIC 으로 그린다
     */
    static int cellBorder(String border, int row, int col, int rows, int cols) {
        return switch (border) {
            case "NONE" -> Rectangle.NO_BORDER;
            case "HORIZONTAL" -> Rectangle.TOP | Rectangle.BOTTOM;
            case "OUTER" -> (row == 0 ? Rectangle.TOP : 0)
                    | (row == rows - 1 ? Rectangle.BOTTOM : 0)
                    | (col == 0 ? Rectangle.LEFT : 0)
                    | (col == cols - 1 ? Rectangle.RIGHT : 0);
            default -> Rectangle.BOX;
        };
    }

    /** 머릿글·바닥글·쪽 번호 */
    private static final class PageEdges extends PdfPageEventHelper {

        private final Settings layout;
        private final Font font;

        private PageEdges(Settings layout, Font font) {
            this.layout = layout;
            this.font = font;
        }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            Settings header = layout.child("header");
            Settings footer = layout.child("footer");
            float left = document.left();
            if (header.flag("enabled", false) && !header.text("text", "").isBlank()) {
                ColumnText.showTextAligned(writer.getDirectContent(), Element.ALIGN_LEFT,
                        new Phrase(header.text("text", ""), font), left, document.top() + 14f, 0);
            }
            float bottom = document.bottom() - 18f;
            if (footer.flag("enabled", false) && !footer.text("text", "").isBlank()) {
                ColumnText.showTextAligned(writer.getDirectContent(), Element.ALIGN_LEFT,
                        new Phrase(footer.text("text", ""), font), left, bottom, 0);
            }
            if (layout.flag("pageNumber", false)) {
                ColumnText.showTextAligned(writer.getDirectContent(), Element.ALIGN_CENTER,
                        new Phrase(String.valueOf(writer.getPageNumber()), font),
                        (document.left() + document.right()) / 2, bottom, 0);
            }
        }
    }

    private static BaseFont font(String path) {
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            return BaseFont.createFont(path.substring(path.lastIndexOf('/') + 1), BaseFont.IDENTITY_H, BaseFont.EMBEDDED,
                    true, in.readAllBytes(), null);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static byte[] docx(MinutesDetailResponse minutes, Style style) {
        StringBuilder body = new StringBuilder();
        body.append(paragraph(minutes.title(), true, 36));
        metaLines(minutes).forEach(line -> body.append(paragraph(line, false, 19)));
        for (SectionResponse section : minutes.sections()) {
            body.append(paragraph("", false, 21));
            body.append(paragraph(section.title(), true, 26));
            Settings settings = style.section(section.title());
            if (settings.text("recordType", "FREE").equals("TABLE")) {
                body.append(docxTable(section.content(), settings.child("table")));
                continue;
            }
            for (String line : section.content().split("\n", -1)) {
                body.append(paragraph(line.replace(CELL_SEPARATOR, " | "), false, 21));
            }
        }
        String document = """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body>%s<w:sectPr><w:pgSz w:w="11906" w:h="16838"/><w:pgMar w:top="1134" w:right="1134" w:bottom="1134" w:left="1134" w:header="0" w:footer="0" w:gutter="0"/></w:sectPr></w:body></w:document>"""
                .formatted(body);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            entry(zip, "[Content_Types].xml", """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/></Types>""");
            entry(zip, "_rels/.rels", """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>""");
            entry(zip, "word/document.xml", document);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }

    /** size는 반 포인트 단위 (21 = 10.5pt) */
    private static String paragraph(String text, boolean bold, int size) {
        return "<w:p>" + run(text, bold, size) + "</w:p>";
    }

    private static String run(String text, boolean bold, int size) {
        return "<w:r><w:rPr><w:rFonts w:ascii=\"맑은 고딕\" w:eastAsia=\"맑은 고딕\" w:hAnsi=\"맑은 고딕\"/>"
                + (bold ? "<w:b/>" : "") + "<w:sz w:val=\"" + size + "\"/></w:rPr>"
                + "<w:t xml:space=\"preserve\">" + escape(text) + "</w:t></w:r>";
    }

    /**
     * 표 칸을 Word 표로 (SCRUM-433). 행·열 나누기, 열 너비, 테두리, 머리 행 색, 정렬은 PDF table()과 같은 규칙을 따른다.
     * 표는 본문 폭을 꽉 채우고(고정 레이아웃), 칸 안쪽 여백은 PDF 칸 여백(4pt)과 같다
     */
    static String docxTable(String content, Settings settings) {
        List<String[]> rows = tableRows(content);
        int cols = tableCols(rows, settings);
        int[] twips = docxColumnTwips(columnWidths(settings.raw("columnWidths"), cols), cols);
        String border = settings.text("border", "BASIC");
        String header = docxColor(settings.text("headerColor", "#f4f4f4"));
        String jc = switch (settings.text("align", "LEFT")) {
            case "CENTER" -> "center";
            case "RIGHT" -> "right";
            default -> "left";
        };

        StringBuilder xml = new StringBuilder("<w:tbl><w:tblPr><w:tblW w:w=\"5000\" w:type=\"pct\"/>"
                + "<w:tblLayout w:type=\"fixed\"/><w:tblCellMar><w:top w:w=\"80\" w:type=\"dxa\"/>"
                + "<w:left w:w=\"80\" w:type=\"dxa\"/><w:bottom w:w=\"80\" w:type=\"dxa\"/>"
                + "<w:right w:w=\"80\" w:type=\"dxa\"/></w:tblCellMar></w:tblPr><w:tblGrid>");
        for (int width : twips) {
            xml.append("<w:gridCol w:w=\"").append(width).append("\"/>");
        }
        xml.append("</w:tblGrid>");
        for (int r = 0; r < rows.size(); r++) {
            xml.append("<w:tr>");
            for (int c = 0; c < cols; c++) {
                String text = c < rows.get(r).length ? rows.get(r)[c] : "";
                xml.append("<w:tc><w:tcPr><w:tcW w:w=\"").append(twips[c]).append("\" w:type=\"dxa\"/>")
                        .append(docxBorders(cellBorder(border, r, c, rows.size(), cols)));
                if (r == 0) {
                    xml.append("<w:shd w:val=\"clear\" w:color=\"auto\" w:fill=\"").append(header).append("\"/>");
                }
                xml.append("</w:tcPr><w:p><w:pPr><w:jc w:val=\"").append(jc).append("\"/></w:pPr>")
                        .append(run(text, r == 0, 21)).append("</w:p></w:tc>");
            }
            xml.append("</w:tr>");
        }
        return xml.append("</w:tbl>").toString();
    }

    /** 열 너비(%)를 본문 폭 twip으로. null이면 같은 너비. 반올림 차이는 마지막 열에 더해 합을 본문 폭에 맞춘다 */
    static int[] docxColumnTwips(float[] widths, int cols) {
        int[] twips = new int[cols];
        int used = 0;
        for (int c = 0; c < cols - 1; c++) {
            twips[c] = Math.round(DOCX_BODY_TWIP * (widths == null ? 100f / cols : widths[c]) / 100f);
            used += twips[c];
        }
        twips[cols - 1] = DOCX_BODY_TWIP - used;
        return twips;
    }

    /** cellBorder()가 고른 변을 칸 테두리로. 선 모양은 PDF와 같은 0.5pt 회색 (w:sz는 1/8pt 단위) */
    private static String docxBorders(int sides) {
        if (sides == Rectangle.NO_BORDER) {
            return "";
        }
        StringBuilder xml = new StringBuilder("<w:tcBorders>");
        String line = " w:val=\"single\" w:sz=\"4\" w:space=\"0\" w:color=\"C0C0C0\"/>";
        // 스키마가 정한 순서: top, left, bottom, right
        if ((sides & Rectangle.TOP) != 0) {
            xml.append("<w:top").append(line);
        }
        if ((sides & Rectangle.LEFT) != 0) {
            xml.append("<w:left").append(line);
        }
        if ((sides & Rectangle.BOTTOM) != 0) {
            xml.append("<w:bottom").append(line);
        }
        if ((sides & Rectangle.RIGHT) != 0) {
            xml.append("<w:right").append(line);
        }
        return xml.append("</w:tcBorders>").toString();
    }

    /** #rrggbb → rrggbb. 형식이 다르면 PDF 기본 머리 행 색 */
    private static String docxColor(String hex) {
        return hex.matches("#?[0-9a-fA-F]{6}") ? hex.replace("#", "").toUpperCase(java.util.Locale.ROOT) : "F4F4F4";
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
                .replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]", "");
    }

    private static void entry(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.strip().getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}
