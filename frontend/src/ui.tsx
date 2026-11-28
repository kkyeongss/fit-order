import { useEffect, useRef, useState, type ReactNode } from 'react'
import { Chart, type ChartConfiguration } from 'chart.js/auto'
import { useBasis } from './hooks'

// 차트 공통 설정: 글꼴·색
Chart.defaults.font.family = "'Pretendard', 'Apple SD Gothic Neo', 'Malgun Gothic', system-ui, sans-serif"
Chart.defaults.font.size = 12
Chart.defaults.color = '#475569'
Chart.defaults.borderColor = '#e2e8f0'
Chart.defaults.plugins.legend.labels.usePointStyle = true
Chart.defaults.plugins.legend.labels.boxWidth = 8
Chart.defaults.maintainAspectRatio = false

/** Chart.js 차트 하나. config가 바뀌면 다시 그린다. */
export function ChartBox({ config, height = 300, label }: { config: ChartConfiguration; height?: number; label: string }) {
  const canvas = useRef<HTMLCanvasElement>(null)
  useEffect(() => {
    if (!canvas.current) return
    const chart = new Chart(canvas.current, config)
    return () => chart.destroy()
  }, [config])
  return (
    <div style={{ height }} className="chart">
      <canvas ref={canvas} role="img" aria-label={label} />
    </div>
  )
}

/** 지금 보고 있는 집계 기준(중복 제거/원자료)이 무슨 뜻인지 한 줄로 알려준다. 자세한 설명은 상단 '?' 버튼. */
export function BasisHint() {
  const { basis } = useBasis()
  return (
    <p className="basis-hint">
      {basis === 'dedup' ? (
        <>
          집계 기준 <strong>중복 제거</strong> — 원본에서 모든 칸이 같은 행은 한 번만 셉니다. 상단의 '원자료'로 바꾸면 모든 행을 셉니다.
        </>
      ) : (
        <>
          집계 기준 <strong>원자료</strong> — 원본 파일의 모든 행을 그대로 셉니다(같은 행도 모두 포함). 상단의 '중복 제거'로 바꾸면 같은
          행을 한 번만 셉니다.
        </>
      )}
    </p>
  )
}

export function Card({
  title,
  subtitle,
  actions,
  children,
  className = '',
}: {
  title?: ReactNode
  subtitle?: ReactNode
  actions?: ReactNode
  children: ReactNode
  className?: string
}) {
  return (
    <section className={`card ${className}`}>
      {(title || actions) && (
        <header className="card-head">
          <div>
            {title && <h2>{title}</h2>}
            {subtitle && <p className="muted">{subtitle}</p>}
          </div>
          {actions && <div className="card-actions">{actions}</div>}
        </header>
      )}
      {children}
    </section>
  )
}

export function Stat({
  label,
  value,
  unit,
  note,
  tone,
}: {
  label: string
  value: ReactNode
  unit?: string
  note?: ReactNode
  tone?: 'good'
}) {
  return (
    <div className={`stat ${tone ?? ''}`}>
      <div className="stat-label">{label}</div>
      <div className="stat-value">
        {value}
        {unit && <span className="stat-unit">{unit}</span>}
      </div>
      {note && <div className="stat-note">{note}</div>}
    </div>
  )
}

export function Badge({ tone = 'neutral', children }: { tone?: 'neutral' | 'ok' | 'info' | 'warn' | 'bad'; children: ReactNode }) {
  return <span className={`badge ${tone}`}>{children}</span>
}

export function Notice({ tone = 'info', children }: { tone?: 'info' | 'warn' | 'bad' | 'ok'; children: ReactNode }) {
  return (
    <div className={`notice ${tone}`} role={tone === 'bad' ? 'alert' : 'status'}>
      {children}
    </div>
  )
}

export function Loading({ what = '불러오는 중' }: { what?: string }) {
  return <div className="loading">{what}…</div>
}

/** 읽기 상태 하나를 화면에 풀어 쓴다: 로딩 → 오류 → 내용 */
export function Async<T>({
  state,
  children,
}: {
  state: { data: T | null; error: string | null; loading: boolean; reload: () => void }
  children: (data: T) => ReactNode
}) {
  if (state.data === null && state.loading) return <Loading />
  if (state.data === null)
    return (
      <Notice tone="bad">
        {state.error ?? '데이터가 없습니다.'}{' '}
        <button className="link" onClick={state.reload}>
          다시 시도
        </button>
      </Notice>
    )
  return (
    <>
      {state.error && <Notice tone="warn">갱신하지 못했습니다: {state.error}</Notice>}
      {children(state.data)}
    </>
  )
}

export function Tabs<T extends string>({
  tabs,
  value,
  onChange,
}: {
  tabs: { id: T; label: string }[]
  value: T
  onChange: (t: T) => void
}) {
  return (
    <div className="tabs" role="tablist">
      {tabs.map((t) => (
        <button
          key={t.id}
          role="tab"
          aria-selected={t.id === value}
          className={t.id === value ? 'active' : ''}
          onClick={() => onChange(t.id)}
        >
          {t.label}
        </button>
      ))}
    </div>
  )
}

/** 접었다 펼치는 영역 */
export function Collapse({ title, children, defaultOpen = false }: { title: ReactNode; children: ReactNode; defaultOpen?: boolean }) {
  const [open, setOpen] = useState(defaultOpen)
  return (
    <section className="card">
      <button className="collapse-head" onClick={() => setOpen(!open)} aria-expanded={open}>
        <span>{title}</span>
        <span aria-hidden>{open ? '▲' : '▼'}</span>
      </button>
      {open && <div className="collapse-body">{children}</div>}
    </section>
  )
}

export function Switch({
  checked,
  onChange,
  label,
  disabled,
}: {
  checked: boolean
  onChange: (v: boolean) => void
  label: string
  disabled?: boolean
}) {
  return (
    <label className="switch">
      <input type="checkbox" role="switch" checked={checked} disabled={disabled} onChange={(e) => onChange(e.target.checked)} />
      <span className="track" aria-hidden />
      <span>{label}</span>
    </label>
  )
}
