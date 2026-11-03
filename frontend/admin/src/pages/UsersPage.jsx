import { useCallback, useState } from 'react'
import { fetchUsers } from '../api/users.js'
import { REQUEST_STATUS } from '../api/requestStatus.js'
import usePagedList from '../hooks/usePagedList.js'
import './UsersPage.css'

const formatDate = (iso) => (iso ? new Date(iso).toLocaleDateString('ko-KR') : '—')

/** 가입한 사용자를 최근 가입순으로 보여 주고 이름·이메일로 찾는다 */
export default function UsersPage() {
  const [input, setInput] = useState('')
  const [query, setQuery] = useState('')
  const load = useCallback((page) => fetchUsers(query, page), [query])
  const users = usePagedList(load)
  const hasItems = users.items.length > 0

  const handleSubmit = (e) => {
    e.preventDefault()
    setQuery(input)
  }

  return (
    <>
      <h1 className="page-title">사용자</h1>
      <section className="panel">
        <form className="users-search" role="search" onSubmit={handleSubmit}>
          <label htmlFor="users-search-input" className="sr-only">
            이름 또는 이메일
          </label>
          <input
            id="users-search-input"
            type="search"
            className="users-search-input"
            placeholder="이름 또는 이메일"
            value={input}
            onChange={(e) => setInput(e.target.value)}
          />
          <button type="submit" className="btn-secondary">
            검색
          </button>
        </form>

        {users.total !== undefined && <p className="note users-count">{users.total}명</p>}

        {users.status === REQUEST_STATUS.ERROR && (
          <p className="error-text">
            사용자 목록을 불러오지 못했어요.{' '}
            <button type="button" className="link-button" onClick={users.reload}>
              다시 시도
            </button>
          </p>
        )}

        {hasItems && (
          <div className="table-scroll">
            <table className="data-table">
              <thead>
                <tr>
                  <th scope="col" className="num">
                    ID
                  </th>
                  <th scope="col">이름</th>
                  <th scope="col">이메일</th>
                  <th scope="col">가입일</th>
                  <th scope="col">마지막 로그인</th>
                </tr>
              </thead>
              <tbody>
                {users.items.map((u) => (
                  <tr key={u.id}>
                    <td className="num">{u.id}</td>
                    <td>{u.name}</td>
                    <td>{u.email}</td>
                    <td>{formatDate(u.createdAt)}</td>
                    <td>{formatDate(u.lastLoginAt)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

        {users.status === REQUEST_STATUS.LOADING && !hasItems && <p className="note">불러오는 중…</p>}
        {users.status === REQUEST_STATUS.COMPLETE && !hasItems && (
          <p className="note">{query ? '찾는 사용자가 없어요.' : '가입한 사용자가 없어요.'}</p>
        )}
        {users.hasNext && users.status === REQUEST_STATUS.COMPLETE && (
          <button type="button" className="btn-secondary users-more" onClick={users.loadMore}>
            더 보기
          </button>
        )}
      </section>
    </>
  )
}
