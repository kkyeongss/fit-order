import { Chart, type ChartConfiguration } from 'chart.js/auto'
import { useMemo, useState } from 'react'
import { carbon, dateTime, GROUP_COLOR, GROUPS, num, pct, pctValue, PRICE_SOURCE, won } from '../format'
import { useBasis, useGet } from '../hooks'
import type { MetricValues, SavingsReport, Summary } from '../types'
import { Async, BasisHint, Card, ChartBox, Notice, Stat } from '../ui'

const MID = '1f3a5f'

/** 지표의 '최근 구간'(예: 2023-2025) 전체 값. 구간 지표가 없으면 null */
function latestPeriodValue(metrics: MetricValues[], code: string): { period: string; value: number } | null {
  const m = metrics.find((x) => x.code === code)
  const rows = (m?.values ?? []).filter((v) => v.dimension === '' && v.period.includes('-'))
  if (rows.length === 0) return null
  const latest = rows.reduce((a, b) => (a.period > b.period ? a : b))
  return { period: latest.period, value: latest.value }
}

/** 연도 지표의 연평균(안정 구간) */
function yearlyAverage(metrics: MetricValues[], code: string, from: number | null, to: number | null): number | null {
  if (from === null || to === null) return null
  const m = metrics.find((x) => x.code === code)
  const vals = (m?.values ?? []).filter((v) => v.dimension === '' && /^\d{4}$/.test(v.period) && +v.period >= from && +v.period <= to)
  return vals.length === 0 ? null : vals.reduce((s, v) => s + v.value, 0) / vals.length
}

export default function Dashboard() {
  const { basis } = useBasis()
  const summary = useGet<Summary>(`/api/uniform/summary?basis=${basis}`)
  const metrics = useGet<MetricValues[]>(`/api/esg/items/uniform/metrics?basis=${basis}`)
  const savings = useGet<SavingsReport>(`/api/uniform/savings?basis=${basis}`)

  return (
    <>
      <div className="page-head">
        <div>
          <h1>ESG 대시보드</h1>
          <p className="muted">근무복 주문 현황과 사이즈 불일치로 인한 과잉 발주 효과</p>
          <BasisHint />
        </div>
      </div>
      <Guide />
      <Async state={summary}>
        {(s) => (
          <Async state={metrics}>{(m) => <Async state={savings}>{(sv) => <Body summary={s} metrics={m} savings={sv} />}</Async>}</Async>
        )}
      </Async>
    </>
  )
}

function Body({ summary, metrics, savings }: { summary: Summary; metrics: MetricValues[]; savings: SavingsReport }) {
  const avg = summary.annualAverage
  const mismatch = latestPeriodValue(metrics, 'uniform.size-mismatch-rate')
  const savedAmount = latestPeriodValue(metrics, 'uniform.saved-amount')
  const savedQty = latestPeriodValue(metrics, 'uniform.saved-quantity')
  const carbonKg = latestPeriodValue(metrics, 'uniform.carbon-reduction')
  const small = yearlyAverage(metrics, 'uniform.small-order-ratio', avg.fromYear, avg.toYear)

  const duplicates = summary.years
    .filter((y) => y.complete && y.uniqueQuantity + y.duplicateQuantity > 0)
    .map((y) => ({ year: y.year, share: y.duplicateQuantity / (y.uniqueQuantity + y.duplicateQuantity) }))
    .filter((d) => d.share >= 0.3)

  return (
    <>
      <div className="grid cols-4">
        <Stat
          label="연평균 주문량"
          value={num(avg.quantity)}
          unit="벌"
          note={avg.fromYear ? `${avg.fromYear}~${avg.toYear}년 평균 · 연 ${num(avg.orderDays, 0)}일 주문` : undefined}
        />
        <Stat
          label="사이즈 불일치율"
          value={mismatch ? pctValue(mismatch.value) : '-'}
          note={mismatch ? `${mismatch.period}년, 이전 3년 사이즈 비율로 주문했을 때 어긋나는 몫` : '비교할 기간이 부족합니다'}
        />
        <Stat
          label="예상 절감액 (연)"
          tone="good"
          value={savedAmount ? won(savedAmount.value) : '-'}
          note={
            savedQty
              ? `절감 ${num(savedQty.value)}벌/년 · 개선률 ${pct(savings.improvementRate, 0)} 가정 · ${pct(savings.estimatedPriceShare, 0)}는 추정 단가 기준`
              : undefined
          }
        />
        <Stat label="탄소 감축량 (연)" tone="good" value={carbonKg ? carbon(carbonKg.value) : '-'} note="절감 수량 × 품목별 탄소계수" />
      </div>

      <Notice tone="info">
        <strong>데이터 기준일:</strong> 마지막 주문일 {summary.dataAsOf.lastOrderDate ?? '-'}
        {summary.dataAsOf.loads.length > 0 &&
          ` · ${summary.dataAsOf.loads.map((l) => `${l.name} ${l.baseYear ?? ''}년(${dateTime(l.loadedAt)} 불러옴)`).join(', ')}`}
        . 이후 주문은 반영되지 않았습니다.
      </Notice>

      {duplicates.length > 0 && (
        <Notice tone="warn">
          <strong>모든 칸이 똑같은 행(완전 중복 행)이 반복된 해가 있습니다.</strong> 해당 해 주문 수량 중 중복 행 비중:{' '}
          {duplicates.map((d) => `${d.year}년 ${pct(d.share, 0)}`).join(', ')}. 실제 재주문인지 입력 중복인지는 원본으로 확인해야 해서,
          지우지 않고 표시만 했습니다. 위 '중복 제거'(같은 행은 한 번만)와 '원자료'(모두 셈) 전환으로 두 경우를 비교하세요.
        </Notice>
      )}

      <div className="grid cols-2">
        <Card title="연도별 주문량" subtitle="품목군별 · *는 1년치가 안 되는 해(평균·추천에서 제외)">
          <YearGroupChart summary={summary} />
        </Card>
        <Card title="중복 행 비중" subtitle="고유 = 같은 행의 첫 번째, 중복 = 같은 행의 두 번째부터 · 원본 파일 기준">
          <DuplicateChart summary={summary} />
        </Card>
        <Card title="연도별 추정 구매 금액" subtitle="그해 주문 수량 × 현재 단가(실제 지출액 아님) · *는 1년치가 안 되는 해">
          <AmountChart summary={summary} />
        </Card>
        <Card title="월별 주문 집중도" subtitle={`${avg.fromYear ?? ''}~${avg.toYear ?? ''}년 합계 중 비중 · 4·8월 강조`}>
          <MonthChart summary={summary} />
        </Card>
        <Card title="1인당 지급량" subtitle="연간 주문량 ÷ 그해 마지막 기준일자 정원 (2021년부터)">
          <PerCapitaChart summary={summary} />
        </Card>
      </div>

      <SavingsBasis savings={savings} />

      <Card title="발주 패턴" subtitle="두 데이터를 합친 주문일 기준">
        <div className="grid cols-3">
          <Stat label="연평균 발주 일수" value={num(avg.orderDays, 1)} unit="일" note="고유 주문일 수" />
          <Stat
            label="소량 발주 비율"
            value={small === null ? '-' : pctValue(small, 0)}
            note="하루 10벌 이하 주문일 ÷ 전체 주문일 (연평균)"
          />
          <Stat
            label="월 집중도 (4월+8월)"
            value={pct(
              summary.months.filter((m) => m.month === 4 || m.month === 8).reduce((s, m) => s + m.share, 0),
              0,
            )}
            note="연 2회 정기 발주로 묶을 수 있는 몫"
          />
        </div>
      </Card>
    </>
  )
}

const alpha = (color: string, complete: boolean) => (complete ? color : `${color}66`)

function YearGroupChart({ summary }: { summary: Summary }) {
  const config = useMemo<ChartConfiguration>(() => {
    const labels = summary.years.map((y) => `${y.year}${y.complete ? '' : '*'}`)
    const present = GROUPS.map((g) => g.label).filter((label) => summary.years.some((y) => (y.byGroup[label] ?? 0) > 0))
    return {
      type: 'bar',
      data: {
        labels,
        datasets: present.map((label) => ({
          label,
          data: summary.years.map((y) => y.byGroup[label] ?? 0),
          backgroundColor: summary.years.map((y) => alpha(GROUP_COLOR[label], y.complete)),
        })),
      },
      options: {
        scales: { x: { stacked: true }, y: { stacked: true, title: { display: true, text: '벌' } } },
        plugins: {
          // 불완전 연도를 연하게 칠해도 범례는 진한 원래 색으로
          legend: {
            labels: {
              generateLabels: (chart) =>
                Chart.defaults.plugins.legend.labels
                  .generateLabels(chart)
                  .map((l) => ({ ...l, fillStyle: GROUP_COLOR[l.text], strokeStyle: GROUP_COLOR[l.text] })),
            },
          },
          tooltip: { mode: 'index', callbacks: { footer: (items) => `합계 ${num(items.reduce((s, i) => s + (i.parsed.y ?? 0), 0))}벌` } },
        },
      },
    }
  }, [summary])
  return <ChartBox config={config} label="연도별 품목군별 주문량 누적 막대 차트" />
}

function DuplicateChart({ summary }: { summary: Summary }) {
  const config = useMemo<ChartConfiguration>(() => {
    const years = summary.years
    return {
      type: 'bar',
      data: {
        labels: years.map((y) => `${y.year}${y.complete ? '' : '*'}`),
        datasets: [
          { label: '고유', data: years.map((y) => y.uniqueQuantity), backgroundColor: `#${MID}` },
          { label: '완전 중복', data: years.map((y) => y.duplicateQuantity), backgroundColor: '#f58518' },
        ],
      },
      options: {
        scales: { x: { stacked: true }, y: { stacked: true, title: { display: true, text: '벌' } } },
        plugins: {
          tooltip: {
            mode: 'index',
            callbacks: {
              footer: (items) => {
                const total = items.reduce((s, i) => s + (i.parsed.y ?? 0), 0)
                const dup = items.find((i) => i.dataset.label === '완전 중복')?.parsed.y ?? 0
                return total ? `중복 ${pct(dup / total, 0)}` : ''
              },
            },
          },
        },
      },
    }
  }, [summary])
  return <ChartBox config={config} label="연도별 고유 수량과 완전 중복 수량 막대 차트" />
}

function MonthChart({ summary }: { summary: Summary }) {
  const config = useMemo<ChartConfiguration>(() => {
    const focus = (m: number) => m === 4 || m === 8
    return {
      type: 'bar',
      data: {
        labels: summary.months.map((m) => `${m.month}월`),
        datasets: [
          {
            label: '비중',
            data: summary.months.map((m) => +(m.share * 100).toFixed(1)),
            backgroundColor: summary.months.map((m) => (focus(m.month) ? '#f58518' : '#9fb3cc')),
          },
        ],
      },
      options: {
        scales: { y: { title: { display: true, text: '%' }, beginAtZero: true } },
        plugins: {
          legend: { display: false },
          tooltip: { callbacks: { label: (i) => `${i.parsed.y}% · ${num(summary.months[i.dataIndex].quantity)}벌` } },
        },
      },
    }
  }, [summary])
  return <ChartBox config={config} label="월별 주문 집중도 막대 차트, 4월과 8월 강조" />
}

function PerCapitaChart({ summary }: { summary: Summary }) {
  const config = useMemo<ChartConfiguration>(() => {
    const rows = summary.perCapita
    return {
      type: 'line',
      data: {
        labels: rows.map((r) => String(r.year)),
        datasets: [
          {
            label: '1인당 지급량',
            data: rows.map((r) => +r.perCapita.toFixed(2)),
            borderColor: '#1f3a5f',
            backgroundColor: '#1f3a5f',
            tension: 0,
            pointRadius: 4,
          },
        ],
      },
      options: {
        scales: { y: { beginAtZero: true, title: { display: true, text: '벌/인' } } },
        plugins: {
          legend: { display: false },
          tooltip: {
            callbacks: {
              afterLabel: (i) =>
                `주문 ${num(rows[i.dataIndex].quantity)}벌 ÷ 정원 ${num(rows[i.dataIndex].headcount)}명 (${rows[i.dataIndex].baseDate})`,
            },
          },
        },
      },
    }
  }, [summary])
  if (summary.perCapita.length === 0) return <p className="muted">정원 데이터가 없습니다. 데이터 관리에서 정원현황을 적재하세요.</p>
  return <ChartBox config={config} label="연도별 1인당 지급량 선 차트" />
}

/** 처음 쓰는 사람을 위한 사용 순서. 닫으면 이 브라우저에서는 다시 보이지 않는다. */
function Guide() {
  const [hidden, setHidden] = useState(() => localStorage.getItem('guide-closed') === '1')
  if (hidden) return null
  return (
    <Notice tone="info">
      <strong>처음이신가요?</strong> ① 이 화면에서 주문 현황을 본다 → ② '사이즈 분석'에서 사이즈 쏠림을 확인한다 → ③ '발주 추천'에서
      연도·인원을 정하고 재고를 입력한다 → ④ 엑셀 발주서나 결재용 보고서로 내려받는다. 데이터에 문제가 있으면 '데이터 개선 요청'에 정리해
      두었습니다.{' '}
      <button
        className="link"
        onClick={() => {
          localStorage.setItem('guide-closed', '1')
          setHidden(true)
        }}
      >
        다시 보지 않기
      </button>
    </Notice>
  )
}

/** 절감 효과가 어떤 가정 위에 있는지: 개선률 범위, 추정 단가 비중, 품목별 단가·탄소계수 출처 */
function SavingsBasis({ savings }: { savings: SavingsReport }) {
  return (
    <Card title="절감 효과 산출 근거" subtitle="절감 = 연 주문량 × 사이즈 불일치율 × 개선률. 개선률은 가정이라 범위로 함께 봅니다.">
      <div className="grid cols-3">
        {savings.scenarios.map((x) => (
          <Stat
            key={x.rate}
            label={`개선률 ${pct(x.rate, 0)}${x.rate === savings.improvementRate ? ' (기본)' : ''}`}
            value={won(x.savedAmount)}
            note={`${num(x.savedQuantity)}벌 · 탄소 ${carbon(x.carbonKg)}`}
          />
        ))}
      </div>
      {savings.estimatedPriceShare > 0 && (
        <p style={{ margin: '10px 0' }}>
          <Notice tone="warn">
            절감액의 <strong>{pct(savings.estimatedPriceShare, 0)}</strong>는 추정 단가({savings.estimatedGroups.join('·')})에 기대고
            있습니다. 실제 단가를 '발주 추천'에서 입력하면 바로 바뀝니다.
          </Notice>
        </p>
      )}
      <div className="table-wrap">
        <table>
          <thead>
            <tr>
              <th>품목</th>
              <th className="num">불일치율</th>
              <th className="num">연 주문량</th>
              <th className="num">절감 수량</th>
              <th className="num">단가</th>
              <th className="num">절감액</th>
              <th className="num">탄소계수</th>
            </tr>
          </thead>
          <tbody>
            {savings.groups.map((g) => (
              <tr key={g.group}>
                <td>{g.label}</td>
                <td className="num">{pct(g.mismatchRate)}</td>
                <td className="num">{num(g.annualQuantity)}</td>
                <td className="num">{num(g.savedQuantity, 1)}</td>
                <td className="num">
                  {won(g.unitPrice)} <span className="muted">{PRICE_SOURCE[g.priceSource].label}</span>
                </td>
                <td className="num">{won(g.savedAmount)}</td>
                <td className="num" title={g.carbonSource}>
                  {num(g.carbonFactor, 1)} kgCO₂e/벌
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <p className="muted">
        탄소계수는 해외 일반 값 기반의 추정치입니다(출처는 칸에 마우스를 올리면 보입니다). 보고서에 쓸 때는 추정치로 표시하세요.
      </p>
    </Card>
  )
}

function AmountChart({ summary }: { summary: Summary }) {
  const config = useMemo<ChartConfiguration>(() => {
    const years = summary.years
    return {
      type: 'bar',
      data: {
        labels: years.map((y) => `${y.year}${y.complete ? '' : '*'}`),
        datasets: [
          {
            label: '추정 구매 금액',
            data: years.map((y) => Math.round(y.amount / 10000)),
            backgroundColor: years.map((y) => alpha('#1f3a5f', y.complete)),
          },
        ],
      },
      options: {
        scales: { y: { beginAtZero: true, title: { display: true, text: '만원' } } },
        plugins: {
          legend: { display: false },
          tooltip: { callbacks: { label: (i) => `${num(i.parsed.y)}만원 (${num(years[i.dataIndex].quantity)}벌)` } },
        },
      },
    }
  }, [summary])
  return <ChartBox config={config} label="연도별 추정 구매 금액 막대 차트" />
}
