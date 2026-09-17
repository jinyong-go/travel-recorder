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
]

/** 초대 링크는 그룹당 1개만 유효하며, 재발급하면 이전 링크가 무효가 된다. */
export const INVITE_TTL_DAYS = 7

export const buildInviteUrl = (token) => `${window.location.origin}/invites/${token}`

export const newInviteToken = () =>
  // 목업용 난수. 실제 토큰은 백엔드가 SecureRandom 으로 만든다.
  Math.random().toString(36).slice(2) + Math.random().toString(36).slice(2)

export const inviteExpiryFromNow = () => {
  const expires = new Date()
  expires.setDate(expires.getDate() + INVITE_TTL_DAYS)
  return expires.toISOString()
}
