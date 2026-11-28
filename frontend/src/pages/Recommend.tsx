import { useCallback, useEffect, useMemo, useState, type FormEvent } from 'react'
import { api, query } from '../api'
import { carbon, dateTime, GROUPS, num, pct, PRICE_SOURCE, signedPct, splitSizeSource, won } from '../format'
import { useAction, useBasis, useGet, useUser } from '../hooks'
import type { Backtest, GroupCode, HeadcountView, ItemPlan, PlanCompare, PlanSummary, Recommendation, SizeLine } from '../types'
import { Async, Badge, BasisHint, Card, Collapse, Notice, Stat } from '../ui'

interface Form {
  year: string
  headcount: string
  groups: GroupCode[]
  /** 대상 직군. null이면 전체(정원 합계) */
  families: string[] | null
}

/** 총량을 직접 지정한 값(품목군 → 벌) */
type Overrides = Partial<Record<GroupCode, number>>

const stockKey = (group: string, source: string, rawSize: string | null) => `${group}|${source}|${rawSize ?? ''}`

export default function Recommend() {
  const { basis } = useBasis()
  const user = useUser()
  const headcount = useGet<HeadcountView>('/api/uniform/headcount')
  const plans = useGet<PlanSummary[]>('/api/uniform/plans')
  const [form, setForm] = useState<Form>({ year: '', headcount: '', groups: [], families: null })
  const [overrides, setOverrides] = useState<Overrides>({})
  const [result, setResult] = useState<Recommendation | null>(null)
  const { busy, error, run } = useAction()

  const request = useCallback(
    (f: Form, o: Overrides) => ({
      year: f.year ? Number(f.year) : null,
      groups: f.groups.length ? f.groups : null,
      headcount: f.headcount ? Number(f.headcount) : null,
      basis,
      totals: Object.keys(o).length ? o : null,
    }),
    [basis],
  )

  const compute = useCallback(
    (f: Form, o: Overrides) =>
      run(async () => {
        const r = await api.post<Recommendation>('/api/uniform/recommendation', request(f, o))
        setResult(r)
        // 처음에는 서버가 정한 기본값(다음 해, 최근 정원)을 입력칸에 채운다
        setForm((cur) => ({
          ...cur,
          year: cur.year || String(r.year),
          headcount: cur.headcount || (r.headcount ? String(r.headcount) : ''),
        }))
        return r
      }),
    [request, run],
  )

  // 처음 열 때와 집계 기준이 바뀔 때 현재 입력값으로 다시 계산한다
  useEffect(() => {
    compute(form, overrides)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [basis])

  const families = headcount.data?.latestByJobFamily ?? []
  const selectedFamilies = form.families ?? families.map((f) => f.jobFamily)
  const familyTotal = (names: string[]) => families.filter((f) => names.includes(f.jobFamily)).reduce((s, f) => s + f.headcount, 0)

  const toggleFamily = (name: string) => {
    const next = selectedFamilies.includes(name) ? selectedFamilies.filter((n) => n !== name) : [...selectedFamilies, name]
    setForm((f) => ({ ...f, families: next, headcount: next.length ? String(familyTotal(next)) : '' }))
  }
  const toggleGroup = (g: GroupCode) =>
    setForm((f) => ({ ...f, groups: f.groups.includes(g) ? f.groups.filter((x) => x !== g) : [...f.groups, g] }))

  const submit = (e: FormEvent) => {
    e.preventDefault()
    compute(form, overrides)
  }
  const recompute = () => compute(form, overrides)
  const setOverride = (g: GroupCode, value: number | null) => {
    const next = { ...overrides }
    if (value === null) delete next[g]
    else next[g] = value
    setOverrides(next)
    compute(form, next)
  }

  const params = result
    ? {
        year: result.year,
        groups: form.groups,
        headcount: result.headcount,
        basis,
        totals:
          Object.entries(overrides)
            .map(([g, v]) => `${g}:${v}`)
            .join(',') || undefined,
      }
    : null
  const exportUrl = params ? `/api/uniform/recommendation/export${query(params)}` : undefined
  const reportUrl = params ? `#/report${query(params)}` : undefined

  return (
    <>
      <div className="page-head">
        <div>
          <h1>발주 추천</h1>
          <p className="muted">
            과거 주문 패턴으로 다음 발주의 품목·사이즈별 수량을 계산합니다. 재고를 입력하면 발주 수량(= 필요 수량 − 재고)까지 계산합니다.
          </p>
          <BasisHint />
        </div>
        {exportUrl && (
          <div className="card-actions no-print">
            <a className="secondary-link" href={reportUrl} target="_blank" rel="noreferrer">
              결재용 보고서
            </a>
            <a className="primary" href={exportUrl} download>
              엑셀 발주서 내려받기
            </a>
          </div>
        )}
      </div>

      {!user.canEdit && <Notice tone="info">부서장 계정은 조회 전용입니다. 재고·단가·발주안 입력과 저장은 담당자 계정으로 하세요.</Notice>}

      <Card title="입력">
        <form className="form-row" onSubmit={submit}>
          <label className="field">
            발주 연도
            <input
              type="number"
              min={result?.yearRange.from ?? 2000}
              max={result?.yearRange.to ?? 2100}
              value={form.year}
              onChange={(e) => setForm({ ...form, year: e.target.value })}
              style={{ width: 110 }}
            />
            {result && (
              <span className="muted">
                {result.yearRange.from}~{result.yearRange.to}년
              </span>
            )}
          </label>
          <label className="field">
            대상 인원(명)
            <input
              type="number"
              min={1}
              value={form.headcount}
              onChange={(e) => setForm({ ...form, headcount: e.target.value })}
              style={{ width: 130 }}
              placeholder="최근 정원"
            />
          </label>
          <fieldset style={{ border: 0, padding: 0, margin: 0 }}>
            <legend className="muted" style={{ padding: 0, marginBottom: 4 }}>
              근무복 지급 대상 직군 (고르면 인원이 자동으로 들어갑니다)
            </legend>
            <div className="checks">
              {families.map((f) => (
                <label key={f.jobFamily}>
                  <input type="checkbox" checked={selectedFamilies.includes(f.jobFamily)} onChange={() => toggleFamily(f.jobFamily)} />
                  {f.jobFamily} {num(f.headcount)}명
                </label>
              ))}
              {families.length === 0 && <span className="muted">정원 데이터가 없습니다</span>}
            </div>
          </fieldset>
          <fieldset style={{ border: 0, padding: 0, margin: 0 }}>
            <legend className="muted" style={{ padding: 0, marginBottom: 4 }}>
              품목 (선택 안 하면 전체)
            </legend>
            <div className="checks">
              {GROUPS.map((g) => (
                <label key={g.code}>
                  <input type="checkbox" checked={form.groups.includes(g.code)} onChange={() => toggleGroup(g.code)} />
                  {g.label}
                </label>
              ))}
            </div>
          </fieldset>
          <button className="primary" disabled={busy}>
            {busy ? '계산하는 중…' : '추천 계산'}
          </button>
        </form>
        <p className="muted" style={{ marginTop: 8 }}>
          정원은 지원직·계약직을 포함한 전체 기준이라, 근무복을 주지 않는 직군이 있으면 빼고 계산하세요. 인원이 바뀌면 과거 주문량을 인원
          비율로 보정합니다.
        </p>
        {error && (
          <div style={{ marginTop: 12 }}>
            <Notice tone="bad">{error}</Notice>
          </div>
        )}
      </Card>

      {result && <Result rec={result} onChanged={recompute} overrides={overrides} setOverride={setOverride} />}
      {!result && !error && <Notice tone="info">계산하는 중입니다…</Notice>}

      {result && (
        <PlansCard
          plans={plans}
          onChanged={plans.reload}
          canEdit={user.canEdit}
          save={{ ...request(form, overrides), year: result.year }}
        />
      )}

      <Collapse title="추천 로직 검증: 백테스트 (과거 해를 모르는 해로 두고 예측해 실제와 비교)">
        <BacktestTable />
      </Collapse>
    </>
  )
}

function Result({
  rec,
  onChanged,
  overrides,
  setOverride,
}: {
  rec: Recommendation
  onChanged: () => void
  overrides: Overrides
  setOverride: (g: GroupCode, v: number | null) => void
}) {
  const estimated = rec.items.filter((i) => i.priceSource === 'ESTIMATE')
  // 품목 상세는 처음에 첫 품목만 펼친다. 요약 표에서 '상세'를 누르면 그 품목을 펼치고 이동한다.
  const [open, setOpen] = useState<Set<string>>(() => new Set(rec.items.slice(0, 1).map((i) => i.group)))
  useEffect(() => setOpen(new Set(rec.items.slice(0, 1).map((i) => i.group))), [rec.year, rec.items.length])
  const toggle = (g: string) =>
    setOpen((cur) => {
      const next = new Set(cur)
      if (!next.delete(g)) next.add(g)
      return next
    })
  const goto = (g: string) => {
    setOpen((cur) => new Set(cur).add(g))
    setTimeout(() => document.getElementById(`plan-${g}`)?.scrollIntoView({ behavior: 'smooth', block: 'start' }), 50)
  }
  const t = rec.totals
  const hasStock = t.stockTotal > 0

  return (
    <>
      {rec.warnings.map((w) => (
        <Notice key={w} tone="warn">
          {w}
        </Notice>
      ))}

      <div className="grid cols-4">
        <Stat
          label={`${rec.year}년 필요 수량`}
          value={num(t.quantity)}
          unit="벌"
          note={`안전재고 ${num(t.safetyQuantity)}벌 포함 · 참고 범위 ${num(t.range.low)}~${num(t.range.high)}벌`}
        />
        <Stat
          label={hasStock ? '발주 수량 (재고 반영)' : '발주 수량'}
          value={num(t.orderQuantity)}
          unit="벌"
          note={hasStock ? `현재 재고 ${num(t.stockTotal)}벌을 뺌` : '재고를 입력하면 그만큼 줄어듭니다'}
        />
        <Stat label="예상 발주 금액" value={won(t.orderAmount)} note="품목별 단가 × 발주 수량" />
        <Stat
          tone="good"
          label="예상 절감 (연)"
          value={won(t.savedAmount)}
          note={`${num(t.savedQuantity)}벌 · 탄소 ${carbon(t.carbonKg)} · 추정치, 개선률 ${pct(rec.improvementRate, 0)} 가정`}
        />
      </div>

      {rec.otherBasis && (
        <Notice tone="info">
          <strong>집계 기준에 따른 차이:</strong> {rec.basis === 'DEDUP' ? '중복 제거' : '원자료'} 기준 필요 수량 {num(t.quantity)}벌, 반대
          기준(
          {rec.otherBasis.basis === 'RAW' ? '원자료' : '중복 제거'})으로는 {num(rec.otherBasis.quantity)}벌(
          {signedPct(rec.otherBasis.quantity / Math.max(1, t.quantity) - 1)}). 같은 행이 실제 반복 주문인지 확인되지 않아 두 기준을 함께
          보여줍니다. <a href="#/requests">기관 확인 요청 보기</a>
        </Notice>
      )}

      {estimated.length > 0 && (
        <Notice tone="warn">
          <strong>추정 단가:</strong> {estimated.map((i) => i.label).join('·')}의 단가는 조달청 시장가 표본이 부족해 추정 단가(
          {won(estimated[0].unitPrice)})로 계산했습니다. 실제 단가를 알면 품목 상세에서 '단가 수정'으로 입력하세요(바로 반영됩니다).
        </Notice>
      )}

      <Card
        title="품목별 요약"
        subtitle="수량은 안전재고 포함. '상세'를 누르면 사이즈별 수량·재고 입력·산정 근거로 이동합니다."
        actions={
          <>
            <button className="secondary" onClick={() => setOpen(new Set(rec.items.map((i) => i.group)))}>
              상세 모두 펼치기
            </button>
            <button className="secondary" onClick={() => setOpen(new Set())}>
              모두 접기
            </button>
          </>
        }
      >
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>품목</th>
                <th className="num">필요 수량</th>
                <th className="num">재고</th>
                <th className="num">발주 수량</th>
                <th className="num">단가</th>
                <th className="num">발주 금액</th>
                <th className="num">예상 절감(연)</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {rec.items.map((i) => (
                <tr key={i.group}>
                  <td>
                    <strong>{i.label}</strong>
                    {i.warnings.length > 0 && (
                      <>
                        {' '}
                        <Badge tone="warn">확인 {i.warnings.length}</Badge>
                      </>
                    )}
                  </td>
                  <td className="num">
                    {num(i.quantity)} <span className="muted">(안전재고 {pct(i.safetyRate, 0)})</span>
                  </td>
                  <td className="num">{num(i.stockTotal)}</td>
                  <td className="num">
                    <strong>{num(i.orderQuantity)}</strong>
                  </td>
                  <td className="num">
                    {won(i.unitPrice)} <Badge tone={PRICE_SOURCE[i.priceSource].tone}>{PRICE_SOURCE[i.priceSource].label}</Badge>
                  </td>
                  <td className="num">{won(i.orderAmount)}</td>
                  <td className="num">{i.savings ? `${num(i.savings.savedQuantity, 1)}벌 · ${won(i.savings.savedAmount)}` : '-'}</td>
                  <td>
                    <button className="link" onClick={() => goto(i.group)}>
                      상세
                    </button>
                  </td>
                </tr>
              ))}
              <tr className="total">
                <td>합계</td>
                <td className="num">{num(t.quantity)}</td>
                <td className="num">{num(t.stockTotal)}</td>
                <td className="num">{num(t.orderQuantity)}</td>
                <td />
                <td className="num">{won(t.orderAmount)}</td>
                <td className="num">
                  {num(t.savedQuantity, 1)}벌 · {won(t.savedAmount)}
                </td>
                <td />
              </tr>
            </tbody>
          </table>
        </div>
      </Card>

      <RoundsCard rec={rec} />

      {rec.items.map((item) => (
        <ItemCard
          key={item.group}
          item={item}
          rec={rec}
          open={open.has(item.group)}
          onToggle={() => toggle(item.group)}
          onChanged={onChanged}
          override={overrides[item.group]}
          setOverride={(v) => setOverride(item.group, v)}
        />
      ))}

      <div className="grid cols-2">
        <Card title="정기 발주 시기 제안" subtitle="과거 주문 수량이 몰린 달 (안정 연도 합계 중 비중)">
          {rec.orderMonths.length === 0 ? (
            <p className="muted">제안할 달이 설정되어 있지 않습니다.</p>
          ) : (
            <div style={{ display: 'grid', gap: 10 }}>
              {rec.orderMonths.map((m) => (
                <div key={m.month}>
                  <div className="kv">
                    <strong>{m.month}월</strong>
                    <span>{pct(m.share, 0)}</span>
                    <span className="muted">{num(m.quantity)}벌</span>
                  </div>
                  <div className="bar" aria-hidden>
                    <span style={{ width: `${Math.min(100, m.share * 100)}%` }} />
                  </div>
                </div>
              ))}
              <p className="muted">두 번에 나눠 발주하면 소량 발주를 줄이고 사이즈 변화를 반기마다 반영할 수 있습니다.</p>
            </div>
          )}
        </Card>
        <GreenCard rec={rec} />
      </div>
    </>
  )
}

/** 정기 발주 회차(4월·8월)별 수량·금액: 품목 × 회차 */
function RoundsCard({ rec }: { rec: Recommendation }) {
  const rounds = rec.totals.rounds
  if (rounds.length === 0) return null
  return (
    <Card
      title="정기 발주 회차별 수량"
      subtitle={`발주 수량을 과거 수량 비중(${rounds.map((r) => `${r.month}월 ${pct(r.share, 0)}`).join(', ')})으로 나눈 값입니다. 엑셀에서도 같은 비율로 계산됩니다.`}
    >
      <div className="table-wrap">
        <table>
          <thead>
            <tr>
              <th>품목</th>
              {rounds.map((r) => (
                <th className="num" key={r.month}>
                  {r.month}월 발주
                </th>
              ))}
              <th className="num">합계</th>
            </tr>
          </thead>
          <tbody>
            {rec.items.map((i) => (
              <tr key={i.group}>
                <td>{i.label}</td>
                {i.rounds.map((r) => (
                  <td className="num" key={r.index}>
                    {num(r.quantity)}벌 <span className="muted">({won(r.amount)})</span>
                  </td>
                ))}
                <td className="num">{num(i.orderQuantity)}벌</td>
              </tr>
            ))}
            <tr className="total">
              <td>합계</td>
              {rounds.map((r) => (
                <td className="num" key={r.month}>
                  {num(r.quantity)}벌 <span className="muted">({won(r.amount)})</span>
                </td>
              ))}
              <td className="num">{num(rec.totals.orderQuantity)}벌</td>
            </tr>
          </tbody>
        </table>
      </div>
    </Card>
  )
}

function ItemCard({
  item,
  rec,
  open,
  onToggle,
  onChanged,
  override,
  setOverride,
}: {
  item: ItemPlan
  rec: Recommendation
  open: boolean
  onToggle: () => void
  onChanged: () => void
  override: number | undefined
  setOverride: (v: number | null) => void
}) {
  const user = useUser()
  const src = PRICE_SOURCE[item.priceSource]
  const s = item.savings
  const sp = useMemo(() => splitSizeSource(item.sizes.map((z) => z.size)), [item.sizes])
  const [edits, setEdits] = useState<Record<string, string>>({})
  const [showZero, setShowZero] = useState(false)
  const [priceEditing, setPriceEditing] = useState(false)
  const [priceText, setPriceText] = useState('')
  const [overrideText, setOverrideText] = useState(override === undefined ? '' : String(override))
  const { busy, error, run } = useAction()

  const isZero = (z: SizeLine) => z.quantity === 0 && z.stock === 0 && (edits[stockKey(item.group, z.source, z.rawSize)] ?? '') === ''
  const zeroCount = item.sizes.filter(isZero).length
  const lines = showZero ? item.sizes : item.sizes.filter((z) => !isZero(z))
  const multiSource = !sp.source && new Set(lines.map((z) => z.source)).size > 1
  const months = rec.totals.rounds
  const colSpan = 6 + months.length

  const dirty = Object.keys(edits).length > 0
  const saveStock = () =>
    run(async () => {
      const changes = item.sizes
        .filter((z) => stockKey(item.group, z.source, z.rawSize) in edits)
        .map((z) => ({
          group: item.group,
          source: z.source,
          rawSize: z.rawSize ?? '',
          quantity: Number(edits[stockKey(item.group, z.source, z.rawSize)] || 0),
        }))
      if (changes.length === 0) return
      await api.put('/api/uniform/stock', changes)
      setEdits({})
      onChanged()
    })

  const savePrice = () =>
    run(async () => {
      await api.put('/api/uniform/prices', [{ group: item.group, price: Number(priceText) }])
      setPriceEditing(false)
      onChanged()
    })
  const clearPrice = () =>
    run(async () => {
      await api.delete(`/api/uniform/prices/${item.group}`)
      onChanged()
    })

  // 출처별 소계(출처가 둘 이상일 때)
  const sourceName = (z: SizeLine) => /\((.+)\)$/.exec(z.size)?.[1] ?? z.source
  const rows: ({ kind: 'size'; z: SizeLine } | { kind: 'subtotal'; source: string; sizes: SizeLine[] })[] = []
  lines.forEach((z, i) => {
    rows.push({ kind: 'size', z })
    if (multiSource && (i === lines.length - 1 || lines[i + 1].source !== z.source)) {
      rows.push({ kind: 'subtotal', source: sourceName(z), sizes: lines.filter((x) => x.source === z.source) })
    }
  })

  return (
    <div id={`plan-${item.group}`} style={{ scrollMarginTop: 72 }}>
      <Card
        title={
          <span style={{ display: 'inline-flex', gap: 10, alignItems: 'center', flexWrap: 'wrap' }}>
            {item.label}{' '}
            <Badge tone={src.tone}>
              단가 {won(item.unitPrice)} · {src.label}
            </Badge>
            {item.warnings.length > 0 && <Badge tone="warn">확인할 점 {item.warnings.length}</Badge>}
          </span>
        }
        actions={
          <>
            <span>
              <strong style={{ fontSize: 18 }}>{num(item.orderQuantity)}벌</strong>{' '}
              <span className="muted">
                발주 · {won(item.orderAmount)}
                {item.stockTotal > 0 ? ` (필요 ${num(item.quantity)}, 재고 ${num(item.stockTotal)})` : ''}
              </span>
            </span>
            <button className="secondary" onClick={onToggle} aria-expanded={open}>
              {open ? '접기' : '사이즈별 보기'}
            </button>
          </>
        }
      >
        {open && (
          <>
            {item.warnings.map((w) => (
              <div key={w} style={{ marginBottom: 8 }}>
                <Notice tone="warn">{w}</Notice>
              </div>
            ))}
            <ul className="basis-lines" style={{ margin: '0 0 12px', padding: 0 }}>
              <li>총량: {item.total.rule}</li>
              <li>
                {item.headcountFactor !== 1 && rec.headcount !== null && item.baseHeadcount !== null
                  ? `인원 보정: ×${num(item.headcountFactor, 3)} (대상 ${num(rec.headcount)}명 ÷ ${item.headcountYear}년 정원 ${num(item.baseHeadcount)}명) → ${num(item.baseQuantity)}벌`
                  : `인원 보정 없음 → ${num(item.baseQuantity)}벌`}
              </li>
              <li>
                사이즈 배분: {item.sizeRule}
                {sp.source && <span className="muted"> · 사이즈 코드는 '{sp.source}' 기준</span>}
              </li>
              <li>
                안전재고: {pct(item.safetyRate, 0)} → +{num(item.safetyQuantity)}벌
              </li>
              <li className="muted">최근 연도별 주문량: {item.history.map((h) => `${h.year}년 ${num(h.quantity)}벌`).join(' · ')}</li>
            </ul>

            {user.canEdit && (
              <div className="form-row item-tools">
                <label className="field">
                  총량 직접 지정(벌)
                  <span style={{ display: 'flex', gap: 6 }}>
                    <input
                      type="number"
                      min={0}
                      value={overrideText}
                      onChange={(e) => setOverrideText(e.target.value)}
                      style={{ width: 110 }}
                      placeholder="자동"
                    />
                    <button className="secondary" disabled={overrideText === ''} onClick={() => setOverride(Number(overrideText))}>
                      적용
                    </button>
                    {override !== undefined && (
                      <button
                        className="link"
                        onClick={() => {
                          setOverrideText('')
                          setOverride(null)
                        }}
                      >
                        해제
                      </button>
                    )}
                  </span>
                </label>
                <div className="field">
                  단가
                  {priceEditing ? (
                    <span style={{ display: 'flex', gap: 6 }}>
                      <input
                        type="number"
                        min={1}
                        step={100}
                        value={priceText}
                        onChange={(e) => setPriceText(e.target.value)}
                        style={{ width: 110 }}
                        autoFocus
                      />
                      <button className="primary" disabled={busy || !priceText} onClick={savePrice}>
                        저장
                      </button>
                      <button className="link" onClick={() => setPriceEditing(false)}>
                        취소
                      </button>
                    </span>
                  ) : (
                    <span style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
                      {won(item.unitPrice)}
                      <button
                        className="link"
                        onClick={() => {
                          setPriceText(String(Math.round(item.unitPrice)))
                          setPriceEditing(true)
                        }}
                      >
                        단가 수정
                      </button>
                      {item.priceSource === 'MANUAL' && (
                        <button className="link" disabled={busy} onClick={clearPrice}>
                          입력 단가 되돌리기
                        </button>
                      )}
                    </span>
                  )}
                </div>
              </div>
            )}
            {error && <Notice tone="bad">{error}</Notice>}

            <div className="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>사이즈</th>
                    <th className="num">비율</th>
                    <th className="num">필요 수량</th>
                    <th className="num">현재 재고</th>
                    <th className="num">발주 수량</th>
                    {months.map((m) => (
                      <th className="num" key={m.month}>
                        {m.month}월 발주
                      </th>
                    ))}
                    <th className="num">발주 금액</th>
                  </tr>
                </thead>
                <tbody>
                  {rows.map((row) => {
                    if (row.kind === 'subtotal') {
                      const sum = (f: (z: SizeLine) => number) => row.sizes.reduce((a, z) => a + f(z), 0)
                      return (
                        <tr className="subtotal" key={`sub-${row.source}`}>
                          <td>{row.source} 소계</td>
                          <td />
                          <td className="num">{num(sum((z) => z.quantity))}</td>
                          <td className="num">{num(sum((z) => z.stock))}</td>
                          <td className="num">{num(sum((z) => z.orderQuantity))}</td>
                          {months.map((m, i) => (
                            <td className="num" key={m.month}>
                              {num(sum((z) => z.rounds[i] ?? 0))}
                            </td>
                          ))}
                          <td className="num">{won(item.unitPrice * sum((z) => z.orderQuantity))}</td>
                        </tr>
                      )
                    }
                    const z = row.z
                    const key = stockKey(item.group, z.source, z.rawSize)
                    const typed = edits[key]
                    return (
                      <tr key={key} style={z.quantity === 0 && z.stock === 0 ? { color: '#94a3b8' } : undefined}>
                        <td>
                          {sp.label(z.size)} {z.unknownCode && <Badge tone="warn">뜻 미확인</Badge>}
                        </td>
                        <td className="num">{pct(z.ratio)}</td>
                        <td className="num">{num(z.quantity)}</td>
                        <td className="num">
                          {user.canEdit ? (
                            <input
                              type="number"
                              min={0}
                              className="stock-input"
                              aria-label={`${item.label} ${sp.label(z.size)} 현재 재고`}
                              value={typed ?? (z.stock || '')}
                              placeholder="0"
                              onChange={(e) => setEdits({ ...edits, [key]: e.target.value })}
                            />
                          ) : (
                            num(z.stock)
                          )}
                        </td>
                        <td className="num">
                          <strong>
                            {num(
                              typed !== undefined && typed !== ''
                                ? Math.max(0, z.quantity - Number(typed))
                                : typed === ''
                                  ? z.quantity
                                  : z.orderQuantity,
                            )}
                          </strong>
                        </td>
                        {months.map((m, i) => (
                          <td className="num" key={m.month}>
                            {num(z.rounds[i] ?? 0)}
                          </td>
                        ))}
                        <td className="num">{won(item.unitPrice * z.orderQuantity)}</td>
                      </tr>
                    )
                  })}
                  <tr className="subtotal">
                    <td>{item.label} 합계</td>
                    <td />
                    <td className="num">{num(item.quantity)}</td>
                    <td className="num">{num(item.stockTotal)}</td>
                    <td className="num">{num(item.orderQuantity)}</td>
                    {item.rounds.map((r) => (
                      <td className="num" key={r.index}>
                        {num(r.quantity)}
                      </td>
                    ))}
                    <td className="num">{won(item.orderAmount)}</td>
                  </tr>
                  {zeroCount > 0 && (
                    <tr>
                      <td colSpan={colSpan} className="muted">
                        수량이 0인 사이즈 {zeroCount}줄을 {showZero ? '함께 보고 있습니다' : '숨겼습니다'}.{' '}
                        <button className="link" onClick={() => setShowZero(!showZero)}>
                          {showZero ? '숨기기' : '보기'}
                        </button>
                      </td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>
            {user.canEdit && (
              <div className="form-row" style={{ marginTop: 10 }}>
                <button className="primary" disabled={busy || !dirty} onClick={saveStock}>
                  {busy ? '저장하는 중…' : '재고 저장'}
                </button>
                <span className="muted">
                  {dirty
                    ? '입력한 재고를 저장하면 발주 수량과 엑셀에 반영됩니다.'
                    : '재고 칸에 지금 남은 수량을 입력하세요. 저장한 값은 모든 담당자가 함께 봅니다.'}
                </span>
              </div>
            )}
            {s && (
              <p className="muted" style={{ marginTop: 10 }}>
                예상 절감(연): 사이즈 불일치율 {pct(s.mismatchRate)} × {num(s.annualQuantity)}벌 × 개선률 →{' '}
                <strong>
                  {num(s.savedQuantity, 1)}벌 · {won(s.savedAmount)} · {carbon(s.carbonKg)}
                </strong>
              </p>
            )}
          </>
        )}
      </Card>
    </div>
  )
}

function GreenCard({ rec }: { rec: Recommendation }) {
  const rows = rec.items.map((i) => ({
    label: i.label,
    market: rec.marketPrices.find((m) => m.group === i.group),
    green: rec.greenProducts.find((g) => g.group === i.group),
  }))
  const anyGreen = rec.greenProducts.some((g) => g.validCount > 0)
  const anyMarket = rows.some((r) => r.market && r.market.ecoCount > 0)
  return (
    <Card title="녹색구매 비교" subtitle="환경표지 인증 제품이 있는지, 값은 얼마나 차이 나는지">
      {!anyGreen && !anyMarket ? (
        <p className="muted">비교할 환경표지 제품 정보가 없습니다. 데이터 관리에서 조달청 시장가·녹색제품을 가져오세요.</p>
      ) : (
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>품목</th>
                <th className="num">쇼핑몰 환경표지</th>
                <th className="num">환경표지 가격</th>
                <th className="num">전체 가격</th>
                <th className="num">인증 유효 제품</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((r) => {
                const m = r.market
                const diff = m && m.ecoMedianPrice ? m.ecoMedianPrice / m.medianPrice - 1 : null
                return (
                  <tr key={r.label}>
                    <td>{r.label}</td>
                    <td className="num">{m ? `${num(m.ecoCount)}건` : '-'}</td>
                    <td className="num">
                      {m?.ecoMedianPrice ? (
                        <>
                          {won(m.ecoMedianPrice)} {diff !== null && <span className="muted">({signedPct(diff)})</span>}
                        </>
                      ) : (
                        '-'
                      )}
                    </td>
                    <td className="num">{m ? won(m.medianPrice) : '-'}</td>
                    <td className="num">
                      {r.green && r.green.validCount > 0 ? `${num(r.green.validCount)}개 · ${num(r.green.companyCount)}개 기업` : '-'}
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      )}
      {!anyGreen && (
        <p className="muted" style={{ marginTop: 8 }}>
          녹색제품 정보조회(환경표지 인증기간·기업)는 아직 가져오지 않았습니다(기관 API 오류, <a href="#/requests">개선 요청 C1</a>). 가격은
          조달청 쇼핑몰 값(중앙값)입니다.
        </p>
      )}
    </Card>
  )
}

/** 저장한 발주안: 지금 계산을 발주안으로 저장하고, 그해 실제 주문이 들어오면 계획과 비교한다. */
function PlansCard({
  plans,
  onChanged,
  canEdit,
  save,
}: {
  plans: { data: PlanSummary[] | null; error: string | null; loading: boolean; reload: () => void }
  onChanged: () => void
  canEdit: boolean
  save: object
}) {
  const [note, setNote] = useState('')
  const [compare, setCompare] = useState<PlanCompare | null>(null)
  const [saved, setSaved] = useState(false)
  const { busy, error, run } = useAction()

  const savePlan = () =>
    run(async () => {
      await api.post('/api/uniform/plans', { ...save, note })
      setNote('')
      setSaved(true)
      onChanged()
    })
  const open = (id: number) => run(async () => setCompare(await api.get<PlanCompare>(`/api/uniform/plans/${id}/compare`)))
  const remove = (id: number) => {
    if (!window.confirm('이 발주안을 지웁니다. 계속할까요?')) return
    run(async () => {
      await api.delete(`/api/uniform/plans/${id}`)
      if (compare?.plan.id === id) setCompare(null)
      onChanged()
    })
  }

  return (
    <Card
      title="저장한 발주안과 실제 비교"
      subtitle="발주를 확정했으면 지금 계산을 저장해 두세요. 그해 실제 주문 데이터가 들어오면 계획과 실제를 비교해, 과잉·부족이 실제로 줄었는지 확인합니다."
    >
      {canEdit && (
        <div className="form-row" style={{ marginBottom: 12 }}>
          <label className="field" style={{ flex: 1, minWidth: 220 }}>
            메모 (예: 4월 정기 발주 확정)
            <input
              type="text"
              value={note}
              onChange={(e) => {
                setSaved(false)
                setNote(e.target.value)
              }}
              maxLength={300}
            />
          </label>
          <button className="primary" disabled={busy} onClick={savePlan}>
            지금 계산을 발주안으로 저장
          </button>
          {saved && <Badge tone="ok">저장했습니다</Badge>}
        </div>
      )}
      {error && <Notice tone="bad">{error}</Notice>}
      <Async state={plans}>
        {(list) =>
          list.length === 0 ? (
            <p className="muted">저장한 발주안이 없습니다.</p>
          ) : (
            <div className="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>발주 연도</th>
                    <th>저장</th>
                    <th>기준</th>
                    <th className="num">필요 수량</th>
                    <th className="num">발주 수량</th>
                    <th className="num">발주 금액</th>
                    <th>메모</th>
                    <th />
                  </tr>
                </thead>
                <tbody>
                  {list.map((p) => (
                    <tr key={p.id}>
                      <td>{p.planYear}년</td>
                      <td className="nowrap">
                        {dateTime(p.savedAt)} · {p.savedBy}
                      </td>
                      <td>{p.basis === 'DEDUP' ? '중복 제거' : '원자료'}</td>
                      <td className="num">{num(p.quantity)}</td>
                      <td className="num">{num(p.orderQuantity)}</td>
                      <td className="num">{won(p.orderAmount)}</td>
                      <td>{p.note}</td>
                      <td className="nowrap">
                        <button className="link" onClick={() => open(p.id)}>
                          실제와 비교
                        </button>{' '}
                        {canEdit && (
                          <button className="link" onClick={() => remove(p.id)}>
                            삭제
                          </button>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )
        }
      </Async>
      {compare && <CompareView compare={compare} />}
    </Card>
  )
}

function CompareView({ compare }: { compare: PlanCompare }) {
  if (!compare.hasActual) {
    return (
      <div style={{ marginTop: 12 }}>
        <Notice tone="info">{compare.message}</Notice>
      </div>
    )
  }
  return (
    <div style={{ marginTop: 14, display: 'grid', gap: 10 }}>
      <h3>
        {compare.plan.planYear}년 계획 vs 실제 ({compare.plan.basis === 'DEDUP' ? '중복 제거' : '원자료'} 기준)
      </h3>
      {compare.message && <Notice tone="warn">{compare.message}</Notice>}
      <div className="grid cols-3">
        <Stat label="계획(필요 수량)" value={num(compare.plannedQuantity)} unit="벌" />
        <Stat
          label="실제 주문"
          value={num(compare.actualQuantity)}
          unit="벌"
          note={compare.lastOrderDate ? `${compare.lastOrderDate}까지` : undefined}
        />
        <Stat
          label="차이(계획 − 실제)"
          value={`${compare.difference > 0 ? '+' : ''}${num(compare.difference)}`}
          unit="벌"
          note={`${signedPct(compare.differencePercent)} · 사이즈 불일치율 ${pct(compare.sizeMismatchRate, 0)}`}
        />
      </div>
      <p className="muted">
        양수는 계획이 실제보다 많았다(남을 뻔), 음수는 모자랐다는 뜻입니다. 사이즈 불일치율은 사이즈별로 남거나 모자란 벌 수의 합 ÷ 실제
        주문량이고 작을수록 계획이 실제와 가깝습니다.
      </p>
      <div className="table-wrap">
        <table>
          <thead>
            <tr>
              <th>품목</th>
              <th className="num">계획</th>
              <th className="num">실제</th>
              <th className="num">차이</th>
              <th className="num">차이 %</th>
              <th className="num">사이즈 불일치율</th>
            </tr>
          </thead>
          <tbody>
            {compare.items.map((i) => (
              <tr key={i.group}>
                <td>
                  <details>
                    <summary>{i.label}</summary>
                    <table style={{ marginTop: 6 }}>
                      <thead>
                        <tr>
                          <th>사이즈</th>
                          <th className="num">계획</th>
                          <th className="num">실제</th>
                          <th className="num">차이</th>
                        </tr>
                      </thead>
                      <tbody>
                        {i.sizes.map((z) => (
                          <tr key={z.size}>
                            <td>{z.size}</td>
                            <td className="num">{num(z.planned)}</td>
                            <td className="num">{num(z.actual)}</td>
                            <td className="num">
                              {z.difference > 0 ? '+' : ''}
                              {num(z.difference)}
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </details>
                </td>
                <td className="num">{num(i.planned)}</td>
                <td className="num">{num(i.actual)}</td>
                <td className="num">
                  {i.difference > 0 ? '+' : ''}
                  {num(i.difference)}
                </td>
                <td className="num">{signedPct(i.differencePercent)}</td>
                <td className="num">{pct(i.sizeMismatchRate, 0)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  )
}

function BacktestTable() {
  const { basis } = useBasis()
  const state = useGet<Backtest>(`/api/uniform/backtest?basis=${basis}`)
  return (
    <Async state={state}>
      {(b) => {
        const rows = [...b.groups, b.total]
        const methods = b.total.averages
        const best = (g: (typeof rows)[number], pick: 'totalError' | 'sizeError') => Math.min(...g.averages.map((a) => a[pick]))
        const rec = b.total.averages.find((a) => a.method === 'RECOMMENDATION')
        const baselines = b.total.averages.filter((a) => a.method !== 'RECOMMENDATION')
        return (
          <>
            <p className="muted">
              {b.fromYear}~{b.toYear}년을 한 해씩 모르는 해로 두고 그 전 데이터만으로 예측해 실제와 비교한 연평균 오차입니다. 총량 오차 =
              |예측 − 실제| ÷ 실제, 사이즈 오차 = 사이즈별로 남거나 모자란 벌 수의 합 ÷ 실제 총량. 값이 작을수록 좋습니다.
            </p>
            {rec && baselines.every((a) => rec.sizeError < a.sizeError) && (
              <Notice tone="ok">
                전체 사이즈 오차는 추천 로직 {pct(rec.sizeError)}로 {baselines.map((a) => `${a.label} ${pct(a.sizeError)}`).join(', ')}보다
                작습니다.
              </Notice>
            )}
            <div className="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th rowSpan={2}>품목</th>
                    {methods.map((m) => (
                      <th key={m.method} colSpan={2} style={{ textAlign: 'center' }}>
                        {m.label}
                      </th>
                    ))}
                  </tr>
                  <tr>
                    {methods.flatMap((m) => [
                      <th className="num" key={`${m.method}-t`}>
                        총량
                      </th>,
                      <th className="num" key={`${m.method}-s`}>
                        사이즈
                      </th>,
                    ])}
                  </tr>
                </thead>
                <tbody>
                  {rows.map((g) => (
                    <tr key={g.label} className={g === b.total ? 'total' : ''}>
                      <td>{g.label}</td>
                      {g.averages.flatMap((a) => [
                        <td className={`num ${a.totalError === best(g, 'totalError') ? 'best' : ''}`} key={`${a.method}-t`}>
                          {pct(a.totalError, 0)}
                        </td>,
                        <td className={`num ${a.sizeError === best(g, 'sizeError') ? 'best' : ''}`} key={`${a.method}-s`}>
                          {pct(a.sizeError, 0)}
                        </td>,
                      ])}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <p className="muted">
              재킷·패딩조끼는 격년 주문이라 직전 해 값으로는 크게 틀립니다. 추천은 같은 홀짝 해의 값을 써서 오차를 줄입니다.
            </p>
          </>
        )
      }}
    </Async>
  )
}
