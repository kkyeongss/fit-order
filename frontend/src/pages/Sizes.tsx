import type { ChartConfiguration } from 'chart.js'
import { useMemo, useState } from 'react'
import { GROUP_COLOR, num, pct, splitSizeSource } from '../format'
import { useBasis, useGet } from '../hooks'
import type { SizeAnalysis } from '../types'
import { Async, Badge, BasisHint, Card, ChartBox, Notice, Stat, Tabs } from '../ui'

export default function Sizes() {
  const { basis } = useBasis()
  const state = useGet<SizeAnalysis[]>(`/api/uniform/sizes?basis=${basis}`)
  const [group, setGroup] = useState<string | null>(null)

  return (
    <>
      <div className="page-head">
        <div>
          <h1>사이즈 분석</h1>
          <p className="muted">예전 사이즈 비율대로 주문하면 요즘 수요와 얼마나 어긋나는지</p>
          <BasisHint />
        </div>
      </div>
      <Async state={state}>
        {(all) => {
          if (all.length === 0) return <Notice tone="warn">분석할 주문 데이터가 없습니다. 데이터 관리에서 근무복 CSV를 불러오세요.</Notice>
          const current = all.find((a) => a.group === group) ?? all[0]
          return (
            <>
              <Tabs tabs={all.map((a) => ({ id: a.group, label: a.label }))} value={current.group} onChange={setGroup} />
              <Detail analysis={current} />
            </>
          )
        }}
      </Async>
    </>
  )
}

function Detail({ analysis }: { analysis: SizeAnalysis }) {
  const c = analysis.comparison
  const rising = analysis.sizes.filter((s) => s.gap > 0.005).sort((a, b) => b.gap - a.gap)
  const falling = analysis.sizes.filter((s) => s.gap < -0.005).sort((a, b) => a.gap - b.gap)
  const sizeNames = useMemo(() => {
    const names = analysis.sizes.map((s) => s.size)
    for (const y of analysis.years) for (const k of Object.keys(y.shares)) if (!names.includes(k)) names.push(k)
    return names
  }, [analysis])
  const sp = useMemo(() => splitSizeSource(sizeNames), [sizeNames])

  return (
    <>
      {sp.source && <p className="muted">사이즈 코드는 모두 '{sp.source}' 기준입니다.</p>}
      <div className="grid cols-3">
        <Stat
          label="사이즈 불일치율"
          value={analysis.mismatchRate === null ? '-' : pct(analysis.mismatchRate)}
          note={
            c ? `${c.previous.from}~${c.previous.to}년 비율로 ${c.recent.from}~${c.recent.to}년을 주문했을 때` : '비교할 기간이 부족합니다'
          }
        />
        <Stat
          label="늘어난 사이즈"
          value={rising.length}
          unit="개"
          note={
            rising.length
              ? rising
                  .slice(0, 3)
                  .map((s) => sp.label(s.size))
                  .join(', ')
              : '없음'
          }
        />
        <Stat
          label="줄어든 사이즈"
          value={falling.length}
          unit="개"
          note={
            falling.length
              ? falling
                  .slice(0, 3)
                  .map((s) => sp.label(s.size))
                  .join(', ')
              : '없음'
          }
        />
      </div>

      {c && analysis.sizes.length > 0 && (
        <Card
          title="두 기간 비교"
          subtitle={`이전 ${c.previous.from}~${c.previous.to}년 vs 최근 ${c.recent.from}~${c.recent.to}년 · 사이즈별 주문 비중`}
        >
          {rising.length > 0 && (
            <Notice tone="info">
              이전 비율대로 주문하면{' '}
              <strong>
                {rising
                  .slice(0, 3)
                  .map((s) => `${sp.label(s.size)}(+${num(s.gap * 100, 1)}%p)`)
                  .join(', ')}
              </strong>{' '}
              사이즈가 모자라고
              {falling.length > 0 && (
                <strong>
                  {' '}
                  {falling
                    .slice(0, 3)
                    .map((s) => sp.label(s.size))
                    .join(', ')}
                </strong>
              )}
              {falling.length > 0 ? ' 사이즈가 남습니다.' : ' 그만큼 다른 사이즈가 남습니다.'}
            </Notice>
          )}
          <CompareChart analysis={analysis} label={sp.label} />
          <div className="table-wrap" style={{ marginTop: 12 }}>
            <table>
              <thead>
                <tr>
                  <th>사이즈</th>
                  <th className="num">이전 수량</th>
                  <th className="num">이전 비중</th>
                  <th className="num">최근 수량</th>
                  <th className="num">최근 비중</th>
                  <th className="num">증감(%p)</th>
                  <th>판정</th>
                </tr>
              </thead>
              <tbody>
                {analysis.sizes.map((s) => (
                  <tr key={s.size}>
                    <td>{sp.label(s.size)}</td>
                    <td className="num">{num(s.previousQuantity)}</td>
                    <td className="num">{pct(s.previousShare)}</td>
                    <td className="num">{num(s.recentQuantity)}</td>
                    <td className="num">{pct(s.recentShare)}</td>
                    <td className={`num ${s.gap > 0.005 ? 'up' : s.gap < -0.005 ? 'down' : ''}`}>
                      {s.gap > 0 ? '+' : ''}
                      {num(s.gap * 100, 1)}
                    </td>
                    <td>
                      {s.gap > 0.005 ? (
                        <Badge tone="bad">모자랄 구간</Badge>
                      ) : s.gap < -0.005 ? (
                        <Badge tone="info">남을 구간</Badge>
                      ) : (
                        <Badge>비슷</Badge>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </Card>
      )}

      <Card title="연도별 사이즈 비중" subtitle="각 해 주문 중 사이즈 비율 · *는 1년치가 안 되는 해">
        {analysis.years.length === 0 ? (
          <p className="muted">데이터가 없습니다.</p>
        ) : (
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>사이즈</th>
                  {analysis.years.map((y) => (
                    <th className="num" key={y.year}>
                      {y.year}
                      {y.complete ? '' : '*'}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {sizeNames.map((name) => (
                  <tr key={name}>
                    <td>{sp.label(name)}</td>
                    {analysis.years.map((y) => {
                      const share = y.shares[name] ?? 0
                      return (
                        <td
                          className="heat"
                          key={y.year}
                          style={{
                            background: share > 0 ? `rgba(47, 111, 176, ${Math.min(0.75, share * 1.4)})` : undefined,
                            color: share > 0.35 ? '#fff' : undefined,
                          }}
                        >
                          {share > 0 ? pct(share, 0) : ''}
                        </td>
                      )
                    })}
                  </tr>
                ))}
                <tr className="subtotal">
                  <td>주문량(벌)</td>
                  {analysis.years.map((y) => (
                    <td className="num" key={y.year}>
                      {num(y.quantity)}
                    </td>
                  ))}
                </tr>
              </tbody>
            </table>
          </div>
        )}
      </Card>
    </>
  )
}

function CompareChart({ analysis, label }: { analysis: SizeAnalysis; label: (name: string) => string }) {
  const config = useMemo<ChartConfiguration>(() => {
    const c = analysis.comparison!
    const base = GROUP_COLOR[analysis.label] ?? '#4c78a8'
    return {
      type: 'bar',
      data: {
        labels: analysis.sizes.map((s) => label(s.size)),
        datasets: [
          {
            label: `이전 ${c.previous.from}~${c.previous.to}`,
            data: analysis.sizes.map((s) => +(s.previousShare * 100).toFixed(1)),
            backgroundColor: '#b8c4d6',
          },
          {
            label: `최근 ${c.recent.from}~${c.recent.to}`,
            data: analysis.sizes.map((s) => +(s.recentShare * 100).toFixed(1)),
            backgroundColor: base,
          },
        ],
      },
      options: {
        scales: {
          y: { beginAtZero: true, title: { display: true, text: '주문 비중 (%)' } },
          x: { ticks: { maxRotation: 50, autoSkip: false } },
        },
        plugins: { tooltip: { callbacks: { label: (i) => `${i.dataset.label}: ${i.parsed.y}%` } } },
      },
    }
  }, [analysis, label])
  return <ChartBox config={config} height={320} label={`${analysis.label} 사이즈별 주문 비중, 이전 기간과 최근 기간 비교 막대 차트`} />
}
