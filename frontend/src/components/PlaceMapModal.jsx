import { buildMapsSearchUrl } from '../config/mapSettings.js'
import NaverMapView from './NaverMapView.jsx'
import { CloseIcon } from './icons.jsx'

// 지도 임베드 모달. 목록·상세 양쪽에서 같은 모양으로 쓴다.
export default function PlaceMapModal({ place, onClose }) {
  return (
    <div className="modal-overlay" onClick={onClose}>
      <div
        className="modal-panel map-modal-panel"
        role="dialog"
        aria-modal="true"
        aria-label={`${place.name} 지도`}
        onClick={(e) => e.stopPropagation()}
      >
        <button type="button" className="modal-close" onClick={onClose} aria-label="닫기">
          <CloseIcon />
        </button>
        <h2>{place.name}</h2>
        <p className="modal-desc">{place.region}</p>
        <NaverMapView place={place} />
        <a
          className="map-embed-link"
          href={buildMapsSearchUrl(place)}
          target="_blank"
          rel="noopener noreferrer"
        >
          새 창에서 네이버 지도로 열기
        </a>
      </div>
    </div>
  )
}
