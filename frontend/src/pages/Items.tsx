import { api } from '../api'
import { dateTime } from '../format'
import { useAction, useBasis, useGet } from '../hooks'
import type { EsgItem, LoadMethod, MetricValues } from '../types'
import { Async, Badge, Card, Notice, Switch } from '../ui'

const CATEGORY = { E: '환경(E)', S: '사회(S)', G: '지배구조(G)' } as const
const METHOD: Record<LoadMethod, string> = { UPLOAD: '파일 업로드', API: 'Open API', BATCH: '배치' }

export default function Items() {
  const state = useGet<EsgItem[]>('/api/esg/items')
  return (
    <>
      <div className="page-head">
        <div>
          <h1>ESG 항목 관리</h1>
          <p className="muted">항목은 모듈 단위로 추가됩니다. 지금은 근무복 하나이고, 새 모듈은 코드에 추가하면 이 목록에 나타납니다.</p>
        </div>
      </div>
      <Async state={state}>{(items) => items.map((item) => <ItemCard key={item.code} item={item} onChanged={state.reload} />)}</Async>
    </>
  )
}

function ItemCard({ item, onChanged }: { item: EsgItem; onChanged: () => void }) {
  const { busy, error, run } = useAction()
  const metrics = useGet<MetricValues[]>(`/api/esg/items/${item.code}/metrics`)

  const change = (body: { enabled?: boolean; loadMethod?: LoadMethod }, confirmText?: string) => {
    if (confirmText && !window.confirm(confirmText)) return
    run(async () => {
      await api.put(`/api/esg/items/${item.code}`, body)
      onChanged()
    })
  }

  return (
    <Card
      title={
        <span style={{ display: 'inline-flex', gap: 10, alignItems: 'center' }}>
          {item.name} <Badge tone="info">{CATEGORY[item.category]}</Badge> <span className="muted">{item.code}</span>
        </span>
      }
      actions={
        <Switch
          checked={item.enabled}
          disabled={busy}
          onChange={(v) => change({ enabled: v })}
          label={item.enabled ? '사용 중' : '사용 안 함'}
        />
      }
    >
      {error && <Notice tone="bad">{error}</Notice>}
      {!item.enabled && <Notice tone="warn">사용하지 않는 항목이라 불러오기가 막혀 있습니다.</Notice>}

      <h3 style={{ margin: '14px 0 8px' }}>데이터 출처와 불러오는 방식</h3>
      <div className="table-wrap">
        <table>
          <thead>
            <tr>
              <th>출처</th>
              <th>코드</th>
              <th>불러오는 방식</th>
            </tr>
          </thead>
          <tbody>
            {item.sources.map((s) => (
              <tr key={s.code}>
                <td>{s.name}</td>
                <td className="muted">{s.code}</td>
                <td>
                  <Badge tone={s.method === 'API' ? 'info' : 'neutral'}>{METHOD[s.method]}</Badge>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <div className="form-row" style={{ marginTop: 12 }}>
        <button
          className="secondary"
          disabled={busy}
          onClick={() =>
            change({ loadMethod: 'UPLOAD' }, '모든 출처의 불러오는 방식을 파일 업로드로 바꿉니다. (API 주소·컬럼 매핑은 그대로 남습니다)')
          }
        >
          모든 원천을 파일 업로드로 (망분리)
        </button>
        <button
          className="secondary"
          disabled={busy}
          onClick={() =>
            change({ loadMethod: 'API' }, 'API 주소가 있는 원천을 Open API 불러오기로 바꿉니다. (파일로만 제공되는 원천은 그대로)')
          }
        >
          API 가능한 원천을 API로
        </button>
      </div>
      <p className="muted" style={{ marginTop: 6 }}>
        기관 내부망에서 외부 API를 못 부를 때 한 번에 업로드 방식으로 바꾸고, 포털에서 받은 CSV를 올립니다. 출처별 세부 설정은 '데이터 관리
        → 원천 설정'에서 바꿉니다.
      </p>

      <h3 style={{ margin: '18px 0 8px' }}>지표</h3>
      <Async state={metrics}>
        {(ms) => (
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>지표</th>
                  <th>단위</th>
                  <th>값 개수</th>
                  <th>마지막 계산</th>
                </tr>
              </thead>
              <tbody>
                {ms.map((m) => (
                  <tr key={m.code}>
                    <td>
                      {m.name} <span className="muted">{m.code}</span>
                    </td>
                    <td>{m.unit}</td>
                    <td className="num">{m.values.length}</td>
                    <td>{dateTime(m.calculatedAt)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Async>
      <BasisNote />
    </Card>
  )
}

function BasisNote() {
  const { basis } = useBasis()
  return (
    <p className="muted" style={{ marginTop: 6 }}>
      값 개수는 '{basis === 'dedup' ? '중복 제거' : '원자료'}' 기준입니다. 적재·단가·사이즈 매핑이 바뀌면 두 기준 모두 자동으로 다시
      계산됩니다.
    </p>
  )
}
