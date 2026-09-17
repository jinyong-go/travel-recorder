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

## 미결 사항
"그룹을 지정해 권한으로 관리"가 공동 편집(Polarsteps 방식)을 의도한 것이라면 데이터 모델과 UX가 완전히 달라짐(공동 편집 + 충돌 처리 필요). 어느 쪽인지 확인 필요.

## 참고 자료
- https://support.polarsteps.com/hc/en-us/articles/24267891448850-Who-can-see-my-Polarsteps-account-and-trips
- https://support.polarsteps.com/article/253-how-to-adjust-the-privacy-settings-of-your-trip
- https://help.wanderlog.com/hc/en-us/articles/4625495771163-Add-friends-to-plan-together
- https://tripmemo.app/guide/choosing-travel-journal-app
- https://support.polarsteps.com/hc/en-us/articles/24266959407250-What-is-a-trip-owner-Travel-Buddy-and-trip-group
- https://polarsteps.helpscoutdocs.com/article/275-how-to-edit-text-with-travel-together
- https://help.instagram.com/476003390920140
- https://support.google.com/photos/answer/9789702?hl=en
