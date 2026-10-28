import './UserAvatar.css'

/** 프로필 사진. 없으면 이름 첫 글자를 보인다. */
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
