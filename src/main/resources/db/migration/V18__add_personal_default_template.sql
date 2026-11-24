-- QA 64 (2026-11-24) 사용자마다 자기 기본 양식을 고칠 수 있다. 처음 고칠 때 공용 기본 양식을 복사해 만든 내 양식에 표시한다.
-- 사람마다 하나만 둔다
ALTER TABLE templates ADD COLUMN personal_default BOOLEAN NOT NULL DEFAULT FALSE;
CREATE UNIQUE INDEX uk_templates_personal_default ON templates (owner_user_id) WHERE personal_default;
