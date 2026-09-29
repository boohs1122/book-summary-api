# BookSummaryApi

책 요약 학습 앱의 서버. 앱이 보낸 텍스트로 LLM 요약과 퀴즈를 생성한다.
클라이언트는 별도 저장소 `BookSummaryApp`(Flutter).

Kotlin + Spring Boot. 기획 문서는 앱 저장소의 `docs/`에 있다.

## 규칙

작업 전 해당하는 규칙 문서를 읽는다.

- [.ai/rules/architecture.md](.ai/rules/architecture.md) — 레이어 구조, LLM 경계
- [.ai/rules/coding.md](.ai/rules/coding.md) — 코드 스타일, Spring 사용 규칙
- [.ai/rules/git.md](.ai/rules/git.md) — 브랜치, 커밋 메시지, git 훅
- [.ai/rules/pr.md](.ai/rules/pr.md) — PR 작성과 병합
- [.ai/rules/testing.md](.ai/rules/testing.md) — 테스트 대상과 작성 기준

## 스킬

절차는 `.agents/skills/`에 둔다. `.claude/skills`는 이 폴더를 가리키는 심볼릭 링크다.

- [commit](.agents/skills/commit/SKILL.md) — 커밋 절차
- [pr](.agents/skills/pr/SKILL.md) — PR 생성과 병합 절차

## 맥락

- [.ai/context/domain.md](.ai/context/domain.md) — 용어 정의
- [.ai/context/api-contract.md](.ai/context/api-contract.md) — 엔드포인트 계약

## 하지 않는 것

- 이미지를 받지 않는다. OCR은 앱에서 끝난다
- 퀴즈 조회 응답에 정답과 해설을 포함하지 않는다. 채점 응답에만 내려보낸다
- LLM 응답을 검증 없이 저장하지 않는다
- API 키를 저장소에 커밋하지 않는다
- 커밋 메시지와 PR 본문에 `Co-Authored-By` 등 AI 작업 표기를 넣지 않는다
- 저장소에 올라가는 파일·커밋 메시지·PR에는 제품과 개발에 관한 내용만 쓴다. 작성자의 개인 사정이나 개발 외 목적은 적지 않는다
- 커밋과 push는 사용자 확인을 거친다. 커밋 전에 작성자가 `boohs1122`인지 확인한다
