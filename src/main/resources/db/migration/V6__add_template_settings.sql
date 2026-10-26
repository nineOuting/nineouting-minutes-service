-- REQ-120 (2026-10-26 Figma 23-2 회의록 양식 편집): 양식 설명·레이아웃, 항목 설명·설정
-- 레이아웃·항목 설정은 화면 표시용 값이라 JSON 글자로 그대로 둔다 (키는 TemplateSettings 참고)
ALTER TABLE templates ADD COLUMN description VARCHAR(100) NOT NULL DEFAULT '';
ALTER TABLE templates ADD COLUMN layout TEXT NOT NULL DEFAULT '{}';
ALTER TABLE template_items ADD COLUMN description VARCHAR(200) NOT NULL DEFAULT '';
ALTER TABLE template_items ADD COLUMN settings TEXT NOT NULL DEFAULT '{}';

UPDATE template_items SET description = '각 안건에 대한 논의 내용을 정리할 수 있어요.' WHERE template_id = 1 AND title = '안건별 논의';
UPDATE template_items SET description = '회의에서 결정된 주요 사항을 정리합니다.' WHERE template_id = 1 AND title = '결정 사항';
UPDATE template_items SET description = '결정하지 못해 다음 회의로 넘길 안건을 정리합니다.' WHERE template_id = 1 AND title = '미결 안건';
UPDATE template_items SET description = '회의 이후 해야 할 업무와 추가 논의가 필요한 항목을 정리합니다.' WHERE template_id = 1 AND title = '할 일';
