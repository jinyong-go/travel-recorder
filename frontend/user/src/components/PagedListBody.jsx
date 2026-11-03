import { REQUEST_STATUS } from '../api/requestStatus.js'

/** "더 보기" 목록의 본문 — 항목, 불러오는 중·실패·빈 목록 안내, "더 보기" 버튼 */
export default function PagedListBody({
  list,
  loadingText,
  errorText,
  emptyText,
  noteClassName,
  errorClassName,
  showMore = true,
  children,
}) {
  const { items, status, hasNext, loadMore, reload } = list
  const hasItems = items.length > 0

  return (
    <>
      {status === REQUEST_STATUS.ERROR && (
        <p className={errorClassName}>
          {errorText}{' '}
          <button type="button" className="link-button" onClick={reload}>
            다시 시도
          </button>
        </p>
      )}
      {hasItems && children}
      {status === REQUEST_STATUS.LOADING && !hasItems && loadingText && (
        <p className={noteClassName}>{loadingText}</p>
      )}
      {status === REQUEST_STATUS.COMPLETE && !hasItems && emptyText && (
        <p className={noteClassName}>{emptyText}</p>
      )}
      {showMore && hasNext && status === REQUEST_STATUS.COMPLETE && (
        <button type="button" className="btn-secondary" onClick={loadMore}>
          더 보기
        </button>
      )}
    </>
  )
}
