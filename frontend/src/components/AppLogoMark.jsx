import { MapPinIcon } from './icons.jsx'

/** 로고 아이콘과 서비스 이름. 감싸는 요소(제목·링크)는 쓰는 쪽이 정한다. */
export default function AppLogoMark() {
  return (
    <>
      <MapPinIcon className="app-title-icon" />
      여행 지도 <span className="by-yong">by YONG</span>
    </>
  )
}
