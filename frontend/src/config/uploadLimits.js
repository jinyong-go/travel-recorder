// 사진 업로드 제한. 백엔드 제한(application.yml의 spring.servlet.multipart,
// app.upload)과 같은 값을 유지해야 하며, 환경변수로 덮어쓸 수 있다.
const positiveNumber = (raw, fallback) => {
  const parsed = Number(raw)
  return Number.isFinite(parsed) && parsed > 0 ? parsed : fallback
}

const MB = 1024 * 1024

/** 사진 1장당 최대 용량(MB) — 백엔드 max-file-size 와 동일해야 한다. */
export const MAX_PHOTO_SIZE_MB = positiveNumber(import.meta.env.VITE_MAX_PHOTO_SIZE_MB, 5)

/** 한 번의 등록 요청에 담을 수 있는 전체 용량(MB) — 백엔드 max-request-size 와 동일해야 한다. */
export const MAX_PHOTO_TOTAL_MB = positiveNumber(import.meta.env.VITE_MAX_PHOTO_TOTAL_MB, 30)

export const MAX_PHOTO_SIZE_BYTES = MAX_PHOTO_SIZE_MB * MB
export const MAX_PHOTO_TOTAL_BYTES = MAX_PHOTO_TOTAL_MB * MB

export const ALLOWED_PHOTO_TYPES = ['image/jpeg', 'image/png', 'image/webp']
export const ALLOWED_PHOTO_ACCEPT = ALLOWED_PHOTO_TYPES.join(',')

export const formatMb = (bytes) => `${(bytes / MB).toFixed(1)}MB`

/**
 * 선택된 파일을 형식·용량 기준으로 걸러낸다.
 * @param files 새로 선택한 File 목록
 * @param selectedBytes 이미 첨부되어 있는 파일들의 합계 용량
 * @returns accepted: 통과한 File 목록, errors: 사용자에게 보여줄 메시지 목록
 */
export const validatePhotoFiles = (files, selectedBytes = 0) => {
  const accepted = []
  const errors = []
  let total = selectedBytes

  files.forEach((file) => {
    if (!ALLOWED_PHOTO_TYPES.includes(file.type)) {
      errors.push(`${file.name}: JPG · PNG · WEBP 형식만 업로드할 수 있습니다.`)
      return
    }
    if (file.size > MAX_PHOTO_SIZE_BYTES) {
      errors.push(
        `${file.name}: ${formatMb(file.size)} — 한 장당 ${MAX_PHOTO_SIZE_MB}MB 이하만 업로드할 수 있습니다.`,
      )
      return
    }
    if (total + file.size > MAX_PHOTO_TOTAL_BYTES) {
      errors.push(`${file.name}: 전체 첨부 용량 ${MAX_PHOTO_TOTAL_MB}MB를 초과합니다.`)
      return
    }
    total += file.size
    accepted.push(file)
  })

  return { accepted, errors }
}
