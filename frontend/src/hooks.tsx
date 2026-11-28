import { createContext, useCallback, useContext, useEffect, useRef, useState, type ReactNode } from 'react'
import { api, type User } from './api'
import type { Basis } from './types'

// 데이터 읽기 ---------------------------------------------------------------

export interface Loaded<T> {
  data: T | null
  error: string | null
  loading: boolean
  reload: () => void
}

/** GET 하나를 읽는다. path가 null이면 읽지 않는다. path가 바뀌면 다시 읽고, 늦게 온 이전 응답은 버린다. */
export function useGet<T>(path: string | null): Loaded<T> {
  const [state, setState] = useState<{ data: T | null; error: string | null; loading: boolean }>({
    data: null,
    error: null,
    loading: path !== null,
  })
  const [tick, setTick] = useState(0)
  const latest = useRef(0)

  useEffect(() => {
    if (path === null) return
    const id = ++latest.current
    setState((s) => ({ ...s, loading: true, error: null }))
    api
      .get<T>(path)
      .then((data) => id === latest.current && setState({ data, error: null, loading: false }))
      .catch((e: Error) => id === latest.current && setState((s) => ({ data: s.data, error: e.message, loading: false })))
  }, [path, tick])

  const reload = useCallback(() => setTick((t) => t + 1), [])
  return { ...state, reload }
}

/** 버튼 동작 하나(저장·업로드 등). 실행 중 표시와 오류 메시지를 함께 다룬다. */
export function useAction() {
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const run = useCallback(async <T,>(fn: () => Promise<T>): Promise<T | undefined> => {
    setBusy(true)
    setError(null)
    try {
      return await fn()
    } catch (e) {
      setError((e as Error).message)
      return undefined
    } finally {
      setBusy(false)
    }
  }, [])
  return { busy, error, run, clearError: () => setError(null) }
}

// 집계 기준(중복 제거/원자료) --------------------------------------------------------

interface BasisState {
  basis: Basis
  setBasis: (b: Basis) => void
}
const BasisContext = createContext<BasisState>({ basis: 'dedup', setBasis: () => {} })

export function BasisProvider({ children }: { children: ReactNode }) {
  const [basis, setBasisState] = useState<Basis>(() => (localStorage.getItem('basis') === 'raw' ? 'raw' : 'dedup'))
  const setBasis = (b: Basis) => {
    localStorage.setItem('basis', b)
    setBasisState(b)
  }
  return <BasisContext.Provider value={{ basis, setBasis }}>{children}</BasisContext.Provider>
}

export const useBasis = () => useContext(BasisContext)

// 로그인한 사용자 ------------------------------------------------------------------

const UserContext = createContext<User | null>(null)

export const UserProvider = UserContext.Provider

/** 로그인한 사용자(역할·시연 모드 여부). 로그인 전에는 화면이 그려지지 않으므로 항상 있다. */
export function useUser(): User {
  const user = useContext(UserContext)
  if (!user) throw new Error('로그인 정보가 없습니다')
  return user
}

// 주소(#/경로?값=...) -----------------------------------------------------------------

export interface HashRoute {
  /** 경로(예: recommend). 쿼리는 뺀다 */
  path: string
  query: URLSearchParams
}

export function useHashRoute(fallback: string): HashRoute {
  const read = () => window.location.hash.replace(/^#\/?/, '') || fallback
  const [raw, setRaw] = useState(read)
  useEffect(() => {
    const on = () => setRaw(read())
    window.addEventListener('hashchange', on)
    return () => window.removeEventListener('hashchange', on)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])
  const [path, queryString = ''] = raw.split('?')
  return { path, query: new URLSearchParams(queryString) }
}
