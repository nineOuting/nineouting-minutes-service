-- 디자인 체크 2026-11-19 (DocTable): 기본 양식 표 머리 줄을 연한 파랑(#e9e8f2)에서 연한 회색(#f4f4f4)으로
-- 양식 편집의 머리 색 고르기에 있는 값이다. 이미 다른 색으로 바꾼 사용자 양식은 건드리지 않는다
UPDATE template_items
SET settings = replace(settings, '"headerColor":"#e9e8f2"', '"headerColor":"#f4f4f4"')
WHERE template_id = 1 AND title = '안건별 논의';

UPDATE templates SET updated_at = LOCALTIMESTAMP WHERE id = 1;
