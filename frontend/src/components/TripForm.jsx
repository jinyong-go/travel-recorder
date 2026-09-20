import { useState } from 'react'
import { DEFAULT_VISIBILITY } from '../data/records.js'
import { tripDurationLabel } from '../data/trips.js'
import VisibilitySelect from './VisibilitySelect.jsx'
import './TripForm.css'

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
 * `onSubmit` 에는 검증을 통과한 값만 넘어온다. 예산은 비어 있으면 `null` 이며 이는 "예산 정보
 * 없음"으로 `0` 과 구분된다 (공통 명세 §3.2).
 */
export default function TripForm({
  initialTrip,
  submitLabel,
  showVisibility = false,
  groups,
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

  // 날짜를 잘못 골랐을 때 저장 전에 알아차리도록 즉시 계산해 보여준다.
  const durationLabel =
    startDate && endDate && endDate >= startDate ? tripDurationLabel({ startDate, endDate }) : ''

  const validate = () => {
    const next = {}
    if (!name.trim()) next.name = '여행 이름을 입력해주세요.'
    else if (name.trim().length > 50) next.name = '여행 이름은 50자 이하로 입력해주세요.'
    if (!startDate) next.startDate = '시작일을 선택해주세요.'
    if (!endDate) next.endDate = '종료일을 선택해주세요.'
    else if (startDate && endDate < startDate) next.endDate = '종료일은 시작일 이후여야 합니다.'
    if (!headcount || Number(headcount) < 1) next.headcount = '인원은 1명 이상이어야 합니다.'
    if (budget !== '' && Number(budget) < 0) next.budget = '예산은 0 이상이어야 합니다.'
    if (memo.length > 1000) next.memo = '여행 설명은 1000자 이하로 입력해주세요.'
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
      <div className="form-field">
        <label htmlFor="trip-name">여행 이름</label>
        <input
          id="trip-name"
          type="text"
          value={name}
          maxLength={50}
          placeholder="예: 제주 3박 4일"
          onChange={(e) => setName(e.target.value)}
        />
        {errors.name && <p className="field-error">{errors.name}</p>}
      </div>

      <div className="form-field">
        <label htmlFor="trip-start">시작일</label>
        <input
          id="trip-start"
          type="date"
          value={startDate}
          onChange={(e) => setStartDate(e.target.value)}
        />
        {errors.startDate && <p className="field-error">{errors.startDate}</p>}
      </div>

      <div className="form-field">
        <label htmlFor="trip-end">종료일</label>
        <input
          id="trip-end"
          type="date"
          value={endDate}
          onChange={(e) => setEndDate(e.target.value)}
        />
        {errors.endDate && <p className="field-error">{errors.endDate}</p>}
        <p className="field-hint" aria-live="polite">
          {durationLabel}
        </p>
      </div>

      <div className="form-field">
        <label htmlFor="trip-headcount">인원</label>
        <input
          id="trip-headcount"
          type="number"
          min={1}
          value={headcount}
          onChange={(e) => setHeadcount(e.target.value)}
        />
        {errors.headcount && <p className="field-error">{errors.headcount}</p>}
        {/* 인원과 공유 그룹을 혼동하는 것이 이 화면에서 가장 흔한 오해다. */}
        <p className="field-hint">함께 간 사람 수입니다. 공유 대상과는 무관합니다.</p>
      </div>

      <div className="form-field">
        <label htmlFor="trip-budget">예산 (선택)</label>
        <input
          id="trip-budget"
          type="number"
          min={0}
          value={budget}
          placeholder="원 단위"
          onChange={(e) => setBudget(e.target.value)}
        />
        {errors.budget && <p className="field-error">{errors.budget}</p>}
        <p className="field-hint">
          여행 전체 총액 하나만 받습니다. 이 여행을 볼 수 있는 사람에게 함께 보입니다.
        </p>
      </div>

      <div className="form-field">
        <label htmlFor="trip-memo">여행 설명 (선택)</label>
        <textarea
          id="trip-memo"
          rows={4}
          value={memo}
          maxLength={1000}
          placeholder="어떤 여행이었는지 적어보세요"
          onChange={(e) => setMemo(e.target.value)}
        />
        {errors.memo && <p className="field-error">{errors.memo}</p>}
      </div>

      {showVisibility && (
        <div className="form-field">
          <VisibilitySelect
            value={visibility}
            onChange={setVisibility}
            groups={groups}
            selectedGroupIds={sharedGroupIds}
            onChangeGroups={setSharedGroupIds}
            onCreateGroupClick={onCreateGroupClick}
          />
          <p className="field-hint">여행을 공유하면 그 안의 여행지와 예산이 모두 함께 보입니다.</p>
        </div>
      )}

      {/* 대표 사진은 이 폼에 없다. 하위 기록의 사진 중에서 고르는 값이다 (공통 명세 §3.2). */}

      <div className="form-actions">
        <button type="button" className="btn-secondary" onClick={onCancel}>
          취소
        </button>
        <button type="submit" className="btn-primary">
          {submitLabel}
        </button>
      </div>
    </form>
  )
}
