import { useState } from 'react'
import { DEFAULT_VISIBILITY } from '../data/visibility.js'
import { tripDurationLabel } from '../data/trips.js'
import VisibilitySelect from './VisibilitySelect.jsx'
import RequiredNote from './RequiredNote.jsx'
import { TRIP_MEMO_MAX_LENGTH, TRIP_NAME_MAX_LENGTH } from '../config/inputLimits.js'
import '../styles/form.css'
import './TripForm.css'

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

/** 여행 입력 폼 — 만들기와 수정이 함께 쓴다 */
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

  // 날짜 순서가 뒤집혔는지
  const dateOrderInvalid = Boolean(startDate && endDate && endDate < startDate)
  const endDateError = dateOrderInvalid ? DATE_ORDER_ERROR : errors.endDate

  // 여행 기간(박·일)
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

      {showVisibility && (
        <VisibilitySelect
          value={visibility}
          onChange={setVisibility}
          selectedGroupIds={sharedGroupIds}
          onChangeGroups={setSharedGroupIds}
          onCreateGroupClick={onCreateGroupClick}
        />
      )}


      {error && <p className="field-error">{error}</p>}

      <div className="form-actions">
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
