# 서버 계약 요약

상세는 앱 저장소 `BookSummaryApp/docs/03_API명세.md`를 따른다.
불일치가 있으면 명세 문서가 기준이다.

## 기본

- Base URL: `{host}/api/v1`
- 인증: `Authorization: Bearer {Firebase ID Token}`
- 시각: ISO-8601 UTC

## 엔드포인트

| # | Method | Path | 용도 |
|---|---|---|---|
| B1 | GET | `/books` | 책 목록 |
| B2 | POST | `/books` | 책 생성 |
| B3 | GET | `/books/{bookId}` | 책 상세 + 회차 목록 |
| B4 | DELETE | `/books/{bookId}` | 책 삭제 |
| B5 | PATCH | `/books/{bookId}` | 책 제목 수정 |
| E2 | POST | `/documents` | 텍스트 등록 + 요약 요청 |
| E3 | GET | `/jobs/{jobId}` | 작업 상태 |
| E4 | GET | `/documents/{documentId}` | 회차 상세 + 요약 |
| E5 | DELETE | `/documents/{documentId}` | 회차 삭제 |
| E6 | POST | `/documents/{documentId}/quiz` | 퀴즈 생성 |
| E7 | GET | `/documents/{documentId}/quiz` | 퀴즈 조회 |
| E8 | POST | `/quizzes/{quizId}/results` | 풀이 결과 저장 |
| E9 | POST | `/documents/{documentId}/retry` | 요약 재생성 |

## 비동기 규약

LLM 호출이 포함된 요청은 `202 Accepted`와 `jobId`를 반환한다.
2초 간격으로 `GET /jobs/{jobId}`를 조회해 `DONE`을 기다린다.

작업 취소 엔드포인트는 없다. 대기 화면의 취소는 화면 이탈만 의미한다.

## 에러

분기 판단에는 `error.code`만 사용한다. `error.message`를 파싱하지 않는다.

주요 코드: `TEXT_TOO_SHORT`, `INVALID_REQUEST`, `UNAUTHORIZED`, `FORBIDDEN`,
`NOT_FOUND`, `JOB_IN_PROGRESS`, `RATE_LIMITED`, `LLM_FAILED`,
`LLM_INVALID_RESPONSE`, `INTERNAL_ERROR`
