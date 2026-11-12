package com.nineouting.backend.minutes.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 초안 줄 저장과 다시 배치의 이전 줄 (ai-service minutes-draft-contract.md 5.7, 8장) */
class DraftLinesTest {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private static final String RESULT = """
            [{"title":"결정 사항","type":"text","aiFilled":true,"content":"- 배포: 30일 배포\\n- 예산: 승인 보류","evidence":[
               {"sentence":"배포: 30일 배포","transcriptSeqs":[40,42],"edited":false,"needsCheck":false,
                "sources":[{"agendaId":101,"kind":"decision","id":null,"decisionPointId":7}]},
               {"sentence":"예산: 승인 보류","transcriptSeqs":[88],"edited":true,"needsCheck":false,
                "sources":[{"agendaId":102,"kind":"undecided","id":null,"decisionPointId":9}]}]},
             {"title":"할 일","type":"table","aiFilled":true,"content":"","evidence":[
               {"cells":["QA 일정표 공유","김철수","수요일까지"],"transcriptSeqs":[43],"edited":false,"needsCheck":false,
                "sources":[{"agendaId":101,"kind":"todo","id":"k-10","decisionPointId":null}]}]},
             {"title":"메모","type":"text","aiFilled":false,"content":"작성자 글","evidence":[]}]""";

    @Test
    void 채운_칸의_줄만_저장하고_같은_칸의_예전_줄은_바꾼다() {
        JsonNode sections = MAPPER.readTree(RESULT);
        List<tools.jackson.databind.node.ObjectNode> lines = DraftLines.fromResult(sections, Set.of("결정 사항", "할 일"));
        assertThat(lines).hasSize(3);
        assertThat(lines.get(2).path("cells").get(1).asString()).isEqualTo("김철수");

        JsonNode stored = MAPPER.readTree("""
                [{"title":"결정 사항","type":"text","sentence":"옛 줄"},{"title":"다른 칸","type":"text","sentence":"남는 줄"}]""");
        JsonNode merged = DraftLines.replace(stored, Set.of("결정 사항"), lines.subList(0, 2));
        assertThat(merged.findValuesAsString("sentence")).containsExactly("남는 줄", "배포: 30일 배포", "예산: 승인 보류");
    }

    @Test
    void 그대로인_줄은_출처를_쓰고_고친_줄은_출처를_이어받고_새로_쓴_줄은_출처가_없다() {
        JsonNode stored = MAPPER.valueToTree(DraftLines.fromResult(MAPPER.readTree(RESULT), Set.of("결정 사항", "할 일")));
        List<DraftLines.Section> before = List.of(
                // 첫 줄은 그대로, 둘째 줄은 고쳤다
                new DraftLines.Section("결정 사항", "- 배포: 30일 배포\n- 예산: 다음 달에 다시 보기", false),
                new DraftLines.Section("할 일", "할 일\t담당\t기한\nQA 일정표 공유\t김철수\t수요일까지\n\t\t", true),
                // 작성자가 쓴 칸 (해당 내용 없음은 줄이 아니다)
                new DraftLines.Section("메모", "고객사 로고 받기\n해당 내용 없음", false));

        JsonNode previous = DraftLines.previousLines(before, stored);

        assertThat(previous).hasSize(4);
        JsonNode kept = previous.get(0);
        assertThat(kept.path("title").asString()).isEqualTo("결정 사항");
        assertThat(kept.path("sentence").asString()).isEqualTo("배포: 30일 배포");
        assertThat(kept.path("draftEdited").asBoolean()).isFalse();
        assertThat(kept.path("sources").get(0).path("decisionPointId").asInt()).isEqualTo(7);

        JsonNode edited = previous.get(1);
        assertThat(edited.path("sentence").asString()).isEqualTo("예산: 다음 달에 다시 보기");
        assertThat(edited.path("draftEdited").asBoolean()).isTrue();
        assertThat(edited.path("edited").asBoolean()).isTrue();
        assertThat(edited.path("transcriptSeqs").get(0).asInt()).isEqualTo(88);

        JsonNode row = previous.get(2);
        assertThat(row.path("sentence").asString()).isEqualTo("QA 일정표 공유 | 김철수 | 수요일까지");
        assertThat(row.path("draftEdited").asBoolean()).isFalse();
        assertThat(row.path("sources").get(0).path("id").asString()).isEqualTo("k-10");

        JsonNode written = previous.get(3);
        assertThat(written.path("title").asString()).isEqualTo("메모");
        assertThat(written.path("draftEdited").asBoolean()).isTrue();
        assertThat(written.path("sources")).isEmpty();
        assertThat(written.path("transcriptSeqs")).isEmpty();
    }

    @Test
    void 다른_칸으로_옮긴_줄도_글이_같으면_출처를_쓰고_지운_줄은_보내지_않는다() {
        JsonNode stored = MAPPER.valueToTree(DraftLines.fromResult(MAPPER.readTree(RESULT), Set.of("결정 사항")));
        List<DraftLines.Section> before = List.of(
                new DraftLines.Section("결정 사항", "", false),
                new DraftLines.Section("메모", "- 배포: 30일 배포", false));

        JsonNode previous = DraftLines.previousLines(before, stored);

        assertThat(previous).hasSize(1);
        assertThat(previous.get(0).path("title").asString()).isEqualTo("메모");
        assertThat(previous.get(0).path("draftEdited").asBoolean()).isFalse();
        assertThat(previous.get(0).path("sources")).hasSize(1);
    }

    @Test
    void 안건으로_묶인_칸은_묶음_제목_줄을_빼고_아래_줄을_안건_제목을_붙여_맞춘다() {
        JsonNode stored = MAPPER.readTree("""
                [{"title":"할 일","type":"text","sentence":"배포 일정: QA 일정표 공유 (김철수, 수요일까지)","transcriptSeqs":[43],
                  "sources":[{"agendaId":101,"kind":"todo","id":"k-10","decisionPointId":null}]},
                 {"title":"할 일","type":"text","sentence":"배포 일정: 배포 공지 (담당자 미정, 기한 미정)","transcriptSeqs":[44],
                  "sources":[{"agendaId":101,"kind":"todo","id":"k-11","decisionPointId":null}]}]""");
        // 묶음 제목 줄("- 배포 일정")은 줄이 아니다. 둘째 줄은 작성자가 담당, 기한을 고쳤다
        String content = String.join("\n", "- 배포 일정", "  - QA 일정표 공유 (김철수, 수요일까지)", "  - 배포 공지 (이영희, 금요일까지)");

        JsonNode previous = DraftLines.previousLines(List.of(new DraftLines.Section("할 일", content, false)), stored);

        assertThat(previous.findValuesAsString("sentence")).containsExactly(
                "배포 일정: QA 일정표 공유 (김철수, 수요일까지)", "배포 일정: 배포 공지 (이영희, 금요일까지)");
        assertThat(previous.get(0).path("draftEdited").asBoolean()).isFalse();
        assertThat(previous.get(1).path("draftEdited").asBoolean()).isTrue();
        assertThat(previous.get(1).path("sources").get(0).path("id").asString()).isEqualTo("k-11");
    }

    @Test
    void 상한을_넘으면_다시_배치하지_않는다() {
        String many = String.join("\n", java.util.Collections.nCopies(DraftLines.MAX_PREVIOUS_LINES + 1, "- 줄"));
        assertThat(DraftLines.previousLines(List.of(new DraftLines.Section("메모", many, false)), null)).isNull();
    }
}
