import './UserAvatar.css'

/**
 * 사용자 프로필 사진. 사진이 없으면 이름 첫 글자로 대신한다. 이름이 늘 바로 옆에 적히는 자리에서만
 * 쓰므로 대체 텍스트를 비우고 보조기술에는 읽히지 않게 한다.
 *
 * @param size 지름(px). 첫 글자 크기도 이에 맞춰 줄어든다.
 */
export default function UserAvatar({ user, size = 32 }) {
  const style = { width: size, height: size, fontSize: Math.round(size * 0.42) }
  return user.profileImageUrl ? (
    <img className="user-avatar" src={user.profileImageUrl} alt="" style={style} />
  ) : (
    <span className="user-avatar user-avatar-fallback" aria-hidden="true" style={style}>
      {user.name.slice(0, 1)}
    </span>
  )
}
