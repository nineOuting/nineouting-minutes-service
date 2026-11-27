-- 디자인 09 "테두리 고르기"(REQ-156, SCRUM-419): 표 테두리 값은 BASIC / HORIZONTAL / OUTER / NONE 이다
-- 디자인에 없는 "굵게"(BOLD)로 저장된 양식 항목은 BASIC 으로 바꾼다. 설정은 서버가 공백 없는 JSON 으로 저장한다 (TemplateSettings.write)
-- 회의록은 양식 설정을 복사하지 않으므로 template_items 만 고친다. 바꾼 뒤에는 어떤 항목이 BOLD 였는지 알 수 없다
UPDATE template_items
SET settings = replace(settings, '"border":"BOLD"', '"border":"BASIC"')
WHERE settings LIKE '%"border":"BOLD"%';
