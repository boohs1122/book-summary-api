# book-summary-api
book summary api

## 로컬 실행

Firebase ID 토큰 검증을 위해 프로젝트 ID와 서비스 계정 인증 정보 경로를 환경 변수로 지정한다.
서비스 계정 JSON 파일은 저장소 밖에 보관한다.

```sh
export FIREBASE_PROJECT_ID="<Firebase 프로젝트 ID>"
export GOOGLE_APPLICATION_CREDENTIALS="<서비스 계정 JSON 절대 경로>"
./gradlew bootRun
```

현재 `GET /api/v1/books`는 인증 연결 확인을 위해 빈 목록을 반환한다. 책 데이터 조회는
책·회차 CRUD 단계에서 구현한다.
