import { useState } from 'react'
import { DEFAULT_VISIBILITY } from '../data/visibility.js'
import { tripDurationLabel } from '../data/trips.js'
import VisibilitySelect from './VisibilitySelect.jsx'
import RequiredNote from './RequiredNote.jsx'
import { TRIP_MEMO_MAX_LENGTH, TRIP_NAME_MAX_LENGTH } from '../config/inputLimits.js'
import '../styles/form.css'
import './TripForm.css'

/**
 * 백엔드 요청 검증과 같은 값이다.
 * 제출 검증과 즉시 표시가 같은 문구를 쓴다.
 */
const DATE_ORDER_ERROR = '종료일은 시작일 이후여야 해요.'

const EMPTY = {
  name: '',
  startDate: '',
  endDate: '',
  headcount: 1,
  budget: '',
  memo: '',
  visibility: DEFAULT_VISIBILITY,
  sharedGroupIds: [],
}

/**
 * 여행 입력 폼. 만들기와 수정이 같은 필드·검증을 쓴다.
 *
 * 공개 범위는 만들기에서만 받는다(`showVisibility`). 수정에서는 여행 상세의 공개 범위 섹션이
 * 그 역할을 하므로, 같은 값을 두 곳에서 바꿀 수 있게 만들지 않는다.
 *
 * `submitting` 동안은 제출·취소 버튼을 막고 문구를 `submittingLabel` 로 바꾼다. 두 번 눌러 여행이
 * 둘 생기는 것을 막고, 눌렸다는 것을 보이기 위해서다.
 *
 * `error` 는 제출 결과의 오류로, 버튼 바로 위에 보인다.
 *
 * `onSubmit` 에는 검증을 통과한 값만 넘어온다. 예산은 비어 있으면 `null` 이며 이는 "예산 정보
 * 없음"으로 `0` 과 구분된다.
 */
export default function TripForm({
  initialTrip,
  submitLabel,
  submittingLabel,
  showVisibility = false,
  submitting = false,
  error,
  onSubmit,
  onCancel,
  onCreateGroupClick,
}) {
  const initial = initialTrip ? { ...EMPTY, ...initialTrip } : EMPTY
  const [name, setName] = useState(initial.name)
  const [startDate, setStartDate] = useState(initial.startDate)
  const [endDate, setEndDate] = useState(initial.endDate)
  const [headcount, setHeadcount] = useState(initial.headcount)
  const [budget, setBudget] = useState(initial.budget ?? '')
  const [memo, setMemo] = useState(initial.memo ?? '')
  const [visibility, setVisibility] = useState(initial.visibility)
  const [sharedGroupIds, setSharedGroupIds] = useState(initial.sharedGroupIds)
  const [errors, setErrors] = useState({})

  // 두 날짜가 다 있고 순서가 뒤집혔으면 제출 전에도 바로 알린다. 달력의 min 만으로는 직접 입력과
  // "종료일을 정한 뒤 시작일을 뒤로 옮기는" 경우를 막지 못한다.
  const dateOrderInvalid = Boolean(startDate && endDate && endDate < startDate)
  // 순서 오류는 제출 전에도 보인다. 그 밖의 오류(미입력)는 제출할 때만 보인다.
  const endDateError = dateOrderInvalid ? DATE_ORDER_ERROR : errors.endDate

  // 날짜를 잘못 골랐을 때 저장 전에 알아차리도록 즉시 계산해 보여준다.
  const durationLabel =
    startDate && endDate && !dateOrderInvalid ? tripDurationLabel({ startDate, endDate }) : ''

  const validate = () => {
    const next = {}
    if (!name.trim()) next.name = '여행 이름을 입력해주세요.'
    else if (name.trim().length > TRIP_NAME_MAX_LENGTH) next.name = `여행 이름은 ${TRIP_NAME_MAX_LENGTH}자 이하로 입력해주세요.`
    if (!startDate) next.startDate = '시작일을 선택해주세요.'
    if (!endDate) next.endDate = '종료일을 선택해주세요.'
    else if (dateOrderInvalid) next.endDate = DATE_ORDER_ERROR
    if (!headcount || Number(headcount) < 1) next.headcount = '인원은 1명 이상이어야 해요.'
    if (budget !== '' && Number(budget) < 0) next.budget = '예산은 0 이상이어야 해요.'
    if (memo.length > TRIP_MEMO_MAX_LENGTH) next.memo = `여행 설명은 ${TRIP_MEMO_MAX_LENGTH}자 이하로 입력해주세요.`
    setErrors(next)
    return Object.keys(next).length === 0
  }

  const handleSubmit = (e) => {
    e.preventDefault()
    if (!validate()) return

    const values = {
      name: name.trim(),
      startDate,
      endDate,
      headcount: Number(headcount),
      budget: budget === '' ? null : Number(budget),
      memo: memo.trim(),
    }
    onSubmit(showVisibility ? { ...values, visibility, sharedGroupIds } : values)
  }

  return (
    <form className="trip-form" onSubmit={handleSubmit} noValidate>
      <RequiredNote />

      <div className="form-field">
        <label htmlFor="trip-name" className="field-required">여행 이름</label>
        <input
          id="trip-name"
          aria-required="true"
          type="text"
          value={name}
          maxLength={TRIP_NAME_MAX_LENGTH}
          placeholder="예: 제주 3박 4일"
          onChange={(e) => setName(e.target.value)}
        />
        {errors.name && <p className="field-error">{errors.name}</p>}
      </div>

      {/* 짝을 이루는 두 입력은 넓은 화면에서 한 줄에 둔다. 폼 길이가 줄어 공개 범위까지 덜 내려간다. */}
      <div className="form-row">
        <div className="form-field">
          <label htmlFor="trip-start" className="field-required">시작일</label>
          <input
            id="trip-start"
            aria-required="true"
            type="date"
            value={startDate}
            onChange={(e) => setStartDate(e.target.value)}
          />
          {errors.startDate && <p className="field-error">{errors.startDate}</p>}
        </div>

        <div className="form-field">
          <label htmlFor="trip-end" className="field-required">종료일</label>
          <input
            id="trip-end"
            aria-required="true"
            type="date"
            // 달력에서 시작일 이전 날짜를 처음부터 고를 수 없게 한다.
            min={startDate || undefined}
            value={endDate}
            onChange={(e) => setEndDate(e.target.value)}
          />
          {endDateError && <p className="field-error">{endDateError}</p>}
          <p className="field-hint" aria-live="polite">
            {durationLabel}
          </p>
        </div>
      </div>

      <div className="form-row">
        <div className="form-field">
          <label htmlFor="trip-headcount" className="field-required">인원</label>
          <input
            id="trip-headcount"
            aria-required="true"
            type="number"
            min={1}
            value={headcount}
            onChange={(e) => setHeadcount(e.target.value)}
          />
          {errors.headcount && <p className="field-error">{errors.headcount}</p>}
          {/* 인원과 공유 그룹을 혼동하는 것이 이 화면에서 가장 흔한 오해다. */}
          <p className="field-hint">함께 간 사람 수예요 (공유 대상과 무관)</p>
        </div>

        <div className="form-field">
          <label htmlFor="trip-budget">예산</label>
          <input
            id="trip-budget"
            type="number"
            min={0}
            value={budget}
            placeholder="원 단위"
            onChange={(e) => setBudget(e.target.value)}
          />
          {errors.budget && <p className="field-error">{errors.budget}</p>}
          <p className="field-hint">여행 전체 총액이에요. 여행을 볼 수 있는 사람에게 함께 보여요.</p>
        </div>
      </div>

      <div className="form-field">
        <label htmlFor="trip-memo">여행 설명</label>
        <textarea
          id="trip-memo"
          rows={4}
          value={memo}
          maxLength={TRIP_MEMO_MAX_LENGTH}
          placeholder="어떤 여행이었는지 적어보세요"
          onChange={(e) => setMemo(e.target.value)}
        />
        {errors.memo && <p className="field-error">{errors.memo}</p>}
      </div>

      {/* 공유하면 여행지와 예산이 함께 보인다는 경고는 선택지 설명이 맡는다. */}
      {showVisibility && (
        <VisibilitySelect
          value={visibility}
          onChange={setVisibility}
          selectedGroupIds={sharedGroupIds}
          onChangeGroups={setSharedGroupIds}
          onCreateGroupClick={onCreateGroupClick}
        />
      )}

      {/* 대표 사진은 이 폼에 없다. 하위 기록의 사진 중에서 고르는 값이다. */}

      {error && <p className="field-error">{error}</p>}

      <div className="form-actions">
        {/* 만드는 도중에 떠나면 여행이 생겼는지 알 수 없게 되므로 제출 중에는 취소도 막는다. */}
        <button type="button" className="btn-secondary" onClick={onCancel} disabled={submitting}>
          취소
        </button>
        <button type="submit" className="btn-primary" disabled={submitting}>
          {submitting ? submittingLabel : submitLabel}
        </button>
      </div>
    </form>
  )
}
