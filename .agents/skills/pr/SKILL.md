---
name: pr
description: feature 브랜치를 PR로 올리거나 병합할 때 사용한다. "PR 만들어줘", "PR 병합해줘" 요청 시.
---

# PR 절차

제목·본문·병합 방식은 [.ai/rules/pr.md](../../../.ai/rules/pr.md)를 따른다.
이 문서는 순서만 다룬다.

## 생성

1. 커밋되지 않은 변경이 없는지 확인한다. 있으면 `commit` 스킬로 먼저 처리한다
2. 브랜치가 원격에 없으면 `git push -u origin {브랜치}`로 올린다
3. `main...HEAD`의 커밋과 diff로 본문을 작성한다
   - `.github/PULL_REQUEST_TEMPLATE.md`의 절을 모두 채운다. 해당 없으면 "없음"
   - 커밋 본문의 결정 사항은 "결정 사항" 절로 옮긴다
4. 제목과 본문을 제시하고 승인을 받는다
5. 승인 후 `gh pr create --base main --title ... --body ...`로 생성하고 링크를 알린다

## 병합

1. 사용자가 병합을 요청했을 때만 진행한다
2. `gh pr merge {번호} --merge --delete-branch`로 병합한다
3. 로컬에서 `git switch main && git pull`로 동기화하고, 병합된 로컬 브랜치를 `git branch -d`로 지운다
