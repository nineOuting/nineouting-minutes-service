package com.nineouting.backend.minutes.controller;

import com.nineouting.backend.minutes.service.DraftService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

/** 서비스 간 주소 (밖으로 열지 않는다) */
@RestController
@RequiredArgsConstructor
public class InternalMinutesController {

    private final DraftService draftService;

    /** ai-service → AI 회의록 초안 결과 (minutes-draft-contract.md 3장). 기다리지 않는 결과도 2xx로 답한다 */
    @PostMapping("/internal/minutes/{minutesId}/draft-result")
    public ResponseEntity<Void> receiveDraftResult(@PathVariable Long minutesId, @RequestBody JsonNode body) {
        draftService.receiveResult(minutesId, body);
        return ResponseEntity.noContent().build();
    }
}
