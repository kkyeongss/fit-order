import type { GroupCode, PriceSource } from './types'

export const GROUPS: { code: GroupCode; label: string }[] = [
  { code: 'SHIRT', label: '셔츠' },
  { code: 'JACKET', label: '재킷' },
  { code: 'JUMPER', label: '점퍼' },
  { code: 'PADDING_VEST', label: '패딩조끼' },
  { code: 'PANTS', label: '바지' },
  { code: 'VEST', label: '조끼' },
]

export const groupLabel = (code: string) => GROUPS.find((g) => g.code === code)?.label ?? code

// 색각 이상에서도 구분되는 범주 색
export const GROUP_COLOR: Record<string, string> = {
  셔츠: '#4c78a8',
  재킷: '#f58518',
  점퍼: '#54a24b',
  패딩조끼: '#b279a2',
  바지: '#e45756',
  조끼: '#72b7b2',
}

export const num = (v: number | null | undefined, digits = 0) =>
  v === null || v === undefined || Number.isNaN(v)
    ? '-'
    : v.toLocaleString('ko-KR', { minimumFractionDigits: digits, maximumFractionDigits: digits })

export const won = (v: number | null | undefined) => (v === null || v === undefined ? '-' : `${num(Math.round(v))}원`)

/** 0.168 → 16.8% */
export const pct = (ratio: number | null | undefined, digits = 1) =>
  ratio === null || ratio === undefined ? '-' : `${num(ratio * 100, digits)}%`

/** 서버가 이미 %로 준 값(예: 16.7) */
export const pctValue = (v: number | null | undefined, digits = 1) => (v === null || v === undefined ? '-' : `${num(v, digits)}%`)

export const carbon = (kg: number | null | undefined) =>
  kg === null || kg === undefined ? '-' : kg >= 1000 ? `${num(kg / 1000, 2)} t CO₂e` : `${num(kg, 0)} kg CO₂e`

export const dateTime = (iso: string | null | undefined) =>
  iso ? new Date(iso).toLocaleString('ko-KR', { dateStyle: 'medium', timeStyle: 'short' }) : '-'

export const PRICE_SOURCE: Record<PriceSource, { label: string; tone: 'ok' | 'info' | 'warn' }> = {
  MANUAL: { label: '입력 단가', tone: 'ok' },
  MARKET: { label: '조달청 시장가', tone: 'info' },
  ESTIMATE: { label: '추정', tone: 'warn' },
}

/**
 * '95 (근무복 주문현황)'처럼 이름 끝에 출처가 붙은 사이즈들이 모두 같은 출처면 꼬리표를 떼고 출처를 따로 알려준다.
 * 출처가 섞여 있으면(점퍼) 그대로 둔다.
 */
export function splitSizeSource(names: string[]): { label: (name: string) => string; source: string | null } {
  const re = /^(.*) \((.+)\)$/
  const sources = new Set(names.map((n) => re.exec(n)?.[2] ?? ''))
  if (sources.size === 1 && !sources.has('')) {
    return { label: (n) => re.exec(n)?.[1] ?? n, source: [...sources][0] }
  }
  return { label: (n) => n, source: null }
}

/** 단위가 달라도 같은 이름이면 같은 값으로 읽는다: metric('uniform.size-mismatch-rate', '2023-2025', '') */
export function metricValue(
  metrics: { code: string; values: { period: string; dimension: string; value: number }[] }[] | null,
  code: string,
  period: string | null,
  dimension = '',
): number | null {
  const m = metrics?.find((x) => x.code === code)
  if (!m) return null
  const found = m.values.find((v) => (period === null || v.period === period) && v.dimension === dimension)
  return found ? found.value : null
}

/** 변경 기록(audit_log)의 동작 코드를 한글로. 모르는 코드는 그대로 보여준다. */
export const AUDIT_ACTION: Record<string, string> = {
  CSV_UPLOAD: '파일 불러오기',
  API_LOAD: 'Open API 불러오기',
  PRICE_UPDATE: '단가 입력',
  PRICE_CLEAR: '입력 단가 되돌림',
  STOCK_UPDATE: '재고 입력',
  PLAN_SAVE: '발주안 저장',
  PLAN_DELETE: '발주안 삭제',
  SIZE_MAPPING: '사이즈 코드 연결',
  SOURCE_UPDATE: '데이터 출처 설정',
  ESG_ITEM_UPDATE: 'ESG 항목 설정',
  CARBON_FACTOR_UPDATE: '탄소계수 변경',
  USER_CREATE: '계정 추가',
  USER_ROLE: '계정 역할 변경',
  USER_ENABLE: '계정 사용 재개',
  USER_DISABLE: '계정 사용 중지',
  USER_PASSWORD_RESET: '비밀번호 재설정(관리자)',
  PASSWORD_CHANGE: '비밀번호 변경',
  DEMO_RESET: '시연 데이터 초기화',
  MARKET_PRICE_API: '조달청 시장가 가져오기',
  MARKET_PRICE_UPLOAD: '조달청 시장가 파일 올리기',
  GREEN_PRODUCT_API: '녹색제품 가져오기',
  GREEN_PRODUCT_UPLOAD: '녹색제품 파일 올리기',
}

export const auditLabel = (action: string) => AUDIT_ACTION[action] ?? action

export const ROLE_LABEL: Record<string, string> = { USER: '담당자', MANAGER: '부서장', ADMIN: '관리자' }

/** 클립보드에 복사한다. 실패하면 false. */
export async function copyText(text: string): Promise<boolean> {
  try {
    await navigator.clipboard.writeText(text)
    return true
  } catch {
    const area = document.createElement('textarea')
    area.value = text
    document.body.appendChild(area)
    area.select()
    const ok = document.execCommand('copy')
    area.remove()
    return ok
  }
}

/** 변화율(-0.12 → -12%)에 부호를 붙인다. */
export const signedPct = (ratio: number | null | undefined, digits = 0) =>
  ratio === null || ratio === undefined ? '-' : `${ratio > 0 ? '+' : ''}${num(ratio * 100, digits)}%`
