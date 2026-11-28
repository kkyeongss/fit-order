import { useState, type FormEvent } from 'react'
import { api, type User } from '../api'
import { useAction } from '../hooks'
import { Notice } from '../ui'

const DEMO = [
  { username: 'user', password: 'user1234', label: '담당자(USER)', note: '조회 + 단가·재고·발주안 입력' },
  { username: 'manager', password: 'manager1234', label: '부서장(MANAGER)', note: '조회 전용 + 변경 기록 (입력은 못 함)' },
  { username: 'admin', password: 'admin1234', label: '관리자(ADMIN)', note: '담당자 화면 + 데이터 관리·계정·ESG 항목' },
]

export default function Login({ onLogin }: { onLogin: (u: User) => void }) {
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const { busy, error, run } = useAction()

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    const user = await run(() => api.login(username.trim(), password))
    if (user) {
      window.location.hash = '/dashboard'
      onLogin(user)
    }
  }

  return (
    <div className="login-wrap">
      <div className="login-card">
        <h1>
          <span className="brand-mark" style={{ background: 'var(--navy)', color: '#fff' }}>
            F
          </span>
          핏오더
        </h1>
        <p className="muted">공공 근무복 주문 데이터로 사이즈별 발주량을 추천하고, 과잉 발주를 줄인 효과를 ESG 지표로 보여줍니다.</p>
        <form onSubmit={submit}>
          <label className="field">
            아이디
            <input type="text" value={username} onChange={(e) => setUsername(e.target.value)} autoComplete="username" autoFocus required />
          </label>
          <label className="field">
            비밀번호
            <input
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              autoComplete="current-password"
              required
            />
          </label>
          {error && <Notice tone="bad">{error}</Notice>}
          <button className="primary" disabled={busy}>
            {busy ? '확인하는 중…' : '로그인'}
          </button>
        </form>
        <div className="demo">
          <strong>시연 계정</strong>
          {DEMO.map((d) => (
            <div className="demo-row" key={d.username}>
              <div>
                <code>{d.username}</code> / <code>{d.password}</code> · {d.label}
                <div className="muted">{d.note}</div>
              </div>
              <button
                type="button"
                className="secondary"
                onClick={() => {
                  setUsername(d.username)
                  setPassword(d.password)
                }}
              >
                채우기
              </button>
            </div>
          ))}
        </div>
      </div>
    </div>
  )
}
