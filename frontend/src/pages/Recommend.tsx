import { useCallback, useEffect, useMemo, useState, type FormEvent } from 'react'
import { api, query } from '../api'
import { carbon, GROUPS, num, pct, PRICE_SOURCE, splitSizeSource, won } from '../format'
import { useAction, useBasis, useGet } from '../hooks'
import type { Backtest, GroupCode, ItemPlan, Recommendation } from '../types'
import { Async, Badge, BasisHint, Card, Collapse, Notice, Stat } from '../ui'

interface Form {
  year: string
  headcount: string
  groups: GroupCode[]
}

export default function Recommend() {
  const { basis } = useBasis()
  const [form, setForm] = useState<Form>({ year: '', headcount: '', groups: [] })
  const [result, setResult] = useState<Recommendation | null>(null)
  const { busy, error, run } = useAction()

  const compute = useCallback(
    (f: Form) =>
      run(async () => {
        const r = await api.post<Recommendation>('/api/uniform/recommendation', {
          year: f.year ? Number(f.year) : null,
          groups: f.groups.length ? f.groups : null,
          headcount: f.headcount ? Number(f.headcount) : null,
          basis,
        })
        setResult(r)
        // 처음에는 서버가 정한 기본값(다음 해, 최근 정원)을 입력칸에 채운다
        setForm((cur) => ({
          ...cur,
          year: cur.year || String(r.year),
          headcount: cur.headcount || (r.headcount ? String(r.headcount) : ''),
        }))
        return r
      }),
    [basis, run],
  )

  // 처음 열 때와 집계 기준이 바뀔 때 현재 입력값으로 다시 계산한다
  useEffect(() => {
    compute(form)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [basis])

  const submit = (e: FormEvent) => {
    e.preventDefault()
    compute(form)
  }
  const toggle = (g: GroupCode) =>
    setForm((f) => ({ ...f, groups: f.groups.includes(g) ? f.groups.filter((x) => x !== g) : [...f.groups, g] }))

  const exportUrl = result
    ? `/api/uniform/recommendation/export${query({ year: result.year, groups: form.groups, headcount: result.headcount, basis })}`
    : undefined

  return (
    <>
      <div className="page-head">
        <div>
          <h1>발주 추천</h1>
          <p className="muted">
            과거 주문 패턴으로 다음 발주의 품목·사이즈별 수량을 계산합니다. 결과는 저장하지 않고 입력값으로 그때그때 계산합니다.
          </p>
          <BasisHint />
        </div>
        {exportUrl && (
          <a className="primary no-print" href={exportUrl} download>
            엑셀 발주표 내려받기
          </a>
        )}
      </div>

      <Card title="입력">
        <form className="form-row" onSubmit={submit}>
          <label className="field">
            발주 연도
            <input
              type="number"
              min={2000}
              max={2100}
              value={form.year}
              onChange={(e) => setForm({ ...form, year: e.target.value })}
              style={{ width: 110 }}
            />
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
              품목 (선택 안 하면 전체)
            </legend>
            <div className="checks">
              {GROUPS.map((g) => (
                <label key={g.code}>
                  <input type="checkbox" checked={form.groups.includes(g.code)} onChange={() => toggle(g.code)} />
                  {g.label}
                </label>
              ))}
            </div>
          </fieldset>
          <button className="primary" disabled={busy}>
            {busy ? '계산하는 중…' : '추천 계산'}
          </button>
        </form>
        {error && (
          <div style={{ marginTop: 12 }}>
            <Notice tone="bad">{error}</Notice>
          </div>
        )}
      </Card>

      {result && <Result rec={result} />}
      {!result && !error && <Notice tone="info">계산하는 중입니다…</Notice>}

      <Collapse title="추천 로직 검증: 백테스트 (과거 해를 모르는 해로 두고 예측해 실제와 비교)">
        <BacktestTable />
      </Collapse>
    </>
  )
}

function Result({ rec }: { rec: Recommendation }) {
  const estimated = rec.items.filter((i) => i.priceSource === 'ESTIMATE')
  const hasHeadcount = rec.headcount !== null
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
  return (
    <>
      <div className="grid cols-4">
        <Stat
          label={`${rec.year}년 추천 수량`}
          value={num(rec.totals.quantity)}
          unit="벌"
          note={`안전재고 ${num(rec.totals.safetyQuantity)}벌 포함${hasHeadcount ? ` · ${num(rec.headcount)}명 기준` : ''}`}
        />
        <Stat label="예상 금액" value={won(rec.totals.amount)} note="품목별 단가 × 추천 수량" />
        <Stat
          label="예상 절감 (연)"
          tone="good"
          value={won(rec.totals.savedAmount)}
          note={`${num(rec.totals.savedQuantity)}벌 · 추천 수량 기준, 개선률 ${pct(rec.improvementRate, 0)} 가정`}
        />
        <Stat label="탄소 감축 (연)" tone="good" value={carbon(rec.totals.carbonKg)} note="절감 수량 × 품목별 탄소계수" />
      </div>

      {estimated.length > 0 && (
        <Notice tone="warn">
          <strong>추정 단가:</strong> {estimated.map((i) => i.label).join('·')}의 단가는 조달청 시장가 표본이 부족해 추정 단가(
          {won(estimated[0].unitPrice)})로 계산했습니다. 실제 단가는 데이터 관리에서 입력하면 바로 반영됩니다.
        </Notice>
      )}

      <Card
        title="품목별 요약"
        subtitle="수량은 안전재고 포함. '상세'를 누르면 사이즈별 수량과 산정 근거로 이동합니다."
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
                <th className="num">총량 기준</th>
                <th className="num">기본</th>
                <th className="num">안전재고</th>
                <th className="num">추천 수량</th>
                <th className="num">단가</th>
                <th className="num">금액</th>
                <th className="num">예상 절감(연)</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {rec.items.map((i) => (
                <tr key={i.group}>
                  <td>
                    <strong>{i.label}</strong>
                  </td>
                  <td className="num">{num(i.total.quantity)}벌</td>
                  <td className="num">{num(i.baseQuantity)}</td>
                  <td className="num">
                    {num(i.safetyQuantity)} <span className="muted">({pct(i.safetyRate, 0)})</span>
                  </td>
                  <td className="num">
                    <strong>{num(i.quantity)}</strong>
                  </td>
                  <td className="num">
                    {won(i.unitPrice)} <Badge tone={PRICE_SOURCE[i.priceSource].tone}>{PRICE_SOURCE[i.priceSource].label}</Badge>
                  </td>
                  <td className="num">{won(i.amount)}</td>
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
                <td />
                <td className="num">{num(rec.totals.quantity - rec.totals.safetyQuantity)}</td>
                <td className="num">{num(rec.totals.safetyQuantity)}</td>
                <td className="num">{num(rec.totals.quantity)}</td>
                <td />
                <td className="num">{won(rec.totals.amount)}</td>
                <td className="num">
                  {num(rec.totals.savedQuantity, 1)}벌 · {won(rec.totals.savedAmount)}
                </td>
                <td />
              </tr>
            </tbody>
          </table>
        </div>
      </Card>

      {rec.items.map((item) => (
        <ItemCard key={item.group} item={item} headcount={rec.headcount} open={open.has(item.group)} onToggle={() => toggle(item.group)} />
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

function ItemCard({ item, headcount, open, onToggle }: { item: ItemPlan; headcount: number | null; open: boolean; onToggle: () => void }) {
  const src = PRICE_SOURCE[item.priceSource]
  const s = item.savings
  const sp = useMemo(() => splitSizeSource(item.sizes.map((z) => z.size)), [item.sizes])
  return (
    <div id={`plan-${item.group}`} style={{ scrollMarginTop: 72 }}>
      <Card
        title={
          <span style={{ display: 'inline-flex', gap: 10, alignItems: 'center' }}>
            {item.label}{' '}
            <Badge tone={src.tone}>
              단가 {won(item.unitPrice)} · {src.label}
            </Badge>
          </span>
        }
        actions={
          <>
            <span>
              <strong style={{ fontSize: 18 }}>{num(item.quantity)}벌</strong> <span className="muted">· {won(item.amount)}</span>
            </span>
            <button className="secondary" onClick={onToggle} aria-expanded={open}>
              {open ? '접기' : '사이즈별 보기'}
            </button>
          </>
        }
      >
        {open && (
          <>
            <ul className="basis-lines" style={{ margin: '0 0 12px', padding: 0 }}>
              <li>총량: {item.total.rule}</li>
              <li>
                {item.headcountFactor !== 1 && headcount !== null && item.baseHeadcount !== null
                  ? `인원 보정: ×${num(item.headcountFactor, 3)} (대상 ${num(headcount)}명 ÷ ${item.headcountYear}년 정원 ${num(item.baseHeadcount)}명) → ${num(item.baseQuantity)}벌`
                  : `인원 보정 없음 → ${num(item.baseQuantity)}벌`}
              </li>
              <li>
                사이즈 배분: {item.sizeRule}
                {sp.source && <span className="muted"> · 사이즈 코드는 '{sp.source}' 기준</span>}
              </li>
              <li>
                안전재고: {pct(item.safetyRate, 0)} → +{num(item.safetyQuantity)}벌
              </li>
            </ul>
            <div className="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>사이즈</th>
                    <th className="num">비율</th>
                    <th className="num">기본 수량</th>
                    <th className="num">안전재고</th>
                    <th className="num">추천 수량</th>
                    <th className="num">금액</th>
                  </tr>
                </thead>
                <tbody>
                  {item.sizes.map((z) => (
                    <tr key={`${z.source}-${z.rawSize}`} style={z.quantity === 0 ? { color: '#94a3b8' } : undefined}>
                      <td>{sp.label(z.size)}</td>
                      <td className="num">{pct(z.ratio)}</td>
                      <td className="num">{num(z.base)}</td>
                      <td className="num">{num(z.safety)}</td>
                      <td className="num">
                        <strong>{num(z.quantity)}</strong>
                      </td>
                      <td className="num">{won(item.unitPrice * z.quantity)}</td>
                    </tr>
                  ))}
                  <tr className="subtotal">
                    <td>{item.label} 소계</td>
                    <td />
                    <td className="num">{num(item.baseQuantity)}</td>
                    <td className="num">{num(item.safetyQuantity)}</td>
                    <td className="num">{num(item.quantity)}</td>
                    <td className="num">{won(item.amount)}</td>
                  </tr>
                </tbody>
              </table>
            </div>
            {s && (
              <p className="muted" style={{ marginTop: 10 }}>
                예상 절감(연): 사이즈 미스매치율 {pct(s.mismatchRate)} × {num(s.annualQuantity)}벌 × 개선률 →{' '}
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
                          {won(m.ecoMedianPrice)}{' '}
                          {diff !== null && (
                            <span className="muted">
                              ({diff >= 0 ? '+' : ''}
                              {num(diff * 100, 0)}%)
                            </span>
                          )}
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
          녹색제품 정보조회(환경표지 인증기간·기업)는 아직 가져오지 않았습니다. 가격은 조달청 쇼핑몰 값(중앙값)입니다.
        </p>
      )}
    </Card>
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
