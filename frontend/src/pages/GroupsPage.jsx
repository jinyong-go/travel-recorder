import { useState } from 'react'
import { Link } from 'react-router-dom'
import { createGroup as createGroupApi, fetchGroups, fetchReceivedInvites } from '../api/groups.js'
import { ApiError } from '../api/client.js'
import useModal from '../hooks/useModal.js'
import usePagedList from '../hooks/usePagedList.js'
import useResource from '../hooks/useResource.js'
import AppHeader from '../components/AppHeader.jsx'
import PagedListBody from '../components/PagedListBody.jsx'
import RequiredNote from '../components/RequiredNote.jsx'
import { ArrowLeftIcon, PlusIcon } from '../components/icons.jsx'
import '../styles/page.css'
import '../styles/form.css'
import '../styles/groupPages.css'
import './GroupsPage.css'
import { GROUP_MEMO_MAX_LENGTH, GROUP_NAME_MAX_LENGTH } from '../config/inputLimits.js'
import { OWNED_GROUP_LIMIT } from '../config/groupLimits.js'


/** 받은 초대 첫 페이지. 건수(`totalElements`)만 쓴다. */
const loadReceivedInvites = () => fetchReceivedInvites(0)

/**
 * 내가 만들었거나 참여 중인 그룹 목록과 새 그룹 만들기.
 *
 * 받은 초대는 목록으로 그리지 않고 건수만 알려 초대함으로 보낸다.
 */
export default function GroupsPage() {
  // 그룹을 만든 뒤에도 처음부터 다시 읽는다. 실패 사유는 나누지 않는다 — 목록을 못 받았다는 사실만
  // 화면에 필요하다.
  const groups = usePagedList(fetchGroups)

  /** 받은 초대 알림 줄의 건수. 못 읽으면 알림 줄을 그리지 않을 뿐이다. */
  const receivedCount = useResource(loadReceivedInvites).data?.totalElements ?? 0

  const [creating, setCreating] = useState(false)
  const [name, setName] = useState('')
  const [memo, setMemo] = useState('')
  const [error, setError] = useState('')
  const [submitting, setSubmitting] = useState(false)

  const closeCreate = () => {
    setCreating(false)
    setName('')
    setMemo('')
    setError('')
  }
  // 생성 모달은 조건부로 붙으므로 훅은 늘 부르고 열림 여부로 켜고 끈다.
  const createPanelRef = useModal(closeCreate, creating)

  // 클라이언트 검증은 서버 검증을 대신하지 않는다. 같은 규칙을 먼저 걸러 왕복을 줄일 뿐이다.
  const handleCreate = async (e) => {
    e.preventDefault()
    const trimmed = name.trim()
    if (!trimmed) {
      setError('그룹 이름을 입력해주세요.')
      return
    }
    if (trimmed.length > GROUP_NAME_MAX_LENGTH) {
      setError(`그룹 이름은 ${GROUP_NAME_MAX_LENGTH}자 이하로 입력해주세요.`)
      return
    }

    setSubmitting(true)
    setError('')
    try {
      // 공백만 남은 메모는 "메모 없음" 과 같다. 서버도 같은 규칙이다.
      await createGroupApi(trimmed, memo.trim() || null)
      await groups.reload()
      closeCreate()
    } catch (err) {
      // 입력 규칙은 위에서 이미 걸렀으므로 서버만 아는 소유 상한만 따로 안내한다.
      setError(
        err instanceof ApiError && err.code === 'GROUP_LIMIT_EXCEEDED'
          ? `그룹은 ${OWNED_GROUP_LIMIT}개까지 만들 수 있어요.`
          : '그룹을 만들지 못했어요.',
      )
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <>
      {/* 알림 줄에 쓰려고 이미 받은 건수다. 헤더가 같은 요청을 다시 보내지 않게 넘긴다. */}
      <AppHeader receivedCount={receivedCount} />

      <main className="page">
        <div className="page-inner">
          <Link to="/trips" className="back-link">
            <ArrowLeftIcon /> 여행 목록으로
          </Link>

          {/* 같은 목록을 두 화면이 각자 그리지 않도록 여기서는 건수만 알린다. */}
          {receivedCount > 0 && (
            <Link to="/invites/received" className="invite-notice">
              받은 초대 {receivedCount}건 <span aria-hidden="true">→</span>
            </Link>
          )}

          <h1 className="page-title">공유 그룹</h1>
          <p className="groups-desc">
            그룹은 여행을 보여줄 대상 목록이에요. 멤버는 공유받은 여행을 볼 수만 있고,
            수정하거나 삭제할 수는 없어요.
          </p>

          <div className="groups-actions">
            <button type="button" className="btn-primary" onClick={() => setCreating(true)}>
              <PlusIcon />
              그룹 만들기
            </button>
          </div>

          <PagedListBody
            list={groups}
            loadingText="그룹을 불러오는 중이에요…"
            errorText="그룹을 불러오지 못했어요."
            emptyText="공유 그룹을 만들면 특정 여행을 원하는 사람에게만 보여줄 수 있어요."
            noteClassName="empty-state"
            errorClassName="empty-state"
          >
            <ul className="group-list">
              {groups.items.map((group) => (
                <li key={group.id} className="group-card">
                  <Link to={`/groups/${group.id}`} className="group-card-link">
                    <span className="group-card-name">{group.name}</span>
                    <span className={`group-role-badge${group.isOwner ? ' owner' : ''}`}>
                      {group.isOwner ? '소유자' : '멤버'}
                    </span>
                    <span className="group-card-count">
                      {group.memberCount}/{group.memberLimit}명
                    </span>
                    {group.memo && <span className="group-card-memo">{group.memo}</span>}
                  </Link>
                </li>
              ))}
            </ul>
          </PagedListBody>
        </div>
      </main>

      {creating && (
        <div className="modal-overlay" onClick={closeCreate}>
          <div
            ref={createPanelRef}
            className="modal-panel group-form-panel"
            role="dialog"
            aria-modal="true"
            aria-label="새 그룹 만들기"
            onClick={(e) => e.stopPropagation()}
          >
            <h2>새 그룹 만들기</h2>
            <form className="group-form" onSubmit={handleCreate}>
              <RequiredNote />

              <div className="form-field">
                <label htmlFor="group-name" className="field-required">이름</label>
                <input
                  id="group-name"
                  aria-required="true"
                  type="text"
                  value={name}
                  maxLength={GROUP_NAME_MAX_LENGTH}
                  autoFocus
                  placeholder="예: 가족, 제주 동행"
                  onChange={(e) => setName(e.target.value)}
                />
              </div>

              <div className="form-field">
                <label htmlFor="group-memo">메모</label>
                <textarea
                  id="group-memo"
                  value={memo}
                  maxLength={GROUP_MEMO_MAX_LENGTH}
                  rows={3}
                  placeholder="예: 설 연휴 사진 공유용"
                  onChange={(e) => setMemo(e.target.value)}
                />
                <p className="field-hint">멤버에게도 보여요.</p>
              </div>

              {error && <p className="field-error">{error}</p>}

              <div className="form-actions">
                <button type="button" className="btn-secondary" onClick={closeCreate}>
                  취소
                </button>
                <button type="submit" className="btn-primary" disabled={submitting}>
                  {submitting ? '만드는 중…' : '만들기'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </>
  )
}
