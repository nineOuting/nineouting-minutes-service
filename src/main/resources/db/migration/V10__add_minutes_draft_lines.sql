-- ai-service docs/api/minutes-draft-contract.md 8, 9장: AI 초안의 줄마다 근거, 출처, 고친 표시를 둔다
-- draft_lines: 초안 결과 줄 목록 JSON [{title, type, sentence 또는 cells, transcriptSeqs, edited, needsCheck, sources, draftEdited}]
-- draft_relayout_version: 양식을 바꿔 다시 배치를 요청한 때의 content_version. 결과가 올 때까지 작성자가 저장하지 않았으면 결과로 칸을 바꾼다
ALTER TABLE minutes ADD COLUMN draft_lines TEXT NULL;
ALTER TABLE minutes ADD COLUMN draft_relayout_version INTEGER NULL;
