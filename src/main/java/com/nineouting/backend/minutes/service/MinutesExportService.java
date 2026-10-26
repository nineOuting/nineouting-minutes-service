package com.nineouting.backend.minutes.service;

import com.lowagie.text.Document;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfWriter;
import com.nineouting.backend.minutes.dto.ExportFormat;
import com.nineouting.backend.minutes.dto.MinutesDetailResponse;
import com.nineouting.backend.minutes.dto.PersonResponse;
import com.nineouting.backend.minutes.dto.SectionResponse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

/**
 * REQ-137 회의록 다운로드 (PDF / .docx). 회의록을 볼 수 있는 사람이면 받을 수 있다.
 * PDF는 한글이 깨지지 않게 나눔고딕(OFL, resources/fonts)을 넣고, .docx는 Word가 읽는 최소 XML 묶음을 직접 만든다.
 */
@Service
@RequiredArgsConstructor
public class MinutesExportService {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm");
    private static final String UNKNOWN_NAME = "알 수 없는 사용자";
    private static final float PAGE_MARGIN = 56f;
    private static final float TITLE_SIZE = 18f;
    private static final float HEADING_SIZE = 13f;
    private static final float BODY_SIZE = 10.5f;
    private static final float META_SIZE = 9.5f;

    private final MinutesService minutesService;

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

    private byte[] pdf(MinutesDetailResponse minutes) {
        BaseFont regular = font("fonts/NanumGothic-Regular.ttf");
        BaseFont bold = font("fonts/NanumGothic-Bold.ttf");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, PAGE_MARGIN, PAGE_MARGIN, PAGE_MARGIN, PAGE_MARGIN);
        PdfWriter.getInstance(document, out);
        document.open();

        Paragraph title = new Paragraph(minutes.title(), new Font(bold, TITLE_SIZE));
        title.setSpacingAfter(8f);
        document.add(title);
        Font meta = new Font(regular, META_SIZE);
        for (String line : metaLines(minutes)) {
            document.add(new Paragraph(line, meta));
        }
        Font heading = new Font(bold, HEADING_SIZE);
        Font body = new Font(regular, BODY_SIZE);
        for (SectionResponse section : minutes.sections()) {
            Paragraph sectionTitle = new Paragraph(section.title(), heading);
            sectionTitle.setSpacingBefore(16f);
            sectionTitle.setSpacingAfter(4f);
            document.add(sectionTitle);
            Paragraph content = new Paragraph(section.content(), body);
            content.setLeading(BODY_SIZE * 1.6f);
            document.add(content);
        }
        document.close();
        return out.toByteArray();
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
                body.append(paragraph(line, false, 21));
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
