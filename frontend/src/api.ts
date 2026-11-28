// 서버와 통신하는 얇은 계층. 세션 쿠키 + CSRF(XSRF-TOKEN 쿠키 → X-XSRF-TOKEN 헤더).

export class ApiError extends Error {
  status: number
  constructor(status: number, message: string) {
    super(message)
    this.status = status
  }
}

type Unauthorized = () => void
let onUnauthorized: Unauthorized = () => {}

/** 세션이 끝났을 때(401) 로그인 화면으로 돌아가기 위한 훅 */
export function setUnauthorizedHandler(handler: Unauthorized) {
  onUnauthorized = handler
}

function xsrfToken(): string {
  const match = document.cookie.split('; ').find((c) => c.startsWith('XSRF-TOKEN='))
  return match ? decodeURIComponent(match.slice('XSRF-TOKEN='.length)) : ''
}

async function failure(res: Response): Promise<ApiError> {
  let message = `요청에 실패했습니다 (${res.status})`
  try {
    const body = await res.json()
    if (body && typeof body.detail === 'string' && body.detail) message = body.detail
  } catch {
    // 본문이 JSON이 아니면 기본 메시지
  }
  if (res.status === 403) message = '권한이 없거나 보안 토큰이 만료되었습니다. 새로고침 후 다시 시도하세요.'
  return new ApiError(res.status, message)
}

async function request<T>(path: string, init: RequestInit = {}, skip401 = false): Promise<T> {
  const method = (init.method ?? 'GET').toUpperCase()
  const headers = new Headers(init.headers)
  if (method !== 'GET') headers.set('X-XSRF-TOKEN', xsrfToken())
  const res = await fetch(path, { ...init, method, headers, credentials: 'same-origin' })
  if (!res.ok) {
    if (res.status === 401 && !skip401) onUnauthorized()
    throw await failure(res)
  }
  if (res.status === 204) return undefined as T
  const text = await res.text()
  return (text ? JSON.parse(text) : undefined) as T
}

function json(body: unknown): RequestInit {
  return { headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) }
}

export function query(params: Record<string, string | number | boolean | undefined | null | string[]>): string {
  const q = new URLSearchParams()
  for (const [k, v] of Object.entries(params)) {
    if (v === undefined || v === null || v === '') continue
    if (Array.isArray(v)) v.forEach((x) => q.append(k, x))
    else q.set(k, String(v))
  }
  const s = q.toString()
  return s ? `?${s}` : ''
}

export const api = {
  get: <T>(path: string) => request<T>(path),
  post: <T>(path: string, body?: unknown) => request<T>(path, { method: 'POST', ...(body === undefined ? {} : json(body)) }),
  put: <T>(path: string, body: unknown) => request<T>(path, { method: 'PUT', ...json(body) }),
  upload: <T>(path: string, form: FormData) => request<T>(path, { method: 'POST', body: form }),
  /** 로그인 시도 자체의 401은 '세션 만료'가 아니므로 로그인 화면으로 돌리지 않는다. */
  login: (username: string, password: string) =>
    request<User>('/api/auth/login', { method: 'POST', body: new URLSearchParams({ username, password }) }, true),
  me: () => request<User>('/api/auth/me', {}, true),
  logout: () => request<void>('/api/auth/logout', { method: 'POST' }, true),
}

export interface User {
  username: string
  roles: string[]
  admin: boolean
}
