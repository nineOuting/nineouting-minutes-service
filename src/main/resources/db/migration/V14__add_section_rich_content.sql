-- REQ-194 회의록 칸 서식 (2026-11-16). 서식을 담은 HTML을 따로 둔다. 검색, AI, 챗봇은 지금처럼 content(일반 텍스트)를 쓴다
-- 되돌리기: minutes.rich-text.enabled=false 로 끄면 이 열을 읽지도 쓰지도 않는다. 열은 남겨 두어도 된다
ALTER TABLE minutes_sections ADD COLUMN rich_content TEXT;
