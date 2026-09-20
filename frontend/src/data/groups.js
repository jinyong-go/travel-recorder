// 공유 그룹 목업. 백엔드 연동 시 /api/groups 응답으로 대체된다.
//
// 그룹은 조회 전용 대상 목록이며 편집 권한과 무관하다.
// 소유자도 멤버에 포함되므로 memberCount 는 소유자를 센 값이다.
export const GROUP_MEMBER_LIMIT = 5

export const MOCK_GROUPS = [
  {
    id: 1,
    name: '가족',
    ownerId: 1,
    members: [
      { id: 1, name: '나', profileImageUrl: null, joinedAt: '2026-08-01' },
      { id: 3, name: '박기록', profileImageUrl: null, joinedAt: '2026-08-03' },
    ],
  },
  {
    id: 2,
    name: '제주 동행',
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
    ownerId: 2,
    members: [
      { id: 2, name: '김여행', profileImageUrl: null, joinedAt: '2026-09-01' },
      { id: 4, name: '이산책', profileImageUrl: null, joinedAt: '2026-09-02' },
    ],
  },
]

/**
 * 초대 목업. 백엔드 연동 시 /api/groups/{id}/invites · /api/invites 응답으로 대체된다.
 *
 * 초대는 토큰도 만료도 갖지 않는다 — 서비스 밖으로 나가지 않으므로 수명을 둘 이유가 없고,
 * 수락·거절·철회 셋 중 하나로 끝나면서 행이 사라진다 (공통 명세 §3.7).
 */
export const MOCK_INVITES = [
  { id: 101, groupId: 1, inviteeId: 4, invitedById: 1, createdAt: '2026-09-18' },
  { id: 102, groupId: 3, inviteeId: 1, invitedById: 2, createdAt: '2026-09-19' },
]
