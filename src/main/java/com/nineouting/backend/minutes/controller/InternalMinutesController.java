package com.nineouting.backend.minutes.controller;

import com.nineouting.backend.minutes.service.DraftService;
import com.nineouting.backend.minutes.service.MinutesRagSync;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

/** 서비스 간 주소 (밖으로 열지 않는다) */
@RestController
@RequiredArgsConstructor
public class InternalMinutesController {

    private final DraftService draftService;
    private final MinutesRagSync minutesRagSync;

    /** ai-service → AI 회의록 초안 결과 (minutes-draft-contract.md 3장). 기다리지 않는 결과도 2xx로 답한다 */
    @PostMapping("/internal/minutes/{minutesId}/draft-result")
    public ResponseEntity<Void> receiveDraftResult(@PathVariable Long minutesId, @RequestBody JsonNode body) {
        draftService.receiveResult(minutesId, body);
        return ResponseEntity.noContent().build();
    }

    /** ai-service → 회의록 넣기 결과 (RAG 명세 "완료 콜백", REQ-185). 지금 보낸 버전이 아닌 결과도 2xx로 답한다 */
    @PostMapping("/internal/rag/ingest-result")
    public ResponseEntity<Void> receiveIngestResult(@RequestBody JsonNode body) {
        minutesRagSync.receiveResult(body);
        return ResponseEntity.noContent().build();
    }

    /** meeting-service → 챗봇이 찾을 회의록: 폴더의 작성 완료 회의록 번호 (최근 회의부터, 쓰레기통 제외, REQ-185) */
    @GetMapping("/internal/minutes/rag-docs")
    public List<Long> ragDocs(@RequestParam Long folderId) {
        return minutesRagSync.confirmedIds(folderId);
    }
}
