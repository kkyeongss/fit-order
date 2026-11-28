import { Chart, type ChartConfiguration } from 'chart.js/auto'
import { useMemo } from 'react'
import { carbon, GROUP_COLOR, GROUPS, num, pct, pctValue, won } from '../format'
import { useBasis, useGet } from '../hooks'
import type { MetricValues, Summary } from '../types'
import { Async, Card, ChartBox, Notice, Stat } from '../ui'

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

  return (
    <>
      <div className="page-head">
        <div>
          <h1>ESG 대시보드</h1>
          <p className="muted">
            근무복 주문 현황과 사이즈 미스매치로 인한 과잉 발주 효과 · {basis === 'dedup' ? '중복 제거 기준' : '원자료 기준'}
          </p>
        </div>
      </div>
      <Async state={summary}>{(s) => <Async state={metrics}>{(m) => <Body summary={s} metrics={m} />}</Async>}</Async>
    </>
  )
}

function Body({ summary, metrics }: { summary: Summary; metrics: MetricValues[] }) {
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
          label="사이즈 미스매치율"
          value={mismatch ? pctValue(mismatch.value) : '-'}
          note={mismatch ? `${mismatch.period}년, 이전 3년 사이즈 비율로 주문했을 때 어긋나는 몫` : '비교할 기간이 부족합니다'}
        />
        <Stat
          label="예상 절감액 (연)"
          tone="good"
          value={savedAmount ? won(savedAmount.value) : '-'}
          note={savedQty ? `절감 ${num(savedQty.value)}벌/년 · 개선률 50% 가정` : undefined}
        />
        <Stat label="탄소 감축량 (연)" tone="good" value={carbonKg ? carbon(carbonKg.value) : '-'} note="절감 수량 × 품목별 탄소계수" />
      </div>

      {duplicates.length > 0 && (
        <Notice tone="warn">
          <strong>완전히 같은 행이 반복된 해가 있습니다.</strong> {duplicates.map((d) => `${d.year}년 ${pct(d.share, 0)}`).join(', ')}. 실제
          재주문인지 입력 중복인지는 원본으로 확인해야 합니다. 위 '중복 제거'·'원자료' 전환으로 두 경우를 비교할 수 있습니다.
        </Notice>
      )}

      <div className="grid cols-2">
        <Card title="연도별 주문량" subtitle="품목군별 · *는 1년치가 안 되는 해(평균·추천에서 제외)">
          <YearGroupChart summary={summary} />
        </Card>
        <Card title="중복 행 비중" subtitle="완전히 같은 행(고유 / 중복) · 원본 파일 기준">
          <DuplicateChart summary={summary} />
        </Card>
        <Card title="월별 주문 집중도" subtitle={`${avg.fromYear ?? ''}~${avg.toYear ?? ''}년 합계 중 비중 · 4·8월 강조`}>
          <MonthChart summary={summary} />
        </Card>
        <Card title="1인당 지급량" subtitle="연간 주문량 ÷ 그해 마지막 기준일자 정원 (2021년부터)">
          <PerCapitaChart summary={summary} />
        </Card>
      </div>

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
