import { useEffect, useState } from 'react'
import { api } from '../api'
import { carbon, dateTime, num, pct, PRICE_SOURCE, signedPct, won } from '../format'
import { useBasis, useGet, useHashRoute, useUser } from '../hooks'
import type { Recommendation, SavingsReport, Summary } from '../types'
import { Async, Notice } from '../ui'

/** 결재용 한 장 보고서(인쇄·PDF 저장). 발주 추천 화면의 입력값을 주소로 받아 같은 계산을 다시 한다. */
export default function Report() {
  const { query } = useHashRoute('report')
  const { basis: contextBasis } = useBasis()
  const user = useUser()
  const basis = query.get('basis') === 'raw' ? 'raw' : query.get('basis') === 'dedup' ? 'dedup' : contextBasis
  const [rec, setRec] = useState<Recommendation | null>(null)
  const [error, setError] = useState<string | null>(null)
  const savings = useGet<SavingsReport>(`/api/uniform/savings?basis=${basis}`)
  const summary = useGet<Summary>(`/api/uniform/summary?basis=${basis}`)

  const key = query.toString()
  useEffect(() => {
    const totals: Record<string, number> = {}
    for (const part of (query.get('totals') ?? '').split(',').filter(Boolean)) {
      const [g, v] = part.split(':')
      totals[g] = Number(v)
    }
    api
      .post<Recommendation>('/api/uniform/recommendation', {
        year: query.get('year') ? Number(query.get('year')) : null,
        headcount: query.get('headcount') ? Number(query.get('headcount')) : null,
        groups: query.getAll('groups').length ? query.getAll('groups') : null,
        basis,
        totals: Object.keys(totals).length ? totals : null,
      })
      .then((r) => {
        setRec(r)
        setError(null)
      })
      .catch((e: Error) => setError(e.message))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [key, basis])

  if (error) return <Notice tone="bad">{error}</Notice>
  if (!rec) return <Notice tone="info">보고서를 만드는 중입니다…</Notice>
  const t = rec.totals

  return (
    <div className="report">
      <div className="no-print report-actions">
        <button className="primary" onClick={() => window.print()}>
          인쇄 / PDF로 저장
        </button>
        <span className="muted">인쇄 창에서 '대상'을 'PDF로 저장'으로 고르면 파일이 됩니다. A4 세로 한 장 안팎입니다.</span>
      </div>

      <header className="report-head">
        <div>
          <p className="muted">
            핏오더 자동 작성 · {dateTime(new Date().toISOString())} · 작성자 {user.username}
          </p>
          <h1>{rec.year}년 근무복 발주 추천 보고서</h1>
          <p className="muted">
            집계 기준 {basis === 'dedup' ? '중복 제거' : '원자료'} · 대상 인원 {rec.headcount === null ? '-' : `${num(rec.headcount)}명`} ·
            데이터 마지막 주문일 {rec.lastOrderDate ?? '-'}
          </p>
        </div>
        <table className="sign">
          <tbody>
            <tr>
              <th>담당</th>
              <th>팀장</th>
              <th>부서장</th>
            </tr>
            <tr>
              <td />
              <td />
              <td />
            </tr>
          </tbody>
        </table>
      </header>

      <h2>1. 요약</h2>
      <table className="rt">
        <tbody>
          <tr>
            <th>필요 수량(안전재고 포함)</th>
            <td>
              {num(t.quantity)}벌{' '}
              <span className="muted">
                (참고 범위 {num(t.range.low)}~{num(t.range.high)}벌, 백테스트 평균 오차 약 {pct(t.range.errorRate, 0)})
              </span>
            </td>
            <th>발주 수량(재고 반영)</th>
            <td>
              <strong>{num(t.orderQuantity)}벌</strong> <span className="muted">(재고 {num(t.stockTotal)}벌 차감)</span>
            </td>
          </tr>
          <tr>
            <th>예상 발주 금액</th>
            <td>
              <strong>{won(t.orderAmount)}</strong>
            </td>
            <th>예상 절감(연, 추정)</th>
            <td>
              {won(t.savedAmount)} · {num(t.savedQuantity)}벌 · 탄소 {carbon(t.carbonKg)}
            </td>
          </tr>
        </tbody>
      </table>

      <h2>2. 품목별 발주 내역</h2>
      <table className="rt">
        <thead>
          <tr>
            <th>품목</th>
            <th>필요</th>
            <th>재고</th>
            <th>발주</th>
            {t.rounds.map((r) => (
              <th key={r.month}>{r.month}월</th>
            ))}
            <th>단가(출처)</th>
            <th>금액</th>
          </tr>
        </thead>
        <tbody>
          {rec.items.map((i) => (
            <tr key={i.group}>
              <td>{i.label}</td>
              <td className="n">{num(i.quantity)}</td>
              <td className="n">{num(i.stockTotal)}</td>
              <td className="n">
                <strong>{num(i.orderQuantity)}</strong>
              </td>
              {i.rounds.map((r) => (
                <td className="n" key={r.index}>
                  {num(r.quantity)}
                </td>
              ))}
              <td className="n">
                {won(i.unitPrice)} <span className="muted">({PRICE_SOURCE[i.priceSource].label})</span>
              </td>
              <td className="n">{won(i.orderAmount)}</td>
            </tr>
          ))}
          <tr className="tot">
            <td>합계</td>
            <td className="n">{num(t.quantity)}</td>
            <td className="n">{num(t.stockTotal)}</td>
            <td className="n">{num(t.orderQuantity)}</td>
            {t.rounds.map((r) => (
              <td className="n" key={r.month}>
                {num(r.quantity)}
              </td>
            ))}
            <td />
            <td className="n">{won(t.orderAmount)}</td>
          </tr>
        </tbody>
      </table>
      {t.rounds.length > 0 && (
        <p className="muted">
          정기 발주: {t.rounds.map((r) => `${r.month}월 ${num(r.quantity)}벌(${won(r.amount)})`).join(' · ')} — 과거 수량이 몰린 달의 비중(
          {t.rounds.map((r) => pct(r.share, 0)).join(', ')})으로 나눴습니다.
        </p>
      )}

      <h2>3. 근거와 확인할 점</h2>
      <ul className="report-list">
        <li>
          <strong>산정 방법</strong>: 품목별 직전 2년 평균(격년 품목은 같은 홀짝 해 값) → 인원 보정 → 최근 3년 사이즈 비율(3:2:1) → 안전재고
          순으로 계산한 규칙 기반 추천입니다. 2022~2025년 백테스트에서 사이즈 오차가 단순 예측보다 작았습니다.
        </li>
        {rec.otherBasis && (
          <li>
            <strong>중복 행 처리에 따른 범위</strong>: {basis === 'dedup' ? '중복 제거' : '원자료'} 기준 {num(t.quantity)}벌, 반대 기준{' '}
            {num(rec.otherBasis.quantity)}벌(
            {signedPct(rec.otherBasis.quantity / Math.max(1, t.quantity) - 1)}). 같은 행이 실제 반복 주문인지는 기관 확인이 필요합니다.
          </li>
        )}
        <Async state={savings}>
          {(s) => (
            <li>
              <strong>절감 효과의 가정</strong>: 개선률 {pct(s.improvementRate, 0)} 가정({s.scenarios.map((x) => pct(x.rate, 0)).join('·')}{' '}
              범위 {won(s.scenarios[0]?.savedAmount)}~{won(s.scenarios[2]?.savedAmount)}). 절감액의 {pct(s.estimatedPriceShare, 0)}가 추정
              단가({s.estimatedGroups.join('·') || '없음'})에 기대므로 실제 단가를 입력하면 달라집니다. 탄소계수는 해외 일반값이라 보고서용
              수치는 추정치입니다.
            </li>
          )}
        </Async>
        {rec.items
          .flatMap((i) => i.warnings.map((w) => ({ label: i.label, w })))
          .map((x) => (
            <li key={x.label + x.w}>
              <strong>{x.label}</strong>: {x.w}
            </li>
          ))}
        {rec.warnings.map((w) => (
          <li key={w}>{w}</li>
        ))}
      </ul>
      <Async state={summary}>
        {(s) => (
          <p className="muted">
            데이터 기준: 마지막 주문일 {s.dataAsOf.lastOrderDate ?? '-'} ·{' '}
            {s.dataAsOf.loads.map((l) => `${l.name} ${l.baseYear ?? ''}년(${dateTime(l.loadedAt)} 불러옴)`).join(', ')}
          </p>
        )}
      </Async>

      <h2>4. 사이즈별 상세</h2>
      {rec.items.map((i) => (
        <div key={i.group} className="report-block">
          <p>
            <strong>{i.label}</strong> <span className="muted">— {i.total.rule}</span>
          </p>
          <table className="rt small">
            <thead>
              <tr>
                <th>사이즈</th>
                <th>필요</th>
                <th>재고</th>
                <th>발주</th>
                {t.rounds.map((r) => (
                  <th key={r.month}>{r.month}월</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {i.sizes
                .filter((z) => z.quantity > 0 || z.stock > 0)
                .map((z) => (
                  <tr key={`${z.source}-${z.rawSize}`}>
                    <td>
                      {z.size}
                      {z.unknownCode ? ' (뜻 미확인)' : ''}
                    </td>
                    <td className="n">{num(z.quantity)}</td>
                    <td className="n">{num(z.stock)}</td>
                    <td className="n">
                      <strong>{num(z.orderQuantity)}</strong>
                    </td>
                    {z.rounds.map((q, idx) => (
                      <td className="n" key={idx}>
                        {num(q)}
                      </td>
                    ))}
                  </tr>
                ))}
            </tbody>
          </table>
        </div>
      ))}

      <h2>5. 결재 의견</h2>
      <div className="opinion" />
      <p className="muted">
        이 보고서의 수치는 공공데이터(근무복 주문현황·주문현황정보·정원현황, 조달청 쇼핑몰 가격)로 계산한 추정치입니다. 확정 전에 현재
        재고와 실제 단가를 확인하세요.
      </p>
    </div>
  )
}
