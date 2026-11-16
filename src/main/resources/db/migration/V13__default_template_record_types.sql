-- 디자인 Version02 회의록 본문 (2026-11-16): 기본 양식 칸의 기록 방식을 채운다. 지금까지는 설정이 비어 모두 줄글이었다
-- 안건별 논의는 표(No, 안건, 논의 내용, 비고), 결정 사항과 미결 안건은 목록, 할 일은 체크리스트
-- 운영 데이터가 없어 기존 기본 양식을 그대로 고친다. 되돌리려면 settings를 '{}'로 돌리면 된다
UPDATE template_items
SET settings    = '{"recordType":"TABLE","table":{"rows":2,"cols":4,"autoWidth":true,"align":"LEFT","border":"BASIC","headerColor":"#e9e8f2"}}',
    description = '안건마다 논의 내용을 표로 정리해요. 열은 No, 안건, 논의 내용, 비고예요.'
WHERE template_id = 1 AND title = '안건별 논의';

UPDATE template_items SET settings = '{"recordType":"LIST"}' WHERE template_id = 1 AND title IN ('결정 사항', '미결 안건');

UPDATE template_items
SET settings    = '{"recordType":"CHECKLIST"}',
    description = '회의 이후 해야 할 업무를 한 줄에 하나씩, 끝에 (담당자, 기한)을 붙여 정리해요.'
WHERE template_id = 1 AND title = '할 일';

UPDATE templates SET updated_at = LOCALTIMESTAMP WHERE id = 1;
