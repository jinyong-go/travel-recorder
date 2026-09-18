# 여행 기록 앱 - 공개 범위 및 공유 설계 검토

## 배경
장소 마스터 데이터는 네이버 등 외부 API로 조회. 자체 DB에는 방문 기록(장소 id/위경도 + 사진, 메모, 평가)만 저장. 이 방문 기록을 사용자 간에 어떻게 공개/공유할지 검토.

## 검토 1: 전체 공개 vs 개인 전용

유사 서비스 조사 결과, 트립/일지 콘텐츠는 대부분 **기본 비공개 + 사용자가 선택적으로 공개 범위를 넓히는 구조**.

- Polarsteps: 트립 단위 비공개/팔로워/전체공개 3단계, 기본값은 팔로워 공개
- Wanderlog: private/friends(초대)/public 선택
- TripMemo, Day One류: 기본 비공개, 명시적으로 공유한 것만 노출
- Wandrly: 완성된 일지를 공개 링크로 누구나 열람 가능 (완전 공개 지향)

**결론**: 장소 마스터 데이터(외부 API 조회)와 방문 기록(개인 데이터)을 분리해서 설계. 방문 기록은 기본 비공개, 편집 권한은 항상 작성자 본인만.

## 검토 2: 공유 대상 - 개인별 vs 그룹

그룹 개념은 실제로 두 가지로 나뉨.

- **공동 편집 그룹** (Polarsteps Travel Together): 최대 5명의 Travel Buddy를 초대하면 멤버 전원이 같은 트립의 step을 생성/수정/삭제 가능. 하나의 기록을 여럿이 같이 편집.
- **재사용 가능한 view 권한 리스트** (Instagram Close Friends): 한 번 만든 리스트를 여러 게시물에 반복 재사용. 편집 권한과 무관하게 순수 조회 대상만 결정.
- Google Photos 공유 앨범: 기본 view-only, 소유자가 "Collaborate" 옵션을 켜야 참여자도 편집 가능 - 두 모델이 옵션으로 공존.

**권장안**: 편집은 항상 작성자 본인만이라는 방향과 일치하는 **view 권한 재사용 리스트(Instagram Close Friends 방식)**로 설계.

이유: 여행 일지는 공유 대상이 매번 바뀌지 않고 반복됨(가족여행은 항상 가족에게, 친구모임은 항상 그 친구들에게). 매번 개별 선택하게 하면 마찰이 커서 실사용 빈도 저하.

### 데이터 모델 스케치
```
group (id, owner_user_id, name)
group_member (group_id, user_id)
visit_record (id, author_user_id, place_id, lat, lng, photo, memo, rating, visibility)
  -- visibility: PRIVATE / GROUP / PUBLIC
visit_record_share (visit_record_id, group_id)  -- visibility=GROUP일 때만 사용
```

- 그룹 멤버는 자동으로 서로의 다른 기록을 보는 게 아니라, 해당 그룹으로 공유 설정된 기록만 조회 가능 (그룹이 곧 전체 권한 범위가 되지 않도록 주의)
- 편집 권한은 `visit_record.author_user_id`로만 판단, 그룹과 무관

> ⚠️ **위 스케치는 검토 3에서 대체되었다.** 공개 범위와 공유 관계는 `visit_record` 가 아니라
> 상위 `trip` 으로 올라갔고, `visit_record_share` 는 `trip_share` 가 되었다. 편집 권한의 근거도
> `visit_record.author_user_id` → `trip.owner_user_id` 다. 엔티티 이름도 `VisitRecord` →
> **`TripRecord`** 로 바뀌었다. 최신 모델은 검토 3의 결론표와
> [백엔드 명세 §3](./backend/SPECIFICATION.md)을 따른다.

## 검토 3: 여행(Trip) 단위 그룹화

방문 기록을 개별 단위로 두는 대신 **여행(Trip)을 상위 그룹으로 두고 방문 기록을 그 하위에 등록**하는 구조를 검토. 유사 서비스의 여행 단위 필드를 조사.

| 필드 | Polarsteps | Wanderlog | TripMemo |
|---|:---:|:---:|:---:|
| 여행 이름 | ✅ 필수 | ✅ | ✅ |
| 시작일·종료일 | ✅ 필수 (종료일 "아직 모름" 허용) | ✅ | ✅ |
| 커버 사진 | ✅ (트립 내 사진 중 선택) | ✅ | ✅ |
| 대표 지역/목적지 | 자동 파생 (step 좌표) | ✅ 직접 입력 | ✅ |
| 여행 설명 | ✅ | ✅ 노트 | ✅ |
| 동행 | Travel Buddies (계정 초대·공동 편집) | 공동 편집자 | 초대 |
| 공개 범위 | ✅ **여행 단위** 3단계 | ✅ | — |
| 일자별 정리 | step이 날짜 보유 | ✅ Day별 | ✅ 날짜 자동 정렬 |
| 예산·경비 | ❌ | ✅ (항목별·정산 포함) | ❌ |

**관찰**

- 세 서비스 모두 **여행 이름과 기간(시작·종료일)을 필수**로 받는다. 이름이 없으면 목록에서 여행을 구분할 방법이 날짜뿐이다.
- **공개 범위는 Polarsteps가 여행 단위로 둔다.** 개별 step 단위 공개 설정은 없다 — 여행 하나가 공유의 단위라는 판단.
- 커버 사진은 세 서비스 모두 **트립에 이미 올라온 사진 중에서 고르는** 방식이다. 별도 업로드 경로를 만들지 않는다.
- 예산·경비는 **계획형 앱(Wanderlog)에만** 있고, 다녀온 기록을 남기는 앱(Polarsteps·TripMemo)에는 없다.
- 동행은 세 서비스 모두 **계정 초대 = 공동 편집**으로 구현한다. "인원 수"만 받는 서비스는 없었다.

**결론** (본 프로젝트 채택안)

| 항목 | 결정 | 근거 |
|---|---|---|
| 구조 | 여행(Trip) → 방문 기록(VisitRecord) 2계층. 여행당 기록 수 제한 없음 | — |
| 여행 소속 | **모든 기록은 여행에 속한다** (필수) | 미소속 기록을 허용하면 공개 범위 판정에 예외 분기가 전부 추가된다 |
| 공개 범위 | **여행 단위로 일원화.** 기록은 소속 여행의 범위를 그대로 따르며 자체 `visibility` 를 갖지 않는다 | Polarsteps와 동일. 두 계층이 각자 범위를 가지면 "여행은 공개인데 이 기록은 왜 안 보이지" 같은 추론을 사용자에게 떠넘기게 된다 |
| 필수 입력 | 여행 이름, 시작일, 종료일, 인원 | 조사한 세 서비스의 공통 필수 필드 + 본 프로젝트 요구 |
| 선택 입력 | 커버 사진(하위 기록의 사진 중 선택), 여행 설명, 예산 | 커버·설명은 세 서비스 공통. 예산은 조사 대상 중 Wanderlog에만 있으나 본 프로젝트가 채택 |
| 인원 | **숫자만** (`headcount`) | 동행자를 계정으로 연결하면 공유 그룹과 개념이 겹치고, 조사한 서비스들처럼 공동 편집 요구로 번진다. 편집 권한은 소유자 한 명이라는 기존 방향과 충돌 |
| 기록별 방문일 | **두지 않는다** | Day별 묶음 보기를 포기하는 대신 입력 항목을 늘리지 않는다. 필요해지면 후속 과제 |
| 예산 범위 | **여행 전체 총액 하나**(원 단위)만 받는다 | 항목별 경비·정산(Wanderlog 수준)은 별도 도메인이라 범위 밖 |

미채택: 대표 지역/목적지 — 하위 기록의 주소에서 파생 가능해 중복 입력이 된다. 필요해지면 저장 없이 화면에서 계산한다.

## 미결 사항
없음. 검토 2의 "공동 편집 여부"는 **조회 전용 재사용 리스트**로 결론났고(편집 권한은 작성자 본인만), 검토 3에서 공유의 단위가 여행임을 확정했다.

## 참고 자료
- https://support.polarsteps.com/hc/en-us/articles/24267891448850-Who-can-see-my-Polarsteps-account-and-trips
- https://support.polarsteps.com/article/253-how-to-adjust-the-privacy-settings-of-your-trip
- https://help.wanderlog.com/hc/en-us/articles/4625495771163-Add-friends-to-plan-together
- https://tripmemo.app/guide/choosing-travel-journal-app
- https://support.polarsteps.com/hc/en-us/articles/24266959407250-What-is-a-trip-owner-Travel-Buddy-and-trip-group
- https://polarsteps.helpscoutdocs.com/article/275-how-to-edit-text-with-travel-together
- https://help.instagram.com/476003390920140
- https://support.google.com/photos/answer/9789702?hl=en
- https://support.polarsteps.com/hc/en-us/articles/29003811909394-How-do-I-create-my-first-trip
- https://support.polarsteps.com/hc/en-us/articles/24267106363282-Can-I-change-the-cover-photo-of-my-trip
- https://www.polarsteps.com/account-and-trip-privacy
- https://wanderlog.com/plan-a-trip
- https://help.wanderlog.com/hc/en-us/sections/4450507212827--Trip-settings
- https://tripmemo.app/features
