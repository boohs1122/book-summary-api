---
name: pr
description: feature 브랜치를 PR로 올리거나 병합할 때 사용한다. "PR 만들어줘", "PR 병합해줘" 요청 시.
---

# PR 절차

제목·본문·병합 방식은 [.ai/rules/pr.md](../../../.ai/rules/pr.md)를 따른다.
이 문서는 순서만 다룬다.

작업 시작과 새 브랜치 생성 전에는 [Git 규칙의 작업 시작과 브랜치 생성](../../../.ai/rules/git.md#작업-시작과-브랜치-생성)을 따른다. 기존 변경이 있으면 보존하고 동기화 상태를 확인한다.

## gh 사용

- 원격 주소가 SSH 별칭이라 `gh`가 저장소를 추론하지 못한다. 모든 명령에 `--repo boohs1122/book-summary-api`를 붙인다
- 한 호스트에 여러 계정이 로그인되어 있을 수 있다. 기본 계정을 바꾸지 않고 명령 단위로 계정을 지정한다

  ```
  GH_TOKEN=$(gh auth token --hostname github.com --user boohs1122) gh pr create --repo boohs1122/book-summary-api ...
  ```

- 시작 전 `gh auth status --hostname github.com`에 `boohs1122`가 없거나 `gh`를 찾지 못하면 멈추고 보고한다

## 생성

1. 커밋되지 않은 변경이 없는지 확인한다. 있으면 `commit` 스킬로 먼저 처리한다
2. 브랜치가 원격에 없으면 `git push -u origin {브랜치}`로 올린다
3. `main...HEAD`의 커밋과 diff로 본문을 작성한다
   - `.github/PULL_REQUEST_TEMPLATE.md`의 절을 모두 채운다. 해당 없으면 "없음"
   - 커밋 본문의 결정 사항은 "결정 사항" 절로 옮긴다
4. 제목과 본문을 제시하고 승인을 받는다
5. 승인 후 `gh pr create --base main --title ... --body-file ...`로 생성하고 링크를 알린다

## 병합

1. 사용자가 병합을 요청했을 때만 진행한다
2. `gh pr merge {번호} --merge --delete-branch`로 병합한다 (계정 지정은 위와 같다)
3. 로컬에서 `git switch main && git pull --ff-only origin main`로 동기화하고, 병합된 로컬 브랜치를 `git branch -d`로 지운다
