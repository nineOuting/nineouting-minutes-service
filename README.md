# nineouting-minutes-service

회의록 작성·검토·확정, 아카이브(검색·필터·즐겨찾기), 홈(검토 대기·최근 확정), 회의록 양식 관리를 맡는 서비스.

- 설계: [nineouting-docs/docs/services/minutes-service.md](https://github.com/nineOuting/nineouting-docs/blob/main/docs/services/minutes-service.md)
- 포트: `8084`
- 현재 1차 범위(AI와 무관한 부분). AI 초안·근거 보기·챗봇은 2차
- 회의가 마무리되면(`meeting.finished`) 회의록을 만든다. 이벤트는 공통 모듈 `inbox`로 받고 `outbox`로 보낸다
- 폴더 권한·사용자 이름은 공통 모듈 `client-common`으로 조회한다

## 로컬 실행

필요한 것: JDK 21, Docker, 실행 중인 user-service(8081)·workspace-service(8082)

1. `~/.gradle/gradle.properties`에 `gpr.user`, `gpr.key`(read:packages 토큰)
2. `docker compose up -d` — PostgreSQL(`localhost:5437`), Kafka(`localhost:9092`)
3. `./gradlew bootRun --args='--spring.profiles.active=local'`

## API

| 메서드 | 경로 | 설명 |
|--------|------|------|
| GET | `/api/meetings/{meetingId}/minutes` | 회의의 회의록 (아직 없으면 404 `MINUTES_NOT_READY`) |
| GET | `/api/minutes/{minutesId}` | 회의록 (확정 전에는 참석자만) |
| PUT | `/api/minutes/{minutesId}/template` | 양식 선택 (작성자, 작성 중) |
| PUT | `/api/minutes/{minutesId}/sections` | 내용 저장 (작성자, `version` 확인) |
| GET | `/api/minutes/{minutesId}/revisions` | 수정 기록 |
| POST | `/api/minutes/{minutesId}/review-request` | 검토 요청 (작성자) |
| GET | `/api/minutes/{minutesId}/reviews` | 참석자별 검토 현황 |
| POST | `/api/minutes/{minutesId}/approve` | 동의 (참석자). 모두 동의하면 자동 확정 |
| POST · GET | `/api/minutes/{minutesId}/comments` | 코멘트 작성(참석자) · 목록 |
| POST | `/api/minutes/{minutesId}/comments/{commentId}/resolve` | 코멘트 반영 표시 (작성자) |
| POST | `/api/minutes/{minutesId}/confirm` | 작성자 확정 |
| PUT | `/api/minutes/{minutesId}/marks` | 즐겨찾기·중요 표시 (개인) |
| GET | `/api/folders/{folderId}/minutes?q=&from=&to=&location=&attendeeId=&mark=&page=&size=` | 아카이브 (폴더의 회의록 전체, 작성 중·검토 중·확정) |
| GET | `/api/users/me/review-requests` | 홈: 내가 검토할 회의록 |
| GET | `/api/users/me/recent-minutes?size=10` | 홈: 최근 확정 회의록 |
| GET · POST · PUT · DELETE | `/api/minutes-templates` | 회의록 양식 (개인 + 기본 양식) |

PDF 다운로드(REQ-113)는 프론트에서 만든다.

## 이벤트

| 방향 | 토픽 | 내용 |
|------|------|------|
| 받음 | `meeting.finished` | 회의록(작성 중) 만들기 |
| 받음 | `meeting.moved` | 회의록 폴더 바꾸기 |
| 받음 | `workspace.folder-deleted` | 폴더 안의 회의록 삭제 |
| 보냄 | `minutes.review-requested` | 검토 요청 알림 (notification), "검토 중" 표시 (meeting) |
| 보냄 | `minutes.confirmed` | 확정 알림·메일 (notification), "확정" 표시 (meeting) |

## 테스트

```bash
./gradlew test   # Testcontainers로 PostgreSQL·Kafka를 띄운다. user·workspace-service는 가짜로 바꾼다
```

## 환경 변수

| 이름 | 기본값 | 설명 |
|------|--------|------|
| `SERVER_PORT` | `8084` | |
| `DB_URL` | `jdbc:postgresql://localhost:5437/minutes_db` | |
| `DB_USERNAME` / `DB_PASSWORD` | `minutes_service` | |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | |
| `JWT_JWKS_URI` | `http://localhost:8081/.well-known/jwks.json` | access 토큰 검증 공개키 |
| `USER_SERVICE_URL` | `http://localhost:8081` | 참석자 이름 조회 |
| `WORKSPACE_SERVICE_URL` | `http://localhost:8082` | 폴더 권한·목록 조회 |
