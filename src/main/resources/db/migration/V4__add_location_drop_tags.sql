-- REQ-055: 아카이브 필터가 기간·참석자·장소로 바뀌었다 (2026-10-26 기획서). 태그는 기획에서 빠져 표를 지운다
ALTER TABLE minutes ADD COLUMN location VARCHAR(100) NOT NULL DEFAULT '';

DROP TABLE minutes_tags;
