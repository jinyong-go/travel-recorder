// 공유 그룹 목업.
//
// **그룹 화면은 이미 서버를 쓴다** (`api/groups.js`, `GroupsContext`). 여기 남은 것은 목업
// 여행의 `sharedGroupIds` 가 가리키는 대상뿐이며, 여행을 연동할 때 이 파일을 통째로 지운다
// (명세 §10.2). 초대 목업은 연동과 함께 이미 걷어냈다.
//
// 소유자도 멤버에 포함되므로 members 는 소유자를 포함한 목록이다.

export const MOCK_GROUPS = [
  {
    id: 1,
    name: '가족',
    memo: '설 연휴 사진 공유용',
    ownerId: 1,
    members: [
      { id: 1, name: '나', profileImageUrl: null, joinedAt: '2026-08-01' },
      { id: 3, name: '박기록', profileImageUrl: null, joinedAt: '2026-08-03' },
    ],
  },
  {
    id: 2,
    name: '제주 동행',
    memo: '2026 여름 제주 같이 간 사람들',
    ownerId: 2,
    members: [
      { id: 2, name: '김여행', profileImageUrl: null, joinedAt: '2026-08-10' },
      { id: 1, name: '나', profileImageUrl: null, joinedAt: '2026-08-12' },
      { id: 4, name: '이산책', profileImageUrl: null, joinedAt: '2026-08-15' },
    ],
  },
  // 내가 멤버가 아닌 그룹. 받은 초대(아래 102번)가 가리키는 대상이며, 수락 전까지는
  // 그룹 목록에도 상세 화면에도 나타나지 않는다.
  {
    id: 3,
    name: '회사 동료',
    memo: null,
    ownerId: 2,
    members: [
      { id: 2, name: '김여행', profileImageUrl: null, joinedAt: '2026-09-01' },
      { id: 4, name: '이산책', profileImageUrl: null, joinedAt: '2026-09-02' },
    ],
  },
]
