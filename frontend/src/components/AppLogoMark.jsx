import { MapPinIcon } from './icons.jsx'

/** 로고 아이콘과 서비스 이름 */
export default function AppLogoMark() {
  return (
    <>
      <MapPinIcon className="app-title-icon" />
      여행 지도 <span className="by-yong">by YONG</span>
    </>
  )
}
