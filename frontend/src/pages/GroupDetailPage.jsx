import { useCallback, useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { ApiError } from '../api/client.js'
import * as api from '../api/groups.js'
import { useGroups } from '../context/GroupsContext.jsx'
import usePagedList from '../hooks/usePagedList.js'
import useTheme from '../hooks/useTheme.js'
import ThemeSelector from '../components/ThemeSelector.jsx'
import HeaderAuth from '../components/HeaderAuth.jsx'
import { ArrowLeftIcon, MapPinIcon, PlusIcon } from '../components/icons.jsx'
import './GroupsPage.css'

const formatDate = (iso) => new Date(iso).toLocaleDateString('ko-KR')

const INVITE_ERROR = {
  USER_NOT_FOUND: '해당 이메일로 가입한 사용자가 없습니다. 주소를 다시 확인해주세요.',
  ALREADY_MEMBER: '이미 이 그룹의 멤버입니다.',
}

/**
 * 조회 실패 안내. **부재와 권한 부족을 구분해 보여 준다** — 여행·기록과 달리 그룹은 존재를
 * 숨기지 않기 때문이다 (명세 §5.8.2, backend §2.2.2).
 */
const LOAD_ERROR = {
  FORBIDDEN: { title: '이 그룹의 멤버만 볼 수 있습니다', desc: '초대를 받아 참여하면 볼 수 있어요.' },
  GROUP_NOT_FOUND: { title: '존재하지 않는 그룹입니다', desc: '주소를 다시 확인해주세요.' },
}

export default function GroupDetailPage() {
  const { groupId } = useParams()
  const navigate = useNavigate()
  const { reloadGroups } = useGroups()
  const { themeKey, changeTheme } = useTheme()

  const [group, setGroup] = useState(null)
  const [loadError, setLoadError] = useState(null)
  const [email, setEmail] = useState('')
  const [inviteMessage, setInviteMessage] = useState(null)
  const [confirming, setConfirming] = useState(null)
  const [busy, setBusy] = useState(false)

  const loadGroup = useCallback(async () => {
    setLoadError(null)
    try {
      setGroup(await api.fetchGroup(groupId))
    } catch (err) {
      setGroup(null)
      setLoadError(err instanceof ApiError ? err.code : 'UNKNOWN')
    }
  }, [groupId])

  useEffect(() => {
    loadGroup()
  }, [loadGroup])

  // 대기 초대는 소유자만 본다. 비소유자에게는 호출 자체를 하지 않는다 (명세 §5.8.3).
  const loadPending = useCallback((page) => api.fetchPendingInvites(groupId, page), [groupId])
  const pending = usePagedList(loadPending, Boolean(group?.isOwner))

  if (loadError) {
    const { title, desc } = LOAD_ERROR[loadError] ?? {
      title: '그룹을 불러오지 못했습니다',
      desc: '잠시 후 다시 시도해주세요.',
    }
    return (
      <main className="detail-page">
        <div className="detail-missing">
          <h1>{title}</h1>
          <p className="detail-missing-desc">{desc}</p>
          <Link to="/groups" className="detail-missing-link">
            <ArrowLeftIcon /> 그룹 목록으로
          </Link>
        </div>
      </main>
    )
  }

  if (!group) {
    return (
      <main className="detail-page">
        <div className="detail-missing">
          <p className="detail-missing-desc">그룹을 불러오는 중이에요…</p>
        </div>
      </main>
    )
  }

  const isOwner = group.isOwner
  const isFull = group.memberCount >= group.memberLimit
  const pendingCount = pending.items.length

  const handleSend = async (e) => {
    e.preventDefault()
    const trimmed = email.trim()
    if (!trimmed) {
      setInviteMessage({ type: 'error', text: '초대할 상대의 이메일을 입력해주세요.' })
      return
    }

    setBusy(true)
    try {
      const { status } = await api.sendInvite(group.id, trimmed)
      setEmail('')
      // 이미 초대한 상대를 다시 초대한 것은 오류가 아니다. 서버가 201 이 아닌 200 으로 답한다.
      setInviteMessage({
        type: 'info',
        text: status === 200 ? '이미 초대한 상대입니다.' : '초대를 보냈습니다.',
      })
      pending.reload()
    } catch (err) {
      const code = err instanceof ApiError ? err.code : null
      setInviteMessage({ type: 'error', text: INVITE_ERROR[code] ?? '초대를 보내지 못했습니다.' })
    } finally {
      setBusy(false)
    }
  }

  const handleRevoke = async (inviteId) => {
    setBusy(true)
    try {
      await api.revokeInvite(group.id, inviteId)
    } catch {
      // 이미 처리된 초대일 수 있다. 어느 쪽이든 목록을 다시 읽으면 실제 상태로 맞춰진다.
      setInviteMessage({ type: 'error', text: '이미 처리되었거나 취소된 초대입니다.' })
    } finally {
      pending.reload()
      setBusy(false)
    }
  }

  const confirmAction = async () => {
    setBusy(true)
    try {
      if (confirming === 'delete') {
        await api.deleteGroup(group.id)
      } else if (confirming === 'leave') {
        await api.leaveGroup(group.id)
      } else if (typeof confirming === 'number') {
        await api.removeMember(group.id, confirming)
      }
    } catch {
      setInviteMessage({ type: 'error', text: '요청을 처리하지 못했습니다. 다시 시도해주세요.' })
      setBusy(false)
      setConfirming(null)
      return
    }

    // 목록의 멤버 수·역할이 함께 바뀌므로 그룹 목록도 다시 읽는다.
    await reloadGroups()
    setConfirming(null)
    setBusy(false)
    if (confirming === 'delete' || confirming === 'leave') {
      navigate('/groups')
      return
    }
    loadGroup()
  }

  const confirmText = {
    delete: {
      title: '그룹을 삭제할까요?',
      desc: '이 그룹으로만 공유한 여행은 나만 보기 상태가 됩니다. 여행과 기록 자체는 삭제되지 않습니다.',
      ok: '삭제',
    },
    leave: {
      title: '그룹에서 나갈까요?',
      desc: '이 그룹으로 공유된 여행은 더 이상 보이지 않습니다.',
      ok: '나가기',
    },
    member: {
      title: '멤버를 제외할까요?',
      desc: '제외된 멤버는 이 그룹으로 공유된 여행을 더 이상 볼 수 없습니다.',
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
          <HeaderAuth />
        </div>
      </header>

      <main className="groups-page">
        <div className="groups-inner">
          <Link to="/groups" className="back-link">
            <ArrowLeftIcon /> 그룹 목록으로
          </Link>

          <h1 className="groups-title">{group.name}</h1>
          {group.memo && <p className="group-memo">{group.memo}</p>}
          <p className="groups-desc">
            멤버 {group.memberCount}/{group.memberLimit}명
            {isOwner && pendingCount > 0 && ` · 대기 중인 초대 ${pendingCount}건`} ·{' '}
            {isOwner ? '내가 만든 그룹' : '참여 중인 그룹'}
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
                    {member.id === group.owner.id && <span className="member-owner-tag">소유자</span>}
                  </span>
                  <span className="member-joined">{formatDate(member.joinedAt)} 참여</span>
                  {isOwner && member.id !== group.owner.id && (
                    <button
                      type="button"
                      className="member-remove"
                      disabled={busy}
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
              <h2 className="group-section-title">초대</h2>
              {/*
                정원 판정은 수락 시점에 하므로 정원이 차도 초대를 막지 않는다 (공통 명세 §3.7).
                막지 않는 대신, 보내기 전에 남은 자리와 그 결과를 먼저 알린다 (§5.8.3).
              */}
              <p className="invite-capacity">
                소유자를 포함해 최대 {group.memberLimit}명까지 참여할 수 있어요. 지금{' '}
                {group.memberCount}/{group.memberLimit}명
                {pendingCount > 0 && ` · 대기 중인 초대 ${pendingCount}건`}
                {isFull &&
                  ' — 정원이 차서 지금은 수락되지 않아요. 자리가 나면 같은 초대로 수락할 수 있어요.'}
              </p>
              {/* 자동완성이나 검색 결과를 붙이지 않는다. 가입자를 훑을 수 있는 화면이 된다 (§3.7). */}
              <form className="invite-form" onSubmit={handleSend}>
                <label className="sr-only" htmlFor="invite-email">
                  초대할 상대의 이메일
                </label>
                <input
                  id="invite-email"
                  type="email"
                  value={email}
                  placeholder="friend@example.com"
                  autoComplete="off"
                  onChange={(e) => {
                    setEmail(e.target.value)
                    setInviteMessage(null)
                  }}
                />
                <button type="submit" className="btn-primary" disabled={busy}>
                  <PlusIcon />
                  {busy ? '보내는 중…' : '초대 보내기'}
                </button>
              </form>
              <p className="invite-note">
                상대가 이 서비스에 가입할 때 쓴 이메일을 정확히 입력해주세요.
              </p>
              {inviteMessage && (
                <p className={inviteMessage.type === 'error' ? 'invite-error' : 'invite-info'}>
                  {inviteMessage.text}
                </p>
              )}

              {pending.items.length > 0 && (
                <ul className="invite-list">
                  {pending.items.map((item) => (
                    <li key={item.id} className="invite-item">
                      <span className="member-avatar" aria-hidden="true">
                        {item.invitee.name.slice(0, 1)}
                      </span>
                      <span className="member-name">{item.invitee.name}</span>
                      <span className="invite-sent-at">{formatDate(item.createdAt)} 보냄</span>
                      <button
                        type="button"
                        className="member-remove"
                        disabled={busy}
                        onClick={() => handleRevoke(item.id)}
                      >
                        초대 취소
                      </button>
                    </li>
                  ))}
                </ul>
              )}
              {/* 정원과 무관하게 초대를 보낼 수 있어 건수가 쌓인다 (명세 §5.8.3). */}
              {pending.hasNext && (
                <button type="button" className="link-button" onClick={pending.loadMore}>
                  더 보기
                </button>
              )}
            </section>
          )}

          <section className="group-section group-danger-zone">
            <button
              type="button"
              className="detail-delete-btn"
              disabled={busy}
              onClick={() => setConfirming(isOwner ? 'delete' : 'leave')}
            >
              {isOwner ? '그룹 삭제' : '그룹 나가기'}
            </button>
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
              <button type="button" className="confirm-ok" disabled={busy} onClick={confirmAction}>
                {confirmText.ok}
              </button>
            </div>
          </div>
        </div>
      )}
    </>
  )
}
