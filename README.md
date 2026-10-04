# book-summary-api

책에서 추출한 텍스트를 저장하고 구조화된 요약을 비동기로 생성하는 Kotlin + Spring Boot API.
클라이언트는 [book-summary-flutter](https://github.com/boohs1122/book-summary-flutter)다.

## API

모든 경로는 `/api/v1`을 기준으로 하며 Firebase ID 토큰 인증이 필요하다.

| Method | Path | 용도 |
|---|---|---|
| GET / POST | `/books` | 책 목록 / 생성 |
| GET / DELETE | `/books/{bookId}` | 책 상세와 회차 목록 / 연관 데이터 삭제 |
| PATCH | `/books/{bookId}` | 소유한 책의 제목 수정 |
| POST | `/documents` | 텍스트 등록과 요약 생성 작업 시작 |
| GET / DELETE | `/documents/{documentId}` | 원문·요약 조회 / 회차 삭제 |
| POST | `/documents/{documentId}/retry` | 실패한 회차의 요약 재시도 |
| GET | `/jobs/{jobId}` | 비동기 작업 상태 조회 |

상세 계약은 [앱 API 명세](https://github.com/boohs1122/book-summary-flutter/blob/main/docs/03_API명세.md)를 따른다.

## 로컬 실행

Firebase ID 토큰 검증을 위해 프로젝트 ID와 서비스 계정 인증 정보 경로를 환경 변수로 지정한다.
서비스 계정 JSON 파일은 저장소 밖에 보관한다.

```sh
export FIREBASE_PROJECT_ID="<Firebase 프로젝트 ID>"
export GOOGLE_APPLICATION_CREDENTIALS="<서비스 계정 JSON 절대 경로>"
export LLM_API_KEY="<Gemini API 키>"
export LLM_MODEL="<구조화 출력을 지원하는 Gemini 모델 ID>"
./gradlew bootRun
```

기본 DB는 `./.h2/booksummary`에 저장되는 파일 H2다. 배포 환경에서는 `DB_URL`,
`DB_USER`, `DB_PASSWORD`로 접속 정보를 지정한다. `.env.example`은 변수 목록이며
Spring Boot가 `.env`를 자동으로 읽지는 않는다.

## 요약 처리

`POST /documents`는 정제한 텍스트와 작업을 먼저 저장하고 `202`와 `jobId`를 반환한다.
트랜잭션 커밋 후 별도 스레드에서 Gemini를 호출한다. 앱은 2초 간격으로 작업 상태를
조회하고 `DONE`이면 반환된 `documentId`로 원문과 요약을 조회한다.

Gemini의 [구조화 출력](https://ai.google.dev/gemini-api/docs/structured-output)을 사용하고,
필수 필드와 분량별 핵심 항목 수를 서버에서 다시 검증한다. 정제 후 1,500자 이하는
3개, 4,000자 이하는 4개, 7,000자 이하는 5개, 그 이상은 6개를 요청한다.
요약·퀴즈는 검증 실패 시 한 번 재요청한다.

퀴즈는 문서 원문과 요약 핵심 항목을 바탕으로 정확히 3문항을 생성하며, 요약 상태가
`DONE`인 문서에만 요청할 수 있다. 문서당 퀴즈는 한 세트만 저장하고, 재응시 결과는
기존 문항에 연결해 누적한다. 퀴즈 조회 응답에는 정답과 해설을 넣지 않으며 풀이 결과
응답에만 포함한다. `QUIZ` 작업의 `DONE` 응답에는 `quizId`가 포함되고, `FAILED` 응답에는
포함되지 않는다. 요약 실패는 E9로 재시도하고 퀴즈 생성 실패는 E6으로 다시 요청한다.
HTTP 실패나 60초 타임아웃은 작업 실패로 저장한다. 실패한 회차의 원문은 유지된다.
퀴즈 생성은 동일한 비동기 작업 흐름을 사용한다.

`FAILED` 작업 응답에도 `documentId`를 포함하여 저장된 원문으로 재시도할 수 있다.
서버 재시작 시 중단된 작업은 `FAILED`로 복구하며 자동으로 LLM을 다시 호출하지 않는다.
완료·실패 작업은 24시간 이후 조회할 수 없고 시간별 정리 작업으로 삭제한다.

같은 책의 등록·재시도·삭제·결과 저장은 책 행 잠금으로 직렬화한다. 회차를 삭제해도
기존 순번은 유지하며 새 회차에는 현재 최대 순번 + 1을 부여한다. 책을 삭제하면 회차,
요약, 퀴즈, 풀이 결과, 작업이 외래 키의 연쇄 삭제로 함께 정리된다. 삭제 후 도착한 생성
결과는 저장하지 않는다.

## 검증

```sh
./gradlew ktlintCheck build
```

테스트에서는 Firebase와 LLM을 대역으로 교체하며 실제 인증·생성 API를 호출하지 않는다.
