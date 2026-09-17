import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { GROUP_MEMBER_LIMIT, INVITE_TTL_DAYS, buildInviteUrl } from '../data/groups.js'
import { useRecords } from '../context/RecordsContext.jsx'
import useTheme from '../hooks/useTheme.js'
import ThemeSelector from '../components/ThemeSelector.jsx'
import { ArrowLeftIcon, LinkIcon, MapPinIcon } from '../components/icons.jsx'
import './GroupsPage.css'

const formatDate = (iso) => new Date(iso).toLocaleDateString('ko-KR')

export default function GroupDetailPage() {
  const { groupId } = useParams()
  const navigate = useNavigate()
  const {
    findGroup,
    currentUser,
    invites,
    issueInvite,
    revokeInvite,
    removeMember,
    leaveGroup,
    deleteGroup,
  } = useRecords()
  const { themeKey, changeTheme } = useTheme()

  const group = findGroup(groupId)
  const [copied, setCopied] = useState(false)
  const [confirming, setConfirming] = useState(null)

  if (!group) {
    // 멤버가 아니면 그룹의 존재 자체를 알리지 않는다.
    return (
      <main className="detail-page">
        <div className="detail-missing">
          <h1>그룹을 찾을 수 없습니다</h1>
          <p className="detail-missing-desc">존재하지 않거나 참여하지 않은 그룹이에요.</p>
          <Link to="/groups" className="detail-missing-link">
            <ArrowLeftIcon /> 그룹 목록으로
          </Link>
        </div>
      </main>
    )
  }

  const isOwner = group.ownerId === currentUser.id
  const invite = invites[group.id]
  const isFull = group.members.length >= GROUP_MEMBER_LIMIT

  const handleIssue = () => {
    issueInvite(group.id)
    setCopied(false)
  }

  const handleCopy = async () => {
    try {
      await navigator.clipboard.writeText(buildInviteUrl(invite.token))
      setCopied(true)
    } catch {
      setCopied(false)
    }
  }

  const confirmAction = () => {
    if (confirming === 'delete') {
      deleteGroup(group.id)
      navigate('/groups')
    } else if (confirming === 'leave') {
      leaveGroup(group.id)
      navigate('/groups')
    } else if (typeof confirming === 'number') {
      removeMember(group.id, confirming)
    }
    setConfirming(null)
  }

  const confirmText = {
    delete: {
      title: '그룹을 삭제할까요?',
      desc: '이 그룹으로만 공유한 기록은 나만 보기 상태가 됩니다. 기록 자체는 삭제되지 않아요.',
      ok: '삭제',
    },
    leave: {
      title: '그룹에서 나갈까요?',
      desc: '이 그룹으로 공유된 기록은 더 이상 보이지 않습니다.',
      ok: '나가기',
    },
    member: {
      title: '멤버를 제외할까요?',
      desc: '제외된 멤버는 이 그룹으로 공유된 기록을 더 이상 볼 수 없습니다.',
      ok: '제외',
    },
  }[typeof confirming === 'number' ? 'member' : confirming] ?? {}

  return (
    <>
      <header className="app-header">
        <Link to="/" className="app-title app-title-link">
          <MapPinIcon className="app-title-icon" />
          여행 지도 <span className="by-yong">by YONG</span>
        </Link>
        <div className="header-actions">
          <ThemeSelector themeKey={themeKey} onChange={changeTheme} />
        </div>
      </header>

      <main className="groups-page">
        <div className="groups-inner">
          <Link to="/groups" className="back-link">
            <ArrowLeftIcon /> 그룹 목록으로
          </Link>

          <h1 className="groups-title">{group.name}</h1>
          <p className="groups-desc">
            멤버 {group.members.length}/{GROUP_MEMBER_LIMIT}명 · {isOwner ? '내가 만든 그룹' : '참여 중인 그룹'}
          </p>

          <section className="group-section">
            <h2 className="group-section-title">멤버</h2>
            <ul className="member-list">
              {group.members.map((member) => (
                <li key={member.id} className="member-item">
                  <span className="member-avatar" aria-hidden="true">
                    {member.name.slice(0, 1)}
                  </span>
                  <span className="member-name">
                    {member.name}
                    {member.id === group.ownerId && <span className="member-owner-tag">소유자</span>}
                  </span>
                  <span className="member-joined">{member.joinedAt} 참여</span>
                  {isOwner && member.id !== group.ownerId && (
                    <button
                      type="button"
                      className="member-remove"
                      onClick={() => setConfirming(member.id)}
                    >
                      제외
                    </button>
                  )}
                </li>
              ))}
            </ul>
          </section>

          {isOwner && (
            <section className="group-section">
              <h2 className="group-section-title">초대 링크</h2>
              {isFull ? (
                <p className="detail-empty">
                  정원({GROUP_MEMBER_LIMIT}명)이 가득 차 새로 초대할 수 없습니다.
                </p>
              ) : invite ? (
                <>
                  <div className="invite-link-row">
                    <input type="text" readOnly value={buildInviteUrl(invite.token)} />
                    <button type="button" className="btn-secondary" onClick={handleCopy}>
                      <LinkIcon />
                      {copied ? '복사됨' : '복사'}
                    </button>
                  </div>
                  <p className="invite-expiry">{formatDate(invite.expiresAt)}까지 유효합니다.</p>
                  <div className="invite-actions">
                    <button type="button" className="link-btn" onClick={handleIssue}>
                      새 링크 만들기
                    </button>
                    <button
                      type="button"
                      className="link-btn"
                      onClick={() => {
                        revokeInvite(group.id)
                        setCopied(false)
                      }}
                    >
                      링크 폐기
                    </button>
                  </div>
                  {/* 재발급이 이전 링크를 죽인다는 사실을 버튼 옆에서 알려야 한다. */}
                  <p className="invite-note">
                    새 링크를 만들면 이전 링크는 사용할 수 없습니다.
                  </p>
                </>
              ) : (
                <>
                  <button type="button" className="btn-primary" onClick={handleIssue}>
                    초대 링크 만들기
                  </button>
                  <p className="invite-note">
                    링크는 만든 뒤 {INVITE_TTL_DAYS}일 동안 유효하고, 그룹당 하나만 살아 있습니다.
                  </p>
                </>
              )}
            </section>
          )}

          <section className="group-section group-danger-zone">
            {isOwner ? (
              <button
                type="button"
                className="detail-delete-btn"
                onClick={() => setConfirming('delete')}
              >
                그룹 삭제
              </button>
            ) : (
              <button
                type="button"
                className="detail-delete-btn"
                onClick={() => setConfirming('leave')}
              >
                그룹 나가기
              </button>
            )}
          </section>
        </div>
      </main>

      {confirming != null && (
        <div className="modal-overlay" onClick={() => setConfirming(null)}>
          <div
            className="modal-panel confirm-panel"
            role="dialog"
            aria-modal="true"
            aria-label={confirmText.title}
            onClick={(e) => e.stopPropagation()}
          >
            <h2>{confirmText.title}</h2>
            <p className="confirm-desc">{confirmText.desc}</p>
            <div className="confirm-actions">
              <button type="button" className="confirm-cancel" onClick={() => setConfirming(null)}>
                취소
              </button>
              <button type="button" className="confirm-ok" onClick={confirmAction}>
                {confirmText.ok}
              </button>
            </div>
          </div>
        </div>
      )}
    </>
  )
}
