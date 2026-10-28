import { useState } from 'react'
import { Link } from 'react-router-dom'
import { createGroup as createGroupApi, fetchGroups, fetchReceivedInvites } from '../api/groups.js'
import { errorMessage } from '../api/client.js'
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

/** 그룹 만들기 실패 안내 */
const CREATE_ERROR = {
  GROUP_LIMIT_EXCEEDED: `그룹은 ${OWNED_GROUP_LIMIT}개까지 만들 수 있어요.`,
}

/** 받은 초대 건수 조회 */
const loadReceivedInvites = () => fetchReceivedInvites(0)

/** 그룹 목록과 그룹 만들기 */
export default function GroupsPage() {
  const groups = usePagedList(fetchGroups)

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
  const createPanelRef = useModal(closeCreate, creating)

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
      await createGroupApi(trimmed, memo.trim() || null)
      await groups.reload()
      closeCreate()
    } catch (err) {
      setError(errorMessage(err, CREATE_ERROR, '그룹을 만들지 못했어요.'))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <>
      <AppHeader receivedCount={receivedCount} />

      <main className="page">
        <div className="page-inner">
          <Link to="/trips" className="back-link">
            <ArrowLeftIcon /> 여행 목록으로
          </Link>

          {/* 받은 초대 알림 */}
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
