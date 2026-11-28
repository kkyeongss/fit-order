import { useState } from 'react'
import { auditLabel, dateTime } from '../format'
import { useGet } from '../hooks'
import type { AuditRow } from '../types'
import { Async, Badge, Card } from '../ui'

interface Filters {
  actions: string[]
  usernames: string[]
}

/** 변경 기록: 누가 언제 무엇을 바꿨는지(단가, 불러오기, 재고, 계정 등). 부서장·관리자만 본다. 기록은 서버가 남기며 여기서는 고칠 수 없다. */
export default function Audit() {
  const [action, setAction] = useState('')
  const [username, setUsername] = useState('')
  const [limit, setLimit] = useState(200)
  const filters = useGet<Filters>('/api/audit/filters')
  const query = new URLSearchParams({ limit: String(limit) })
  if (action) query.set('action', action)
  if (username) query.set('username', username)
  const rows = useGet<AuditRow[]>(`/api/audit?${query.toString()}`)

  return (
    <>
      <div className="page-head">
        <div>
          <h1>변경 기록</h1>
          <p className="muted">
            누가 언제 무엇을 바꿨는지 보여줍니다(단가·재고·발주안·데이터 불러오기·계정 등). 기록은 서버가 자동으로 남기며 이 화면에서
            고치거나 지울 수 없습니다.
          </p>
        </div>
      </div>
      <Card>
        <div className="form-row">
          <label className="field">
            동작
            <select value={action} onChange={(e) => setAction(e.target.value)}>
              <option value="">전체</option>
              {(filters.data?.actions ?? []).map((a) => (
                <option key={a} value={a}>
                  {auditLabel(a)}
                </option>
              ))}
            </select>
          </label>
          <label className="field">
            사용자
            <select value={username} onChange={(e) => setUsername(e.target.value)}>
              <option value="">전체</option>
              {(filters.data?.usernames ?? []).map((u) => (
                <option key={u} value={u}>
                  {u}
                </option>
              ))}
            </select>
          </label>
          <label className="field">
            개수
            <select value={limit} onChange={(e) => setLimit(Number(e.target.value))}>
              {[50, 200, 500, 1000].map((n) => (
                <option key={n} value={n}>
                  최근 {n}건
                </option>
              ))}
            </select>
          </label>
          <button className="secondary" onClick={rows.reload}>
            새로고침
          </button>
        </div>
      </Card>
      <Card>
        <Async state={rows}>
          {(list) => (
            <div className="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>일시</th>
                    <th>사용자</th>
                    <th>동작</th>
                    <th>대상</th>
                    <th>내용</th>
                  </tr>
                </thead>
                <tbody>
                  {list.map((r) => (
                    <tr key={r.id}>
                      <td className="nowrap">{dateTime(r.createdAt)}</td>
                      <td>{r.username}</td>
                      <td>
                        <Badge tone="info">{auditLabel(r.action)}</Badge>
                      </td>
                      <td>{r.target}</td>
                      <td style={{ wordBreak: 'break-all' }}>{r.detail}</td>
                    </tr>
                  ))}
                  {list.length === 0 && (
                    <tr>
                      <td colSpan={5} className="muted">
                        기록이 없습니다.
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
