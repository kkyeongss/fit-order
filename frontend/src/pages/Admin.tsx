import { useState, type FormEvent } from 'react'
import { api } from '../api'
import { dateTime, num, PRICE_SOURCE, ROLE_LABEL, won } from '../format'
import { useAction, useGet, useUser } from '../hooks'
import type {
  CarbonFactor,
  UserView,
  GreenFetchResult,
  GreenProductView,
  LoadHistory,
  MarketFetchResult,
  MarketPrice,
  Preview,
  PriceView,
  SizeRow,
  SourceQuality,
  SourceView,
} from '../types'
import { Async, Badge, Card, Notice, Tabs } from '../ui'

type Tab = 'load' | 'quality' | 'prices' | 'carbon' | 'sizes' | 'sources' | 'users'
const TABS: { id: Tab; label: string }[] = [
  { id: 'load', label: '데이터 불러오기' },
  { id: 'quality', label: '데이터 점검' },
  { id: 'prices', label: '단가·시장가·녹색제품' },
  { id: 'carbon', label: '탄소계수' },
  { id: 'sizes', label: '사이즈 코드 연결' },
  { id: 'sources', label: '데이터 출처 설정' },
  { id: 'users', label: '계정' },
]

export default function Admin() {
  const [tab, setTab] = useState<Tab>('load')
  const user = useUser()
  return (
    <>
      <div className="page-head">
        <div>
          <h1>데이터 관리</h1>
          <p className="muted">
            데이터 불러오기, 점검, 단가·사이즈 매핑, 출처 설정, 탄소계수, 계정. 바꾸면 모든 지표가 자동으로 다시 계산됩니다.
          </p>
        </div>
      </div>
      {user.demo && <Notice tone="warn">공개 시연 서버라 이 화면의 저장·불러오기는 막혀 있습니다. 조회만 할 수 있습니다.</Notice>}
      <Tabs tabs={TABS} value={tab} onChange={setTab} />
      {tab === 'load' && <LoadTab goPrices={() => setTab('prices')} />}
      {tab === 'quality' && <QualityTab />}
      {tab === 'prices' && <PricesTab />}
      {tab === 'carbon' && <CarbonTab />}
      {tab === 'sizes' && <SizesTab />}
      {tab === 'sources' && <SourcesTab />}
      {tab === 'users' && <UsersTab />}
    </>
  )
}

const STATUS_TONE = { SUCCESS: 'ok', FAILED: 'bad' } as const

// 적재 --------------------------------------------------------------------------

function LoadTab({ goPrices }: { goPrices: () => void }) {
  const sources = useGet<SourceView[]>('/api/admin/sources')
  const history = useGet<LoadHistory[]>('/api/admin/uploads')
  const { busy, error, run } = useAction()
  const [source, setSource] = useState('')
  const [baseYear, setBaseYear] = useState('')
  const [file, setFile] = useState<File | null>(null)
  const [done, setDone] = useState<LoadHistory | null>(null)

  const after = (h: LoadHistory | undefined) => {
    if (h) setDone(h)
    history.reload()
    sources.reload()
  }

  const upload = (e: FormEvent, code: string) => {
    e.preventDefault()
    if (!file || !code) return
    const form = new FormData()
    form.set('source', code)
    if (baseYear) form.set('baseYear', baseYear)
    form.set('file', file)
    run(async () => after(await api.upload<LoadHistory>('/api/admin/uploads', form)))
  }

  const fetchApi = (code: string) => run(async () => after(await api.post<LoadHistory>(`/api/admin/sources/${code}/fetch`)))

  return (
    <>
      <ExternalApis goPrices={goPrices} />
      <Async state={sources}>
        {(list) => {
          const selected = source || list.find((s) => s.method === 'UPLOAD')?.code || list[0]?.code || ''
          const apiSources = list.filter((s) => s.method === 'API')
          return (
            <div className="grid cols-2">
              <Card title="CSV 업로드" subtitle="같은 파일은 다시 적재하지 않습니다. 출처별로 기준연이 가장 최근인 적재만 집계에 씁니다.">
                <form onSubmit={(e) => upload(e, selected)} style={{ display: 'grid', gap: 12 }}>
                  <label className="field">
                    데이터 원천
                    <select value={selected} onChange={(e) => setSource(e.target.value)}>
                      {list.map((s) => (
                        <option key={s.code} value={s.code}>
                          {s.name} ({s.method === 'UPLOAD' ? '파일 업로드' : 'Open API'})
                        </option>
                      ))}
                    </select>
                  </label>
                  <label className="field">
                    기준연 (비우면 파일 안 최대 날짜의 연도)
                    <input
                      type="number"
                      value={baseYear}
                      onChange={(e) => setBaseYear(e.target.value)}
                      placeholder="예: 2026"
                      style={{ width: 140 }}
                    />
                  </label>
                  <label className="field">
                    CSV 파일
                    <input type="file" accept=".csv,text/csv" onChange={(e) => setFile(e.target.files?.[0] ?? null)} />
                  </label>
                  <div>
                    <button className="primary" disabled={busy || !file}>
                      {busy ? '적재하는 중…' : '업로드·적재'}
                    </button>
                  </div>
                </form>
              </Card>
              <Card
                title="Open API 적재"
                subtitle="적재 방식이 API인 원천을 지금 받아 적재합니다. 마지막 적재와 내용이 같으면 적재하지 않습니다."
              >
                {apiSources.length === 0 ? (
                  <p className="muted">API 방식 원천이 없습니다. '원천 설정'에서 방식을 바꿀 수 있습니다.</p>
                ) : (
                  <div style={{ display: 'grid', gap: 12 }}>
                    {apiSources.map((s) => (
                      <div key={s.code} className="form-row" style={{ justifyContent: 'space-between' }}>
                        <div>
                          <strong>{s.name}</strong>{' '}
                          <Badge tone={s.apiKeyConfigured ? 'ok' : 'bad'}>{s.apiKeyConfigured ? '인증키 설정됨' : '인증키 없음'}</Badge>
                          <div className="muted">
                            {s.lastLoad
                              ? `마지막 적재 ${dateTime(s.lastLoad.at)} · ${s.lastLoad.status === 'SUCCESS' ? `${num(s.lastLoad.rowCount)}행` : '실패'}`
                              : '아직 적재하지 않음'}
                          </div>
                        </div>
                        <button className="secondary" disabled={busy || !s.apiKeyConfigured} onClick={() => fetchApi(s.code)}>
                          지금 적재
                        </button>
                      </div>
                    ))}
                  </div>
                )}
              </Card>
            </div>
          )
        }}
      </Async>

      {error && <Notice tone="bad">{error}</Notice>}
      {done && (
        <Notice tone="ok">
          적재했습니다: {done.source} {done.baseYear}년 · {num(done.rowCount)}행 · 완전 중복 {num(done.duplicateCount)}행 · 품질 플래그{' '}
          {num(done.flaggedCount)}행 {done.active ? '· 집계에 사용 중' : ''}
        </Notice>
      )}

      <Card title="적재 이력">
        <Async state={history}>
          {(rows) => (
            <div className="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>일시</th>
                    <th>원천</th>
                    <th className="num">기준연</th>
                    <th>파일/출처</th>
                    <th className="num">행</th>
                    <th className="num">중복</th>
                    <th className="num">플래그</th>
                    <th>결과</th>
                    <th>적재자</th>
                  </tr>
                </thead>
                <tbody>
                  {rows.map((h) => (
                    <tr key={h.id}>
                      <td className="nowrap">{dateTime(h.loadedAt)}</td>
                      <td>{h.source}</td>
                      <td className="num">{h.baseYear ?? '-'}</td>
                      <td>{h.fileName ?? '-'}</td>
                      <td className="num">{num(h.rowCount)}</td>
                      <td className="num">{num(h.duplicateCount)}</td>
                      <td className="num">{num(h.flaggedCount)}</td>
                      <td>
                        <Badge tone={STATUS_TONE[h.status]}>{h.status === 'SUCCESS' ? '성공' : '실패'}</Badge>{' '}
                        {h.active && <Badge tone="info">집계 사용</Badge>}
                        {h.message && <div className="muted">{h.message}</div>}
                      </td>
                      <td>{h.loadedBy}</td>
                    </tr>
                  ))}
                  {rows.length === 0 && (
                    <tr>
                      <td colSpan={9} className="muted">
                        적재 이력이 없습니다.
                      </td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>
          )}
        </Async>
      </Card>
    </>
  )
}

// 품질 리포트 ---------------------------------------------------------------------

function QualityTab() {
  const state = useGet<SourceQuality[]>('/api/admin/quality')
  return (
    <Async state={state}>
      {(list) =>
        list.length === 0 ? (
          <Notice tone="warn">집계에 쓰는 적재가 없습니다. 먼저 데이터를 적재하세요.</Notice>
        ) : (
          list.map((q) => (
            <Card key={q.source} title={q.name} subtitle={`${q.baseYear ?? '-'}년 기준 · ${q.fileName ?? ''} · ${dateTime(q.loadedAt)}`}>
              <div className="kv" style={{ marginBottom: 10 }}>
                <span>
                  전체 <strong>{num(q.rowCount)}</strong>행
                </span>
                <span>
                  완전 중복 <strong>{num(q.duplicateRows)}</strong>행 ({q.rowCount ? num((q.duplicateRows / q.rowCount) * 100, 1) : 0}%)
                </span>
                <span>
                  품질 플래그 <strong>{num(q.flaggedRows)}</strong>행
                </span>
                {q.sizeCodes > 0 && (
                  <span>
                    사이즈 코드 <strong>{q.sizeCodes}</strong>개 중 매핑 안 된 것 <strong>{q.unmappedSizeCodes}</strong>개
                  </span>
                )}
              </div>
              {q.flags.length === 0 ? (
                <Badge tone="ok">플래그 없음</Badge>
              ) : (
                q.flags.map((f) => (
                  <details key={f.flag} style={{ marginTop: 6 }}>
                    <summary>
                      <Badge tone="warn">{f.label}</Badge> {num(f.rows)}행
                    </summary>
                    <div className="table-wrap">
                      <table>
                        <thead>
                          <tr>
                            <th className="num">줄</th>
                            <th>원본 값 (앞 {f.samples.length}건)</th>
                          </tr>
                        </thead>
                        <tbody>
                          {f.samples.map((s) => (
                            <tr key={s.lineNo}>
                              <td className="num">{s.lineNo}</td>
                              <td style={{ wordBreak: 'break-all' }}>{s.rawValues}</td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </div>
                  </details>
                ))
              )}
            </Card>
          ))
        )
      }
    </Async>
  )
}

// 단가·시장가·녹색제품 --------------------------------------------------------------------

function PricesTab() {
  return (
    <>
      <UnitPrices />
      <MarketPrices />
      <GreenProducts />
    </>
  )
}

function UnitPrices() {
  const state = useGet<PriceView[]>('/api/admin/prices')
  const { busy, error, run } = useAction()
  const [edits, setEdits] = useState<Record<string, string>>({})
  const [saved, setSaved] = useState(false)

  const save = () => {
    const changes = Object.entries(edits)
      .filter(([, v]) => v.trim() !== '')
      .map(([group, v]) => ({ group, price: Number(v) }))
    if (changes.length === 0) return
    run(async () => {
      await api.put('/api/admin/prices', changes)
      setEdits({})
      setSaved(true)
      state.reload()
    })
  }

  return (
    <Card title="단가" subtitle="우선순위: 직접 입력 > 조달청 시장가 > 추정 단가. 입력하면 오늘부터 적용되고 절감액이 다시 계산됩니다.">
      <Async state={state}>
        {(rows) => (
          <>
            <div className="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>품목</th>
                    <th className="num">현재 단가</th>
                    <th>출처</th>
                    <th>근거</th>
                    <th>새 단가(원)</th>
                  </tr>
                </thead>
                <tbody>
                  {rows.map((p) => (
                    <tr key={p.group}>
                      <td>{p.label}</td>
                      <td className="num">{won(p.price)}</td>
                      <td>
                        <Badge tone={PRICE_SOURCE[p.source].tone}>{p.sourceLabel}</Badge>
                      </td>
                      <td className="muted">{p.note}</td>
                      <td>
                        <input
                          type="number"
                          min={1}
                          step={100}
                          value={edits[p.group] ?? ''}
                          onChange={(e) => {
                            setSaved(false)
                            setEdits({ ...edits, [p.group]: e.target.value })
                          }}
                          style={{ width: 120 }}
                          aria-label={`${p.label} 새 단가`}
                        />
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <div className="form-row" style={{ marginTop: 12 }}>
              <button className="primary" disabled={busy || Object.values(edits).every((v) => v.trim() === '')} onClick={save}>
                단가 저장
              </button>
              {saved && <Badge tone="ok">저장했습니다. 지표를 다시 계산했습니다.</Badge>}
            </div>
            {error && <Notice tone="bad">{error}</Notice>}
          </>
        )}
      </Async>
    </Card>
  )
}

function MarketPrices() {
  const state = useGet<MarketPrice[]>('/api/admin/market-prices')
  const { busy, error, run } = useAction()
  const [result, setResult] = useState<MarketFetchResult | null>(null)
  const [file, setFile] = useState<File | null>(null)

  const apply = (r: MarketFetchResult | undefined) => {
    if (r) {
      setResult(r)
      state.reload()
    }
  }
  const upload = () => {
    if (!file) return
    const form = new FormData()
    form.set('file', file)
    run(async () => apply(await api.upload<MarketFetchResult>('/api/admin/market-prices/upload', form)))
  }

  return (
    <Card
      title="조달청 쇼핑몰 시장가"
      subtitle="나라장터 쇼핑몰의 최근 1년 근무복 계약가격을 품목군별 중앙값으로 요약합니다. 표본이 5건 미만이면 단가로 쓰지 않습니다."
      actions={
        <button
          className="primary"
          disabled={busy}
          onClick={() => run(async () => apply(await api.post<MarketFetchResult>('/api/admin/market-prices/fetch')))}
        >
          {busy ? '가져오는 중…' : 'API에서 가져오기'}
        </button>
      }
    >
      {error && <Notice tone="bad">{error}</Notice>}
      {result && (
        <Notice tone="ok">
          {num(result.itemCount)}개 상품을 받았습니다. 품목군을 정하지 못한 상품 {num(result.unclassifiedCount)}개(상·하의 묶음 등)는
          뺐습니다.
        </Notice>
      )}
      <Async state={state}>
        {(rows) =>
          rows.length === 0 ? (
            <p className="muted">아직 가져오지 않았습니다.</p>
          ) : (
            <div className="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>품목</th>
                    <th className="num">표본</th>
                    <th className="num">중앙값</th>
                    <th className="num">하위~상위 25%</th>
                    <th className="num">환경표지</th>
                    <th>단가로 사용</th>
                    <th>가져온 때</th>
                  </tr>
                </thead>
                <tbody>
                  {rows.map((m) => (
                    <tr key={m.group}>
                      <td>{m.label}</td>
                      <td className="num">{num(m.sampleCount)}건</td>
                      <td className="num">{won(m.medianPrice)}</td>
                      <td className="num">
                        {won(m.q1Price)} ~ {won(m.q3Price)}
                      </td>
                      <td className="num">{m.ecoCount ? `${m.ecoCount}건 · ${won(m.ecoMedianPrice)}` : '-'}</td>
                      <td>{m.usable ? <Badge tone="ok">사용</Badge> : <Badge tone="warn">표본 부족</Badge>}</td>
                      <td className="muted">
                        {dateTime(m.fetchedAt)} ({m.method === 'API' ? 'API' : '파일'})
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )
        }
      </Async>
      <details style={{ marginTop: 12 }}>
        <summary>망분리 환경: CSV로 올리기</summary>
        <div className="form-row" style={{ marginTop: 8 }}>
          <input type="file" accept=".csv,text/csv" onChange={(e) => setFile(e.target.files?.[0] ?? null)} />
          <button className="secondary" disabled={busy || !file} onClick={upload}>
            업로드
          </button>
        </div>
        <p className="muted">필요한 컬럼: 물품규격명, 계약가격금액 (선택: 물품단위, 의무구매대상인증명, 쇼핑계약번호, 쇼핑계약순번)</p>
      </details>
    </Card>
  )
}

function GreenProducts() {
  const state = useGet<GreenProductView[]>('/api/admin/green-products')
  const { busy, error, run } = useAction()
  const [result, setResult] = useState<GreenFetchResult | null>(null)
  const [file, setFile] = useState<File | null>(null)

  const apply = (r: GreenFetchResult | undefined) => {
    if (r) {
      setResult(r)
      state.reload()
    }
  }
  const upload = () => {
    if (!file) return
    const form = new FormData()
    form.set('file', file)
    run(async () => apply(await api.upload<GreenFetchResult>('/api/admin/green-products/upload', form)))
  }

  return (
    <Card
      title="녹색제품(환경표지 인증)"
      subtitle="한국환경산업기술원 녹색제품 정보조회. 인증이 유효한 근무복 수와 기업을 발주 추천의 녹색구매 비교에 씁니다."
      actions={
        <button
          className="primary"
          disabled={busy}
          onClick={() => run(async () => apply(await api.post<GreenFetchResult>('/api/admin/green-products/fetch')))}
        >
          {busy ? '가져오는 중…' : 'API에서 가져오기'}
        </button>
      }
    >
      {error && <Notice tone="bad">{error}</Notice>}
      {result && (
        <Notice tone="ok">
          {num(result.productCount)}개 제품을 받았습니다. 인증이 유효한 제품 {num(result.validCount)}개.
        </Notice>
      )}
      <Async state={state}>
        {(rows) =>
          rows.length === 0 ? (
            <p className="muted">아직 가져오지 않았습니다.</p>
          ) : (
            <div className="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>제품명</th>
                    <th>기업</th>
                    <th>인증 종료</th>
                    <th>상태</th>
                  </tr>
                </thead>
                <tbody>
                  {rows.slice(0, 30).map((g) => (
                    <tr key={g.product.productId}>
                      <td>{g.product.name}</td>
                      <td>{g.product.company ?? '-'}</td>
                      <td>{g.product.certTo ?? '-'}</td>
                      <td>{g.valid ? <Badge tone="ok">유효</Badge> : <Badge tone="warn">만료·취소</Badge>}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
              {rows.length > 30 && <p className="muted">앞 30개만 보여줍니다. 전체 {num(rows.length)}개.</p>}
            </div>
          )
        }
      </Async>
      <details style={{ marginTop: 12 }}>
        <summary>망분리 환경: CSV로 올리기</summary>
        <div className="form-row" style={{ marginTop: 8 }}>
          <input type="file" accept=".csv,text/csv" onChange={(e) => setFile(e.target.files?.[0] ?? null)} />
          <button className="secondary" disabled={busy || !file} onClick={upload}>
            업로드
          </button>
        </div>
        <p className="muted">
          필요한 컬럼: 상품명 (선택: 상품ID, 소분류명, 모델명, 환경표지인증번호, 인증시작일, 인증종료일, 인증상태, 인증기업명, G2B식별번호)
        </p>
      </details>
    </Card>
  )
}

// 사이즈 매핑 ---------------------------------------------------------------------

function SizesTab() {
  const state = useGet<SizeRow[]>('/api/admin/size-mappings')
  const { busy, error, run } = useAction()
  const [edits, setEdits] = useState<Record<string, string>>({})
  const [saved, setSaved] = useState(false)
  const key = (r: SizeRow) => `${r.source}|${r.rawSize}`

  const save = (rows: SizeRow[]) => {
    const changes = rows
      .filter((r) => key(r) in edits && (edits[key(r)] ?? '') !== (r.standardSize ?? ''))
      .map((r) => ({ source: r.source, rawSize: r.rawSize, standardSize: edits[key(r)] }))
    if (changes.length === 0) return
    run(async () => {
      await api.put('/api/admin/size-mappings', changes)
      setEdits({})
      setSaved(true)
      state.reload()
    })
  }

  return (
    <Card
      title="사이즈 매핑"
      subtitle="두 파일의 사이즈 체계(주문현황 80~125, 주문현황정보 10~95)를 표준 사이즈로 연결합니다. 뜻을 확인하기 전에는 비워 두면 '코드 (출처)'로 따로 집계합니다."
    >
      <Async state={state}>
        {(rows) => (
          <>
            <Notice tone="info">
              같은 표준 사이즈를 입력한 코드는 하나로 합산됩니다. 비우면 매핑을 지웁니다. 저장하면 불일치율·추천이 다시 계산됩니다.
            </Notice>
            <div className="table-wrap" style={{ marginTop: 12 }}>
              <table>
                <thead>
                  <tr>
                    <th>출처</th>
                    <th>원본 사이즈</th>
                    <th className="num">행 수</th>
                    <th>표준 사이즈</th>
                  </tr>
                </thead>
                <tbody>
                  {rows.map((r) => (
                    <tr key={key(r)}>
                      <td>{r.sourceName}</td>
                      <td>{r.rawSize}</td>
                      <td className="num">{num(r.rowCount)}</td>
                      <td>
                        <input
                          type="text"
                          value={edits[key(r)] ?? r.standardSize ?? ''}
                          onChange={(e) => {
                            setSaved(false)
                            setEdits({ ...edits, [key(r)]: e.target.value })
                          }}
                          style={{ width: 110 }}
                          aria-label={`${r.sourceName} ${r.rawSize} 표준 사이즈`}
                        />
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <div className="form-row" style={{ marginTop: 12 }}>
              <button className="primary" disabled={busy || Object.keys(edits).length === 0} onClick={() => save(rows)}>
                저장
              </button>
              {saved && <Badge tone="ok">저장했습니다. 지표를 다시 계산했습니다.</Badge>}
            </div>
            {error && <Notice tone="bad">{error}</Notice>}
          </>
        )}
      </Async>
    </Card>
  )
}

// 원천 설정 -----------------------------------------------------------------------

function SourcesTab() {
  const state = useGet<SourceView[]>('/api/admin/sources')
  return (
    <Async state={state}>
      {(list) => (
        <>
          <Notice tone="info">
            적재 방식·API 주소·인코딩·컬럼 이름을 코드 수정 없이 바꿉니다. CSV로 받던 데이터가 Open API로 바뀌면 방식과 주소만 바꾸고
            '미리보기'로 컬럼을 확인하세요. 인증키는 서버 환경변수에만 두며 여기에는 설정 여부만 보입니다.
          </Notice>
          {list.map((s) => (
            <SourceCard key={s.code} source={s} onSaved={state.reload} />
          ))}
        </>
      )}
    </Async>
  )
}

function SourceCard({ source, onSaved }: { source: SourceView; onSaved: () => void }) {
  const [method, setMethod] = useState(source.method)
  const [url, setUrl] = useState(source.url ?? '')
  const [charset, setCharset] = useState(source.charset)
  const [perPage, setPerPage] = useState(String(source.perPage))
  const [columns, setColumns] = useState<Record<string, string>>(source.columns)
  const [file, setFile] = useState<File | null>(null)
  const [preview, setPreview] = useState<Preview | null>(null)
  const [saved, setSaved] = useState(false)
  const { busy, error, run } = useAction()

  const save = () =>
    run(async () => {
      await api.put(`/api/admin/sources/${source.code}`, { method, url: url || null, perPage: Number(perPage), charset, columns })
      setSaved(true)
      onSaved()
    })

  const runPreview = () =>
    run(async () => {
      const form = new FormData()
      form.set('method', method)
      if (url) form.set('url', url)
      form.set('charset', charset)
      if (file) form.set('file', file)
      setPreview(await api.upload<Preview>(`/api/admin/sources/${source.code}/preview`, form))
    })

  return (
    <Card
      title={
        <span style={{ display: 'inline-flex', gap: 10, alignItems: 'center' }}>
          {source.name} <span className="muted">{source.code}</span>
        </span>
      }
      subtitle={
        source.lastLoad
          ? `마지막 적재 ${dateTime(source.lastLoad.at)} · ${source.lastLoad.status === 'SUCCESS' ? `${num(source.lastLoad.rowCount)}행` : '실패'}${source.activeBaseYear ? ` · 집계 기준연 ${source.activeBaseYear}` : ''}`
          : '아직 적재하지 않음'
      }
      actions={
        method === 'API' ? (
          <Badge tone={source.apiKeyConfigured ? 'ok' : 'bad'}>{source.apiKeyConfigured ? '인증키 설정됨' : '인증키 없음'}</Badge>
        ) : undefined
      }
    >
      <div className="form-row">
        <label className="field">
          적재 방식
          <select
            value={method}
            onChange={(e) => {
              setSaved(false)
              setMethod(e.target.value as typeof method)
            }}
          >
            <option value="UPLOAD">파일 업로드</option>
            <option value="API">Open API</option>
          </select>
        </label>
        <label className="field" style={{ flex: 1, minWidth: 260 }}>
          API 주소 (https://)
          <input
            type="text"
            value={url}
            onChange={(e) => {
              setSaved(false)
              setUrl(e.target.value)
            }}
            placeholder={method === 'API' ? 'https://api.odcloud.kr/api/...' : '(파일 업로드는 필요 없음)'}
          />
        </label>
        <label className="field">
          인코딩
          <input
            type="text"
            value={charset}
            onChange={(e) => {
              setSaved(false)
              setCharset(e.target.value)
            }}
            style={{ width: 90 }}
          />
        </label>
        <label className="field">
          한 번에 받을 행
          <input
            type="number"
            value={perPage}
            onChange={(e) => {
              setSaved(false)
              setPerPage(e.target.value)
            }}
            style={{ width: 100 }}
          />
        </label>
      </div>

      <h3 style={{ margin: '16px 0 8px' }}>컬럼 매핑 (코드가 읽는 항목 → 원본 컬럼 이름)</h3>
      <div className="grid cols-3">
        {source.fields.map((f) => (
          <label className="field" key={f.name}>
            <span>
              {f.label} {f.required && <Badge tone="warn">필수</Badge>}
            </span>
            <input
              type="text"
              value={columns[f.name] ?? ''}
              onChange={(e) => {
                setSaved(false)
                setColumns({ ...columns, [f.name]: e.target.value })
              }}
            />
          </label>
        ))}
      </div>

      <div className="form-row" style={{ marginTop: 16 }}>
        <button className="primary" disabled={busy} onClick={save}>
          저장
        </button>
        <button className="secondary" disabled={busy} onClick={runPreview}>
          미리보기
        </button>
        {method === 'UPLOAD' && (
          <input type="file" accept=".csv,text/csv" onChange={(e) => setFile(e.target.files?.[0] ?? null)} aria-label="미리보기 파일" />
        )}
        {saved && <Badge tone="ok">저장했습니다</Badge>}
      </div>
      {error && (
        <div style={{ marginTop: 10 }}>
          <Notice tone="bad">{error}</Notice>
        </div>
      )}
      {preview && (
        <div style={{ marginTop: 14, display: 'grid', gap: 10 }}>
          <div className="kv">
            <span>
              읽은 행 <strong>{num(preview.rowCount)}</strong>
            </span>
            <span>
              컬럼 <strong>{preview.columns.length}</strong>개
            </span>
          </div>
          {preview.missingColumns.length > 0 && (
            <Notice tone="bad">매핑했는데 원천에 없는 컬럼: {preview.missingColumns.join(', ')}</Notice>
          )}
          {preview.unmappedFields.length > 0 && (
            <Notice tone="warn">매핑이 비어 있는 필수 항목: {preview.unmappedFields.join(', ')}</Notice>
          )}
          {preview.missingColumns.length === 0 && preview.unmappedFields.length === 0 && (
            <Notice tone="ok">컬럼 매핑이 모두 맞습니다.</Notice>
          )}
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  {preview.columns.map((c) => (
                    <th key={c}>{c}</th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {preview.sampleRows.map((row, i) => (
                  <tr key={i}>
                    {preview.columns.map((c) => (
                      <td key={c}>{row[c]}</td>
                    ))}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </Card>
  )
}

// 탄소계수 -----------------------------------------------------------------------

function CarbonTab() {
  const factors = useGet<CarbonFactor[]>('/api/admin/carbon-factors')
  const { busy, error, run } = useAction()
  const [edits, setEdits] = useState<Record<string, { kg?: string; source?: string }>>({})
  const [saved, setSaved] = useState(false)

  const save = () =>
    run(async () => {
      const changes = (factors.data ?? [])
        .filter((f) => edits[f.group])
        .map((f) => ({
          group: f.group,
          kgCo2e: Number(edits[f.group].kg ?? f.kgCo2e),
          source: edits[f.group].source ?? f.source,
        }))
      await api.put('/api/admin/carbon-factors', changes)
      setEdits({})
      setSaved(true)
      factors.reload()
    })

  return (
    <Card
      title="품목별 탄소계수"
      subtitle="벌당 kgCO₂e. 탄소 감축량 = 절감 수량 × 계수. 해외 일반 값이라 추정치이므로, 기관이 쓰는 값이 있으면 출처와 함께 바꿔 넣으세요."
    >
      <Async state={factors}>
        {(list) => (
          <>
            <div className="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>품목</th>
                    <th className="num">kgCO₂e/벌</th>
                    <th>출처</th>
                  </tr>
                </thead>
                <tbody>
                  {list.map((f) => (
                    <tr key={f.group}>
                      <td>{f.label}</td>
                      <td className="num">
                        <input
                          type="number"
                          min={0}
                          step={0.1}
                          style={{ width: 90 }}
                          aria-label={`${f.label} 탄소계수`}
                          value={edits[f.group]?.kg ?? String(f.kgCo2e)}
                          onChange={(e) => setEdits({ ...edits, [f.group]: { ...edits[f.group], kg: e.target.value } })}
                        />
                      </td>
                      <td>
                        <input
                          type="text"
                          style={{ width: '100%', minWidth: 260 }}
                          aria-label={`${f.label} 출처`}
                          value={edits[f.group]?.source ?? f.source}
                          onChange={(e) => setEdits({ ...edits, [f.group]: { ...edits[f.group], source: e.target.value } })}
                        />
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <div className="form-row" style={{ marginTop: 10 }}>
              <button className="primary" disabled={busy || Object.keys(edits).length === 0} onClick={save}>
                {busy ? '저장하는 중…' : '저장'}
              </button>
              {saved && <Badge tone="ok">저장했습니다. 탄소 감축량이 다시 계산됩니다.</Badge>}
            </div>
            {error && <Notice tone="bad">{error}</Notice>}
          </>
        )}
      </Async>
    </Card>
  )
}

// 계정 ---------------------------------------------------------------------------

function UsersTab() {
  const users = useGet<UserView[]>('/api/admin/users')
  const user = useUser()
  const { busy, error, run } = useAction()
  const [form, setForm] = useState({ username: '', password: '', role: 'USER' })
  const [resetDone, setResetDone] = useState<string | null>(null)

  const create = (e: FormEvent) => {
    e.preventDefault()
    run(async () => {
      await api.post('/api/admin/users', form)
      setForm({ username: '', password: '', role: 'USER' })
      users.reload()
    })
  }
  const update = (username: string, body: object) =>
    run(async () => {
      await api.put(`/api/admin/users/${encodeURIComponent(username)}`, body)
      users.reload()
    })
  const resetPassword = (username: string) => {
    const password = window.prompt(`${username}의 새 비밀번호 (8자 이상)`)
    if (password) update(username, { password })
  }
  const resetDemo = () => {
    if (!window.confirm('입력된 단가·재고·발주안을 지우고 처음 상태로 되돌립니다. 계속할까요?')) return
    run(async () => {
      const r = await api.post<{ prices: number; stock: number; plans: number }>('/api/admin/demo/reset', {})
      setResetDone(`단가 ${r.prices}건, 재고 ${r.stock}건, 발주안 ${r.plans}건을 지웠습니다.`)
    })
  }

  return (
    <>
      <Card
        title="계정"
        subtitle="사용자(실무 담당자·부서장): 조회·입력·변경 기록 / 관리자: 전체. 계정은 지우지 않고 사용 중지로 관리합니다."
      >
        <Async state={users}>
          {(list) => (
            <div className="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>아이디</th>
                    <th>역할</th>
                    <th>상태</th>
                    <th>만든 날</th>
                    <th />
                  </tr>
                </thead>
                <tbody>
                  {list.map((u) => (
                    <tr key={u.username}>
                      <td>{u.username}</td>
                      <td>
                        <select
                          aria-label={`${u.username} 역할`}
                          value={u.role}
                          disabled={busy || u.username === user.username}
                          onChange={(e) => update(u.username, { role: e.target.value })}
                        >
                          {Object.entries(ROLE_LABEL).map(([k, v]) => (
                            <option key={k} value={k}>
                              {v}
                            </option>
                          ))}
                        </select>
                      </td>
                      <td>{u.enabled ? <Badge tone="ok">사용 중</Badge> : <Badge tone="bad">중지</Badge>}</td>
                      <td className="nowrap">{dateTime(u.createdAt)}</td>
                      <td className="nowrap">
                        <button className="link" disabled={busy} onClick={() => resetPassword(u.username)}>
                          비밀번호 재설정
                        </button>{' '}
                        {u.username !== user.username && (
                          <button className="link" disabled={busy} onClick={() => update(u.username, { enabled: !u.enabled })}>
                            {u.enabled ? '사용 중지' : '사용 재개'}
                          </button>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </Async>
        <form className="form-row" style={{ marginTop: 14 }} onSubmit={create}>
          <label className="field">
            새 아이디
            <input value={form.username} onChange={(e) => setForm({ ...form, username: e.target.value })} required minLength={3} />
          </label>
          <label className="field">
            비밀번호 (8자 이상)
            <input
              type="password"
              value={form.password}
              onChange={(e) => setForm({ ...form, password: e.target.value })}
              required
              minLength={8}
              autoComplete="new-password"
            />
          </label>
          <label className="field">
            역할
            <select value={form.role} onChange={(e) => setForm({ ...form, role: e.target.value })}>
              {Object.entries(ROLE_LABEL).map(([k, v]) => (
                <option key={k} value={k}>
                  {v}
                </option>
              ))}
            </select>
          </label>
          <button className="primary" disabled={busy}>
            계정 추가
          </button>
        </form>
        {error && <Notice tone="bad">{error}</Notice>}
      </Card>
      <Card
        title="시연 데이터 초기화"
        subtitle="입력된 단가·재고·발주안을 지우고 처음 상태로 되돌립니다. 시연 서버는 매일 새벽 4시에 자동으로 실행합니다."
      >
        <button className="secondary" disabled={busy} onClick={resetDemo}>
          지금 초기화
        </button>
        {resetDone && <Badge tone="ok">{resetDone}</Badge>}
      </Card>
    </>
  )
}

/** 가격·녹색제품 Open API 2종. 근무복 주문 출처와 달리 주소·키를 서버 설정으로 두고, 가져오기는 '단가·시장가·녹색제품' 탭에서 한다. */
function ExternalApis({ goPrices }: { goPrices: () => void }) {
  const market = useGet<MarketPrice[]>('/api/admin/market-prices')
  const green = useGet<GreenProductView[]>('/api/admin/green-products')
  const marketAt = market.data
    ?.map((m) => m.fetchedAt)
    .sort()
    .pop()
  const greenCount = green.data?.length ?? 0
  const rows = [
    {
      name: '조달청_나라장터쇼핑몰 품목정보 서비스',
      no: '15129471',
      url: 'https://apis.data.go.kr/1230000/at/ShoppingMallPrdctInfoService',
      use: "품목군별 시장가(단가 출처 '조달청 시장가')와 환경표지 제품 수",
      state: marketAt ? <Badge tone="ok">가져옴 {dateTime(marketAt)}</Badge> : <Badge tone="warn">아직 안 가져옴</Badge>,
    },
    {
      name: '한국환경산업기술원_녹색제품 정보조회 서비스 GW',
      no: '15158379',
      url: 'https://apis.data.go.kr/B552518/EnvironmentSignalRecognitionInformationInquiryService',
      use: '환경표지 인증 제품·기업·인증기간(녹색구매 비교)',
      state:
        greenCount > 0 ? (
          <Badge tone="ok">{num(greenCount)}개 제품</Badge>
        ) : (
          <Badge tone="bad">가져온 데이터 없음 (기관 서버 오류 04)</Badge>
        ),
    },
  ]
  return (
    <Card
      title="가격·녹색제품 Open API"
      subtitle="인증키(DATA_GO_KR_SERVICE_KEY)와 주소는 서버 설정에 있고 화면에서 바꾸지 않습니다. 가져오기는 '단가·시장가·녹색제품' 탭에서 합니다."
      actions={
        <button className="secondary" onClick={goPrices}>
          가져오기 화면으로
        </button>
      }
    >
      <div className="table-wrap">
        <table>
          <thead>
            <tr>
              <th>API</th>
              <th>쓰임</th>
              <th>주소</th>
              <th>상태</th>
            </tr>
          </thead>
          <tbody>
            {rows.map((r) => (
              <tr key={r.no}>
                <td>
                  {r.name} <span className="muted">({r.no})</span>
                </td>
                <td>{r.use}</td>
                <td style={{ wordBreak: 'break-all' }}>{r.url}</td>
                <td>{r.state}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </Card>
  )
}
