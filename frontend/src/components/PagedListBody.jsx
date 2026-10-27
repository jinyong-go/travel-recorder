import { REQUEST_STATUS } from '../api/requestStatus.js'

/**
 * `usePagedList` 목록 하나의 자리 — 불러오는 중·실패·빈 목록 안내와 "더 보기" 를 그린다.
 * 항목은 `children` 으로 받아, 한 건이라도 있으면 그대로 둔다.
 *
 * 상태 판정을 한곳에 둔다. 화면마다 따로 짜면 실패한 목록 아래에 빈 목록이 함께 그려지거나,
 * 불러오는 중에 "더 보기" 를 또 누를 수 있는 식으로 화면끼리 동작이 어긋난다.
 *
 * - 실패하면 이미 받은 항목은 남기고 다시 시도(처음부터 다시 읽기)를 단다.
 * - 불러오는 중 안내는 항목이 없을 때만 보인다. 쓰기 뒤 다시 읽을 때마다 목록 아래에서 깜빡이지
 *   않게 한다.
 * - "더 보기" 는 조회가 끝난 상태에서만 보인다. `showMore` 를 끄면 그리지 않는다 — 첫 페이지만
 *   보여 주고 전용 화면으로 보내는 곳(내 정보의 최근 로그인)이다.
 * - `loadingText`·`emptyText` 를 비우면 그 안내는 그리지 않는다.
 *
 * @param list `usePagedList` 의 반환값
 */
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
        // 목록 끝에서 다음 동작이 눈에 띄어야 하므로 문장 속 링크가 아니라 버튼 모양으로 둔다.
        <button type="button" className="btn-secondary" onClick={loadMore}>
          더 보기
        </button>
      )}
    </>
  )
}
