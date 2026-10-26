package com.nineouting.backend.minutes.service;

import com.lowagie.text.Document;
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
 * 머릿글·바닥글·쪽 번호와 기록 방식(목록·체크리스트·표)을 따른다. .docx는 Word가 읽는 최소 XML 묶음을 직접 만든다.
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

    private final MinutesService minutesService;
    private final TemplateRepository templateRepository;
    private final TemplateItemRepository templateItemRepository;
    private final TemplateSettings templateSettings;

    public record ExportFile(byte[] content, String fileName, String contentType) {
    }

    public ExportFile export(Long minutesId, Long userId, ExportFormat format) {
        MinutesDetailResponse minutes = minutesService.get(minutesId, userId);
        String baseName = minutes.title().replaceAll("[\\\\/:*?\"<>|]", "_");
        return format == ExportFormat.PDF
                ? new ExportFile(pdf(minutes), baseName + ".pdf", "application/pdf")
                : new ExportFile(docx(minutes), baseName + ".docx",
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
    private record Settings(Map<String, Object> values) {

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

    private byte[] pdf(MinutesDetailResponse minutes) {
        Style style = style(minutes);
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
                    Paragraph content = new Paragraph(section.content(), body);
                    content.setLeading(bodySize * 1.6f);
                    document.add(content);
                }
            }
        }
        document.close();
        return out.toByteArray();
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
    private static PdfPTable table(String content, Settings settings, Font body) {
        List<String[]> rows = new ArrayList<>();
        for (String line : content.split("\n")) {
            rows.add(line.split(CELL_SEPARATOR, -1));
        }
        int cols = Math.max(1, Math.max((int) settings.number("cols", 1),
                rows.stream().mapToInt(row -> row.length).max().orElse(1)));
        PdfPTable table = new PdfPTable(cols);
        table.setWidthPercentage(100f);
        table.setSpacingBefore(2f);
        String border = settings.text("border", "BASIC");
        Color header = color(settings.text("headerColor", "#f4f4f4"));
        for (int r = 0; r < rows.size(); r++) {
            for (int c = 0; c < cols; c++) {
                String text = c < rows.get(r).length ? rows.get(r)[c] : "";
                PdfPCell cell = new PdfPCell(new Phrase(text, body));
                cell.setHorizontalAlignment(align(settings.text("align", "LEFT")));
                cell.setPadding(4f);
                cell.setBorder("NONE".equals(border) ? Rectangle.NO_BORDER : Rectangle.BOX);
                cell.setBorderWidth("BOLD".equals(border) ? 1.5f : 0.5f);
                cell.setBorderColor(Color.LIGHT_GRAY);
                if (r == 0) {
                    cell.setBackgroundColor(header);
                }
                table.addCell(cell);
            }
        }
        return table;
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

    private static byte[] docx(MinutesDetailResponse minutes) {
        StringBuilder body = new StringBuilder();
        body.append(paragraph(minutes.title(), true, 36));
        metaLines(minutes).forEach(line -> body.append(paragraph(line, false, 19)));
        for (SectionResponse section : minutes.sections()) {
            body.append(paragraph("", false, 21));
            body.append(paragraph(section.title(), true, 26));
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
        return "<w:p><w:r><w:rPr><w:rFonts w:ascii=\"맑은 고딕\" w:eastAsia=\"맑은 고딕\" w:hAnsi=\"맑은 고딕\"/>"
                + (bold ? "<w:b/>" : "") + "<w:sz w:val=\"" + size + "\"/></w:rPr>"
                + "<w:t xml:space=\"preserve\">" + escape(text) + "</w:t></w:r></w:p>";
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
