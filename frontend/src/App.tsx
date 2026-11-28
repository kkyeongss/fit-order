import { useCallback, useEffect, useState, type ReactNode } from 'react'
import { api, setUnauthorizedHandler, type User } from './api'
import { useBasis, useHashRoute } from './hooks'
import Admin from './pages/Admin'
import Dashboard from './pages/Dashboard'
import Items from './pages/Items'
import Login from './pages/Login'
import Recommend from './pages/Recommend'
import Sizes from './pages/Sizes'
import { Loading, Notice } from './ui'

interface Route {
  id: string
  label: string
  admin?: boolean
  render: () => ReactNode
}

const ROUTES: Route[] = [
  { id: 'dashboard', label: '대시보드', render: () => <Dashboard /> },
  { id: 'sizes', label: '사이즈 분석', render: () => <Sizes /> },
  { id: 'recommend', label: '발주 추천', render: () => <Recommend /> },
  { id: 'admin', label: '데이터 관리', admin: true, render: () => <Admin /> },
  { id: 'items', label: 'ESG 항목', admin: true, render: () => <Items /> },
]

export default function App() {
  // undefined: 확인 중, null: 로그인 안 됨
  const [user, setUser] = useState<User | null | undefined>(undefined)

  useEffect(() => {
    setUnauthorizedHandler(() => setUser(null))
    api
      .me()
      .then(setUser)
      .catch(() => setUser(null))
  }, [])

  if (user === undefined) return <Loading what="확인하는 중" />
  if (user === null) return <Login onLogin={setUser} />
  return <Shell user={user} onLogout={() => setUser(null)} />
}

function Shell({ user, onLogout }: { user: User; onLogout: () => void }) {
  const [route] = useHashRoute('dashboard')
  const { basis, setBasis } = useBasis()
  const visible = ROUTES.filter((r) => !r.admin || user.admin)
  const current = ROUTES.find((r) => r.id === route)

  const logout = useCallback(async () => {
    await api.logout().catch(() => {})
    onLogout()
  }, [onLogout])

  return (
    <>
      <header className="topbar no-print">
        <div className="topbar-inner">
          <div className="brand">
            <span className="brand-mark">F</span>핏오더
          </div>
          <nav className="nav" aria-label="주 메뉴">
            {visible.map((r, i) => (
              <span key={r.id} style={{ display: 'contents' }}>
                {r.admin && !visible[i - 1]?.admin && <span className="sep" aria-hidden />}
                <a href={`#/${r.id}`} className={r.id === route ? 'active' : ''} aria-current={r.id === route ? 'page' : undefined}>
                  {r.label}
                </a>
              </span>
            ))}
          </nav>
          <div className="topbar-right">
            <div className="segmented" role="group" aria-label="집계 기준">
              <button
                className={basis === 'dedup' ? 'active' : ''}
                aria-pressed={basis === 'dedup'}
                onClick={() => setBasis('dedup')}
                title="완전히 같은 행은 한 번만 센다"
              >
                중복 제거
              </button>
              <button
                className={basis === 'raw' ? 'active' : ''}
                aria-pressed={basis === 'raw'}
                onClick={() => setBasis('raw')}
                title="원본 파일의 모든 행을 센다"
              >
                원자료
              </button>
            </div>
            <span className="user-chip">
              {user.username} ({user.admin ? '관리자' : '담당자'})
            </span>
            <button className="ghost" onClick={logout}>
              로그아웃
            </button>
          </div>
        </div>
      </header>
      <main>
        {current && (!current.admin || user.admin) ? (
          current.render()
        ) : current ? (
          <Notice tone="bad">이 화면은 관리자만 볼 수 있습니다.</Notice>
        ) : (
          <Notice tone="warn">없는 화면입니다. 위 메뉴에서 고르세요.</Notice>
        )}
      </main>
    </>
  )
}
