# 기능별 시퀀스

현재 구현 기준의 주요 기능 호출 순서. 규칙·권한 판정의 기준은 [공통 명세](./specification.md), 요청·응답
형식과 오류 코드는 [API 명세](./api-specification.md), 화면 동작은 [프론트엔드 명세](./frontend-specification.md).

- 참여자: 브라우저(프론트엔드 SPA), API 서버, DB, NAVER API HUB
- 쓰기 요청: 전부 세션 쿠키 + `X-XSRF-TOKEN` 헤더 (§1)
- 접근 불가: 볼 수 없는 여행·기록은 없는 것과 같은 `404`, 볼 수 있지만 소유자가 아니면 `403`
- 화면 갱신: 쓰기 성공 후 다시 조회. 낙관적 갱신 없음

## 1. 세션 확인과 CSRF 토큰

앱 부팅 시 1회. 쓰기 요청 직전에 토큰이 없으면 다시 받음.

```mermaid
sequenceDiagram
    participant B as 브라우저
    participant A as API 서버
    participant D as DB

    B->>A: GET /api/auth/session
    A-->>B: authenticated · csrfToken
    Note over B: csrfToken 메모리 보관
    opt authenticated = true
        B->>A: GET /api/auth/me
        A->>D: 사용자 조회
        A-->>B: 본인 정보
    end

    Note over B,A: 이후 쓰기 요청 (POST·PUT·PATCH·DELETE)
    opt 토큰 없음
        B->>A: GET /api/auth/session
        A-->>B: csrfToken
    end
    B->>A: 쓰기 요청 + X-XSRF-TOKEN
    alt 세션 만료
        A-->>B: 401
        Note over B: 로그인 화면으로 이동 (session_expired)
    else 정상
        A-->>B: 2xx
    end
```

## 2. 로그인·로그아웃

네이버 OAuth 복구 전 임시 로그인 (`local`·`dev` 프로파일 전용 고정 계정).

```mermaid
sequenceDiagram
    participant B as 브라우저
    participant A as API 서버
    participant D as DB

    B->>A: POST /api/auth/login (username · password)
    A->>A: 인증
    alt 실패
        A-->>B: 401 UNAUTHENTICATED (사유 구분 없음)
    else 성공
        A->>D: 로그인 이력 저장
        A->>A: 세션 id 교체 · CSRF 토큰 폐기 · 인증 정보 세션 저장
        A->>D: 사용자 조회
        A-->>B: 본인 정보 + 새 세션 쿠키
        Note over B: csrfToken 폐기 → 다음 쓰기 요청 때 재발급
    end

    B->>A: POST /api/auth/logout
    A->>A: 세션 무효화 · CSRF 토큰 폐기
    A-->>B: 204
    Note over B: 사용자 정보·csrfToken 폐기
```

## 3. 여행 만들기

공개 범위 GROUP 선택 시에만 내 그룹 목록 조회.

```mermaid
sequenceDiagram
    participant B as 브라우저
    participant A as API 서버
    participant D as DB

    opt 공개 범위 GROUP 선택
        B->>A: GET /api/groups?page
        A->>D: 내가 속한 그룹 조회
        A-->>B: 그룹 목록
    end

    B->>A: POST /api/trips (기본 정보 · visibility · groupIds)
    A->>D: 여행 저장 (소유자 = 요청자)
    A->>D: 기존 공유 삭제
    opt visibility = GROUP
        A->>D: groupIds 가 전부 내가 속한 그룹인지 확인
        alt 속하지 않은 그룹 포함
            A-->>B: 404 GROUP_NOT_FOUND (트랜잭션 롤백)
        end
        A->>D: 여행 공유 저장
    end
    A->>D: 공개 범위 이력 저장 (OWNER_CHANGE)
    A-->>B: 201 여행
    Note over B: 여행 상세로 이동
```

## 4. 여행 상세 조회

비로그인도 PUBLIC 여행 조회 가능.

```mermaid
sequenceDiagram
    participant B as 브라우저
    participant A as API 서버
    participant D as DB

    B->>A: GET /api/trips/{tripId}
    A->>D: 여행 조회 + 공개 범위 조건
    alt 없음 또는 볼 수 없음
        A-->>B: 404 TRIP_NOT_FOUND
    else
        A->>D: 기록 수 · (소유자면) 공유 그룹 조회
        A-->>B: 여행 (공개 범위·공유 그룹은 소유자에게만)
    end

    B->>A: GET /api/records?tripId&sort=OLDEST&lat&lng&page
    A->>D: 여행 열람 가능 여부 확인
    A->>D: 기록 페이지 조회 + 사진 · 태그
    A-->>B: 기록 목록 (기준 위치가 있으면 거리 포함)

    loop 사진마다
        B->>A: GET /api/files/photos/{photoId}
        A->>D: 열람 가능 여부 확인 후 바이너리 조회
        A-->>B: 이미지 (볼 수 없으면 404)
    end
```

## 5. 여행지(기록) 추가

장소 검색 → 기록 등록 → 사진 업로드. 기록과 사진은 별도 요청이라 사진만 실패할 수 있음.

```mermaid
sequenceDiagram
    participant B as 브라우저
    participant A as API 서버
    participant D as DB
    participant N as NAVER API HUB

    B->>A: GET /api/places/search?keyword&page
    A->>N: 지역 검색 (검색어당 최대 5건)
    alt 키 없음 · 호출 실패
        A-->>B: 502 PLACE_SEARCH_UNAVAILABLE
    else
        N-->>A: 검색 결과
        A->>A: 태그 제거 · 좌표 환산 · 중복 제거 · 페이지 자르기
        A-->>B: 장소 후보
        Note over B: 기준 위치로 거리 계산·정렬
    end

    Note over B: 후보 선택 · 평점 · 메모 · 사진 입력
    B->>A: POST /api/records (tripId · 장소 정보 · 평점 · 메모 · 태그)
    A->>D: 내가 소유한 여행인지 확인
    alt 아님
        A-->>B: 404 TRIP_NOT_FOUND
    else
        A->>D: 태그 조회·생성 · 기록 저장
        A-->>B: 201 기록
    end

    opt 사진 있음
        B->>A: POST /api/records/{recordId}/photos (multipart)
        A->>D: 기록 편집 권한 확인
        loop 파일마다
            A->>A: 형식 · 용량 · 파일 시그니처 검사
            A->>D: 사진 메타데이터 · 바이너리 저장
        end
        alt 한 장이라도 실패
            A-->>B: 400 INVALID_FILE (전체 롤백)
            Note over B: 기록은 유지, 사진 실패 안내
        else
            A-->>B: 201 사진 목록
        end
    end
    Note over B: 기록 목록 다시 조회
```

## 6. 여행 공개 범위 변경

소유자 전용. 공유 그룹은 통째로 교체.

```mermaid
sequenceDiagram
    participant B as 브라우저
    participant A as API 서버
    participant D as DB

    B->>A: PATCH /api/trips/{tripId}/visibility (visibility · groupIds)
    A->>D: 여행 조회 + 공개 범위 조건
    alt 볼 수 없음
        A-->>B: 404 TRIP_NOT_FOUND
    else 소유자 아님
        A-->>B: 403 FORBIDDEN
    else
        A->>D: 기존 공유 삭제
        opt visibility = GROUP
            A->>D: 그룹 소속 확인 (실패 시 404 GROUP_NOT_FOUND)
            A->>D: 여행 공유 저장
        end
        A->>D: 공개 범위 이력 저장 (OWNER_CHANGE, 값이 같아도 저장)
        A-->>B: 여행
    end
```

## 7. 여행·기록 삭제

```mermaid
sequenceDiagram
    participant B as 브라우저
    participant A as API 서버
    participant D as DB

    B->>A: DELETE /api/trips/{tripId}
    A->>D: 편집 권한 확인 (404 / 403)
    A->>D: 여행 공유 삭제
    A->>D: 여행 · 하위 기록 soft delete
    A->>D: 하위 사진 바이너리 삭제 (메타데이터 유지)
    A-->>B: 204

    B->>A: DELETE /api/records/{recordId}
    A->>D: 편집 권한 확인 (404 RECORD_NOT_FOUND / 403)
    A->>D: 이 기록의 사진이 커버면 커버 해제
    A->>D: 기록 soft delete · 사진 바이너리 삭제
    A-->>B: 204
```

## 8. 그룹 만들기

```mermaid
sequenceDiagram
    participant B as 브라우저
    participant A as API 서버
    participant D as DB

    B->>A: POST /api/groups (name · memo)
    A->>D: 소유 그룹 수 확인
    alt 상한 도달
        A-->>B: 409 GROUP_LIMIT_EXCEEDED
    else
        A->>D: 그룹 저장 (소유자 = 요청자)
        A->>D: 소유자를 첫 멤버로 저장
        A->>D: 멤버 이력 저장 (JOINED)
        A-->>B: 201 그룹
        Note over B: 그룹 목록 다시 조회
    end
```

## 9. 초대 보내기·철회

소유자 전용. 이메일로 가입자 지정. 정원은 수락 시점에 확인.

```mermaid
sequenceDiagram
    participant O as 소유자 브라우저
    participant A as API 서버
    participant D as DB

    O->>A: POST /api/groups/{groupId}/invites (email)
    A->>D: 그룹 조회 · 소유자 확인 (404 / 403)
    A->>D: 이메일로 가입자 조회
    alt 가입자 없음
        A-->>O: 404 USER_NOT_FOUND
    else 이미 멤버
        A-->>O: 409 ALREADY_MEMBER
    else 대기 초대 있음
        A-->>O: 200 기존 초대 ("이미 초대한 상대")
    else
        A->>D: 초대 저장
        A-->>O: 201 초대
    end
    Note over O: 대기 초대 목록 다시 조회

    O->>A: DELETE /api/groups/{groupId}/invites/{inviteId}
    A->>D: 소유자 확인 → 초대 조회 (다른 그룹 초대면 404)
    A->>D: 초대 이력 저장 (REVOKED) · 초대 삭제
    A-->>O: 204
```

## 10. 초대 수락·거절

받은 사람 전용. 남의 초대는 없는 초대와 같은 `404 INVITE_NOT_FOUND`.

```mermaid
sequenceDiagram
    participant I as 받은 사람 브라우저
    participant A as API 서버
    participant D as DB

    I->>A: GET /api/invites?page
    A->>D: 받은 대기 초대 조회
    A-->>I: 초대 목록

    I->>A: POST /api/invites/{inviteId}/accept
    A->>D: 초대 조회 · 받은 사람 확인
    A->>D: 그룹 행 잠금 (SELECT FOR UPDATE)
    alt 그룹 정원 초과
        A-->>I: 409 GROUP_MEMBER_LIMIT_EXCEEDED (초대 유지)
    else 참여 그룹 상한 도달
        A-->>I: 409 GROUP_LIMIT_EXCEEDED (초대 유지)
    else
        A->>D: 멤버 저장 · 멤버 이력 저장 (JOINED)
        A->>D: 초대 이력 저장 (ACCEPTED) · 초대 삭제
        A-->>I: 204
        Note over I: 대기·이력 다시 조회 후 그룹 상세로 이동
    end

    I->>A: POST /api/invites/{inviteId}/reject
    A->>D: 초대 조회 · 받은 사람 확인
    A->>D: 초대 이력 저장 (REJECTED) · 초대 삭제
    A-->>I: 204
```

## 11. 그룹 탈퇴·멤버 제외·그룹 삭제

멤버십이 끝나면 그 멤버가 이 그룹에 건 여행 공유도 해제. 여행의 공개 범위 값은 GROUP 유지.

```mermaid
sequenceDiagram
    participant B as 브라우저
    participant A as API 서버
    participant D as DB

    B->>A: DELETE /api/groups/{groupId}/members/me
    A->>D: 그룹 조회
    alt 소유자 또는 비멤버
        A-->>B: 403 FORBIDDEN
    else
        A->>D: 멤버 삭제 · 멤버 이력 저장 (LEFT)
        A->>D: 내가 이 그룹에 건 공유 삭제 · 공개 범위 이력 저장 (OWNER_LEFT)
        A-->>B: 204
    end

    B->>A: DELETE /api/groups/{groupId}/members/{userId}
    A->>D: 소유자 확인 (403) · 대상 확인 (소유자 지목 400 / 비멤버 404 MEMBER_NOT_FOUND)
    A->>D: 멤버 삭제 · 멤버 이력 저장 (REMOVED)
    A->>D: 대상이 이 그룹에 건 공유 삭제 · 공개 범위 이력 저장 (OWNER_REMOVED)
    A-->>B: 204

    B->>A: DELETE /api/groups/{groupId}
    A->>D: 소유자 확인 (404 / 403)
    A->>D: 이 그룹의 공유 전부 삭제 · 공개 범위 이력 저장 (GROUP_DELETED)
    A->>D: 멤버 이력 저장 (GROUP_DELETED) · 대기 초대 이력 저장 (GROUP_DELETED)
    A->>D: 초대 · 멤버 · 그룹 삭제 (여행은 유지)
    A-->>B: 204
```
