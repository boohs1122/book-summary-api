# PR 규칙

PR을 쓰는 변경은 [git.md](git.md)의 브랜치 절을 따른다. 리뷰 절차는 두지 않는다.
생성과 병합 절차는 `pr` 스킬([.agents/skills/pr/SKILL.md](../../.agents/skills/pr/SKILL.md))을 따른다.

- 제목은 커밋 제목 형식(`{타입}({스코프}): {요약}`)을 따른다
- 본문은 `.github/PULL_REQUEST_TEMPLATE.md`를 따른다
- 병합은 "Create a merge commit"으로 한다
- 병합한 브랜치는 삭제한다
- PR 생성·병합은 사용자 확인을 거친다
