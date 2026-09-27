# Kotlin · Spring 코드 규칙

## 스타일

- Kotlin 공식 컨벤션을 따른다
- 클래스 PascalCase, 함수·변수 camelCase, 상수 UPPER_SNAKE_CASE
- 가변 상태를 두지 않는다. `val`을 기본으로 쓴다
- 널 허용 타입은 필요한 곳에만 쓴다. `!!`를 쓰지 않는다

## Spring

- 생성자 주입만 사용한다. 필드 주입(`@Autowired`)을 쓰지 않는다
- `@Transactional`은 `service`에만 붙인다
- 설정값은 `@ConfigurationProperties`로 묶어 받는다. `@Value` 남용을 피한다
- 컨트롤러 요청 검증은 `@Valid`와 Bean Validation으로 처리한다

## DTO

- 요청·응답 DTO는 `data class`로 정의한다
- 필드명은 camelCase. API 명세와 일치시킨다
- 엔티티를 DTO로 재사용하지 않는다

## 식별자

접두사 + ULID 형식을 사용한다.

| 대상 | 접두사 |
|---|---|
| Book | `bok_` |
| Document | `doc_` |
| Quiz | `quz_` |
| Job | `job_` |
| QuizResult | `res_` |

## 에러

- 도메인 예외를 정의하고 `@RestControllerAdvice`에서 HTTP 응답으로 변환한다
- 응답 형식은 `{ "error": { "code": ..., "message": ... } }` 하나로 통일한다
- `message`는 사용자에게 보여도 되는 한국어 문구로 쓴다
- 스택 트레이스를 응답에 넣지 않는다

## 테스트

전 구간을 덮지 않는다. 규칙이 있고 회귀가 발생하기 쉬운 지점에만 붙인다.

- LLM 응답 파싱·검증
- 텍스트 정제 (공백·줄바꿈 정리, 하이픈 분철 결합)
- 문항 수 산정 (글자 수 구간 경계)

LLM을 실제로 호출하지 않는다. 고정 응답으로 파싱과 검증만 검증한다.

## 금지

- API 키를 코드나 설정 파일에 넣지 않는다. 환경 변수로 주입한다
- `println` 사용 금지. 로거를 쓴다
- 엔티티에 `@Data` 성격의 무분별한 setter를 만들지 않는다
