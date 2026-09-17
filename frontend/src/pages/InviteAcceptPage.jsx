import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { useRecords } from '../context/RecordsContext.jsx'
import useTheme from '../hooks/useTheme.js'
import ThemeSelector from '../components/ThemeSelector.jsx'
import { ArrowLeftIcon, MapPinIcon, UsersIcon } from '../components/icons.jsx'
import './GroupsPage.css'

// TODO: 로그인 연동 후 실제 인증 상태로 교체
const IS_LOGGED_IN = true

const ERROR_MESSAGE = {
  NOT_FOUND: '유효하지 않은 초대 링크입니다.',
  // 만료와 부재를 구분해 알린다. 만료는 "새 링크를 요청하세요" 로 이어질 수 있는 상황이라서다.
  EXPIRED: '초대 링크가 만료되었습니다. 초대한 분께 새 링크를 요청해주세요.',
  LIMIT: '그룹 정원(5명)이 가득 찼습니다.',
}

const formatDate = (iso) => new Date(iso).toLocaleDateString('ko-KR')

export default function InviteAcceptPage() {
  const { token } = useParams()
  const navigate = useNavigate()
  const { findInvite, acceptInvite } = useRecords()
  const { themeKey, changeTheme } = useTheme()
  const [error, setError] = useState('')

  const invite = findInvite(token)

  const handleAccept = () => {
    const result = acceptInvite(token)
    if (result.error) {
      setError(ERROR_MESSAGE[result.error] ?? '초대를 수락하지 못했습니다.')
      return
    }
    navigate(`/groups/${result.groupId}`)
  }

  const body = () => {
    if (!invite) return <p className="invite-error">{ERROR_MESSAGE.NOT_FOUND}</p>
    if (invite.expired) return <p className="invite-error">{ERROR_MESSAGE.EXPIRED}</p>

    return (
      <>
        <p className="invite-target">
          <strong>{invite.group.name}</strong> 그룹 초대
        </p>
        <p className="invite-expiry">{formatDate(invite.expiresAt)}까지 참여할 수 있어요.</p>
        <p className="groups-desc">
          참여하면 이 그룹으로 공유된 기록을 볼 수 있습니다. 다른 사람의 기록을 수정하거나
          삭제할 수는 없어요.
        </p>
        {error && <p className="invite-error">{error}</p>}
        {IS_LOGGED_IN ? (
          <button type="button" className="btn-primary" onClick={handleAccept}>
            참여하기
          </button>
        ) : (
          // 로그인 후 이 페이지로 돌아와야 한다. 되돌아올 경로를 쿼리에 실어 보낸다.
          <Link
            to={`/login?redirect=${encodeURIComponent(`/invites/${token}`)}`}
            className="btn-primary"
          >
            로그인하고 참여하기
          </Link>
        )}
      </>
    )
  }

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
        <div className="groups-inner invite-panel">
          <span className="invite-icon" aria-hidden="true">
            <UsersIcon />
          </span>
          <h1 className="groups-title">그룹 초대</h1>
          {body()}
          <Link to="/records" className="back-link">
            <ArrowLeftIcon /> 기록 목록으로
          </Link>
        </div>
      </main>
    </>
  )
}
