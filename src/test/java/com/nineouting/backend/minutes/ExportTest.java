package com.nineouting.backend.minutes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
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
    void 양식_설정을_따른_PDF를_만든다() throws Exception {
        Long writer = newId();
        String body = """
                {"name":"표 양식","items":[{"title":"안건표","settings":{"recordType":"TABLE","table":{"cols":2,"border":"BOLD"}}},
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
}
