package com.nineouting.backend.minutes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import com.jayway.jsonpath.JsonPath;
import java.time.LocalDateTime;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;

/** REQ-137 회의록 다운로드 */
class ExportTest extends ApiTestSupport {

    @Test
    void 회의록을_PDF와_docx로_받는다() throws Exception {
        Long writer = newId();
        Long folderMember = newId();
        Long minutesId = finishMeetingAt(newId(), folder(newId(), "기획", writer, folderMember), "배포 회의",
                LocalDateTime.of(2026, 10, 20, 10, 0), "회의실 A", writer, writer);
        writeContent(minutesId, writer, "배포 일정을 정했다 <API & 웹>");

        byte[] pdf = call(folderMember, get("/api/minutes/" + minutesId + "/export"), null)
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(pdf, 0, 5, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");

        byte[] docx = call(folderMember, get("/api/minutes/" + minutesId + "/export").param("format", "DOCX"), null)
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString(".docx")))
                .andReturn().getResponse().getContentAsByteArray();
        String document = null;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(docx))) {
            for (ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                if (entry.getName().equals("word/document.xml")) {
                    document = new String(zip.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
        }
        assertThat(document).contains("배포 회의", "회의실 A", "안건별 논의", "배포 일정을 정했다 &lt;API &amp; 웹&gt;");

        call(newId(), get("/api/minutes/" + minutesId + "/export"), null).andExpect(status().isForbidden());
    }

    @Test
    void 줄글_칸의_서식을_저장하고_PDF에_넣는다() throws Exception {
        Long writer = newId();
        Long attendee = newId();
        Long minutesId = finishMeeting(newId(), folder(newId(), "기획", writer, attendee), "서식 회의",
                LocalDateTime.of(2026, 10, 20, 10, 0), writer, writer, attendee);
        String detail = call(writer, put("/api/minutes/" + minutesId + "/template"), "{\"templateId\":1}")
                .andReturn().getResponse().getContentAsString();
        List<Integer> ids = JsonPath.read(detail, "$.data.sections[*].sectionId");
        int version = JsonPath.read(detail, "$.data.version");
        String rich = "<b>배포</b> 일정<div style=\\\"text-align: center\\\"><i>가운데</i></div><script>alert(1)</script>";
        String body = """
                {"version":%d,"sections":[{"sectionId":%d,"title":"메모","content":"배포 일정\\n가운데","richContent":"%s"}]}"""
                .formatted(version, ids.get(0), rich);

        call(writer, put("/api/minutes/" + minutesId + "/sections"), body)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.richText").value(true))
                .andExpect(jsonPath("$.data.sections[0].content").value("배포 일정\n가운데"))
                .andExpect(jsonPath("$.data.sections[0].richContent")
                        .value("<b>배포</b> 일정<div style=\"text-align: center;\"><i>가운데</i></div>"));
        call(attendee, get("/api/minutes/" + minutesId), null)
                .andExpect(jsonPath("$.data.sections[0].richContent").value(containsString("<b>배포</b>")));

        byte[] pdf = call(attendee, get("/api/minutes/" + minutesId + "/export"), null)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(pdf, 0, 5, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");

        // 서식 없이 저장하면 (서식 기능을 끈 화면, 예전 화면) 예전 서식은 지운다
        call(writer, put("/api/minutes/" + minutesId + "/sections"), """
                {"version":%d,"sections":[{"sectionId":%d,"title":"메모","content":"새 글"}]}""".formatted(version + 1, ids.get(0)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sections[0].richContent").doesNotExist());
    }

    @Test
    void 양식_설정을_따른_PDF를_만든다() throws Exception {
        Long writer = newId();
        String body = """
                {"name":"표 양식","items":[{"title":"안건표","settings":{"recordType":"TABLE","table":{"cols":2,"border":"HORIZONTAL"}}},
                 {"title":"안건표 2","settings":{"recordType":"TABLE","table":{"cols":3,"border":"OUTER"}}},
                 {"title":"할 일","settings":{"recordType":"CHECKLIST","titleSize":18,"color":"#27187d"}}],
                 "layout":{"pageNumber":true,"align":"CENTER","marginVerticalMm":30,
                           "header":{"enabled":true,"text":"SYNC"},"footer":{"enabled":true,"text":"사내용"}}}""";
        Long templateId = id(call(writer, post("/api/minutes-templates"), body), "$.data.templateId");
        Long minutesId = finishMeeting(newId(), folder(newId(), "기획", writer), "표 회의", LocalDateTime.of(2026, 10, 20, 10, 0),
                writer, writer);
        call(writer, put("/api/minutes/" + minutesId + "/template"), "{\"templateId\":%d}".formatted(templateId));

        byte[] pdf = call(writer, get("/api/minutes/" + minutesId + "/export"), null)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(pdf, 0, 5, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");
    }

    @Test
    void 표_칸은_양식_열_너비대로_Word_표로_만든다() throws Exception {
        Long writer = newId();
        String body = """
                {"name":"너비 양식","items":[
                 {"title":"안건표","settings":{"recordType":"TABLE","table":{"cols":3,"columnWidths":[20,30,50],"border":"OUTER"}}},
                 {"title":"메모","settings":{"recordType":"FREE"}}]}""";
        Long templateId = id(call(writer, post("/api/minutes-templates"), body), "$.data.templateId");
        Long minutesId = finishMeeting(newId(), folder(newId(), "기획", writer), "너비 회의", LocalDateTime.of(2026, 10, 20, 10, 0),
                writer, writer);
        String detail = call(writer, put("/api/minutes/" + minutesId + "/template"), "{\"templateId\":%d}".formatted(templateId))
                .andReturn().getResponse().getContentAsString();
        List<Integer> ids = JsonPath.read(detail, "$.data.sections[*].sectionId");
        int version = JsonPath.read(detail, "$.data.version");
        call(writer, put("/api/minutes/" + minutesId + "/sections"), """
                {"version":%d,"sections":[{"sectionId":%d,"title":"안건표","content":"안건\\t담당\\t기한\\n배포\\t운영\\t11/30"},
                 {"sectionId":%d,"title":"메모","content":"메모\\t글"}]}""".formatted(version, ids.get(0), ids.get(1)))
                .andExpect(status().isOk());

        String document = docxDocument(call(writer, get("/api/minutes/" + minutesId + "/export").param("format", "DOCX"), null)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray());
        assertThat(document).contains("<w:tbl>",
                "<w:gridCol w:w=\"1928\"/><w:gridCol w:w=\"2891\"/><w:gridCol w:w=\"4819\"/>", "11/30");
        assertThat(document.indexOf("<w:tbl>")).isEqualTo(document.lastIndexOf("<w:tbl>"));
        // 표가 아닌 칸은 예전처럼 글 줄 (탭은 " | ")
        assertThat(document).contains("메모 | 글");
    }

    private static String docxDocument(byte[] docx) throws Exception {
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(docx))) {
            for (ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                if (entry.getName().equals("word/document.xml")) {
                    return new String(zip.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
        }
        throw new AssertionError("word/document.xml 없음");
    }
}
