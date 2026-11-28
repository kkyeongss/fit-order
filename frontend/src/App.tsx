import { useCallback, useEffect, useRef, useState, type FormEvent, type ReactNode } from 'react'
import { api, setUnauthorizedHandler, type User } from './api'
import { ROLE_LABEL } from './format'
import { UserProvider, useAction, useBasis, useHashRoute, useUser } from './hooks'
import Admin from './pages/Admin'
import Audit from './pages/Audit'
import Dashboard from './pages/Dashboard'
import Items from './pages/Items'
import Login from './pages/Login'
import Recommend from './pages/Recommend'
import Report from './pages/Report'
import Requests from './pages/Requests'
import Sizes from './pages/Sizes'
import { Loading, Modal, Notice } from './ui'

interface Route {
  id: string
  label: string
  /** 누가 볼 수 있나: 모두 / 관리자 / 부서장·관리자 */
  who: 'all' | 'admin' | 'overseer'
  /** 메뉴에 보이지 않는 화면(주소로만 연다) */
  hidden?: boolean
  render: () => ReactNode
}

const ROUTES: Route[] = [
  { id: 'dashboard', label: '대시보드', who: 'all', render: () => <Dashboard /> },
  { id: 'sizes', label: '사이즈 분석', who: 'all', render: () => <Sizes /> },
  { id: 'recommend', label: '발주 추천', who: 'all', render: () => <Recommend /> },
  { id: 'requests', label: '데이터 개선 요청', who: 'all', render: () => <Requests /> },
  { id: 'audit', label: '변경 기록', who: 'overseer', render: () => <Audit /> },
  { id: 'admin', label: '데이터 관리', who: 'admin', render: () => <Admin /> },
  { id: 'items', label: 'ESG 항목', who: 'admin', render: () => <Items /> },
  { id: 'report', label: '결재용 보고서', who: 'all', hidden: true, render: () => <Report /> },
]

const allowed = (r: Route, user: User) =>
  r.who === 'all' || (r.who === 'admin' && user.admin) || (r.who === 'overseer' && (user.admin || user.manager))

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
  return (
    <UserProvider value={user}>
      <Shell onLogout={() => setUser(null)} />
    </UserProvider>
  )
}

function Shell({ onLogout }: { onLogout: () => void }) {
  const user = useUser()
  const { path } = useHashRoute('dashboard')
  const { basis, setBasis } = useBasis()
  const visible = ROUTES.filter((r) => !r.hidden && allowed(r, user))
  const current = ROUTES.find((r) => r.id === path)

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
                {r.who !== 'all' && visible[i - 1]?.who === 'all' && <span className="sep" aria-hidden />}
                <a href={`#/${r.id}`} className={r.id === path ? 'active' : ''} aria-current={r.id === path ? 'page' : undefined}>
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
            <AccountMenu onLogout={logout} />
          </div>
        </div>
      </header>
      {user.demo && (
        <div className="demo-banner no-print" role="note">
          <strong>공개 시연 서버</strong> — 데이터·설정·계정 변경은 막혀 있습니다. 단가·재고·발주안 입력은 해 볼 수 있고, 입력한 값은 매일
          새벽 4시에 지워집니다.
        </div>
      )}
      <main>
        {current && allowed(current, user) ? (
          current.render()
        ) : current ? (
          <Notice tone="bad">이 화면을 볼 권한이 없습니다. ({ROLE_LABEL[user.roles[0]] ?? user.roles[0]} 계정)</Notice>
        ) : (
          <Notice tone="warn">없는 화면입니다. 위 메뉴에서 고르세요.</Notice>
        )}
      </main>
    </>
  )
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
            중복은 2024년 이후에 몰려 있어(대시보드 '중복 행 비중') 기본은 중복 제거로 두고, 두 기준을 바로 비교할 수 있게 했습니다. 기관에
            확인을 요청할 내용은 '데이터 개선 요청' 화면에 있습니다.
          </p>
          <p className="muted">선택한 기준은 모든 화면(대시보드·사이즈 분석·발주 추천·엑셀)에 적용됩니다.</p>
        </div>
      )}
    </div>
  )
}

/** 계정 메뉴: 역할 표시, 비밀번호 변경, 로그아웃 */
function AccountMenu({ onLogout }: { onLogout: () => void }) {
  const user = useUser()
  const [open, setOpen] = useState(false)
  const [changing, setChanging] = useState(false)
  const box = useRef<HTMLDivElement>(null)
  useEffect(() => {
    if (!open) return
    const onDown = (e: MouseEvent) => {
      if (!box.current?.contains(e.target as Node)) setOpen(false)
    }
    document.addEventListener('mousedown', onDown)
    return () => document.removeEventListener('mousedown', onDown)
  }, [open])
  const role = user.admin ? '관리자' : user.manager ? '부서장' : '담당자'
  return (
    <div className="help account" ref={box}>
      <button className="user-chip-btn" aria-expanded={open} aria-haspopup="menu" onClick={() => setOpen(!open)}>
        {user.username} ({role}) ▾
      </button>
      {open && (
        <div className="help-pop menu" role="menu">
          <button
            role="menuitem"
            className="menu-item"
            onClick={() => {
              setOpen(false)
              setChanging(true)
            }}
          >
            비밀번호 변경
          </button>
          <button role="menuitem" className="menu-item" onClick={onLogout}>
            로그아웃
          </button>
        </div>
      )}
      {changing && <PasswordModal onClose={() => setChanging(false)} />}
    </div>
  )
}

function PasswordModal({ onClose }: { onClose: () => void }) {
  const user = useUser()
  const [current, setCurrent] = useState('')
  const [next, setNext] = useState('')
  const [again, setAgain] = useState('')
  const [done, setDone] = useState(false)
  const { busy, error, run } = useAction()
  const mismatch = again !== '' && next !== again

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    if (next !== again) return
    const ok = await run(async () => {
      await api.post('/api/auth/password', { current, next })
      return true
    })
    if (ok) setDone(true)
  }

  return (
    <Modal title="비밀번호 변경" onClose={onClose}>
      {user.demo ? (
        <Notice tone="warn">공개 시연 서버에서는 비밀번호를 바꿀 수 없습니다(공개된 시연 계정이 잠기는 것을 막기 위해서입니다).</Notice>
      ) : done ? (
        <Notice tone="ok">비밀번호를 바꿨습니다. 다음 로그인부터 새 비밀번호를 쓰세요.</Notice>
      ) : (
        <form onSubmit={submit} style={{ display: 'grid', gap: 12 }}>
          <label className="field">
            현재 비밀번호
            <input type="password" value={current} onChange={(e) => setCurrent(e.target.value)} autoComplete="current-password" required />
          </label>
          <label className="field">
            새 비밀번호 (8자 이상, 아이디와 달라야 함)
            <input
              type="password"
              value={next}
              onChange={(e) => setNext(e.target.value)}
              autoComplete="new-password"
              minLength={8}
              required
            />
          </label>
          <label className="field">
            새 비밀번호 확인
            <input type="password" value={again} onChange={(e) => setAgain(e.target.value)} autoComplete="new-password" required />
          </label>
          {mismatch && <Notice tone="warn">새 비밀번호가 서로 다릅니다.</Notice>}
          {error && <Notice tone="bad">{error}</Notice>}
          <div>
            <button className="primary" disabled={busy || mismatch}>
              {busy ? '바꾸는 중…' : '바꾸기'}
            </button>
          </div>
        </form>
      )}
    </Modal>
  )
}
