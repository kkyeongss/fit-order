import { useCallback, useEffect, useRef, useState, type ReactNode } from 'react'
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

/** 집계 기준 설명(? 버튼). 바깥을 누르거나 Esc를 누르면 닫힌다. */
function BasisHelp() {
  const [open, setOpen] = useState(false)
  const box = useRef<HTMLDivElement>(null)
  useEffect(() => {
    if (!open) return
    const onDown = (e: MouseEvent) => {
      if (!box.current?.contains(e.target as Node)) setOpen(false)
    }
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setOpen(false)
    }
    document.addEventListener('mousedown', onDown)
    document.addEventListener('keydown', onKey)
    return () => {
      document.removeEventListener('mousedown', onDown)
      document.removeEventListener('keydown', onKey)
    }
  }, [open])
  return (
    <div className="help" ref={box}>
      <button
        className="help-btn"
        aria-label="집계 기준 설명"
        aria-expanded={open}
        aria-controls="basis-help"
        onClick={() => setOpen(!open)}
      >
        ?
      </button>
      {open && (
        <div id="basis-help" className="help-pop" role="dialog" aria-label="집계 기준 설명">
          <h3>중복 제거 / 원자료가 뭔가요?</h3>
          <p>
            원본 CSV에는 <strong>날짜·품목·사이즈·수량 등 모든 칸이 똑같은 행</strong>이 여러 번 들어 있습니다(완전 중복 행). 첫 번째 행은
            정상으로 보고, 같은 행이 다시 나오면 중복으로 표시합니다. 행을 지우지는 않습니다.
          </p>
          <ul>
            <li>
              <strong>중복 제거</strong>(기본): 중복으로 표시된 행을 빼고 셉니다.
            </li>
            <li>
              <strong>원자료</strong>: 원본의 모든 행을 그대로 셉니다.
            </li>
          </ul>
          <p>
            <strong>왜 둘 다 보여주나요?</strong> 같은 행이 실제로 두 번 주문한 것인지 입력이 겹친 것인지 데이터만으로는 알 수 없습니다.
            중복은 2024년 이후에 몰려 있어(대시보드 '중복 행 비중') 기본은 중복 제거로 두고, 두 기준을 바로 비교할 수 있게 했습니다.
          </p>
          <p className="muted">선택한 기준은 모든 화면(대시보드·사이즈 분석·발주 추천·엑셀)에 적용됩니다.</p>
        </div>
      )}
    </div>
  )
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
                title="모든 칸이 같은 행은 한 번만 센다"
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
            <BasisHelp />
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
