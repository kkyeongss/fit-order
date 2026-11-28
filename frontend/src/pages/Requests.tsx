import { useEffect, useMemo, useState } from 'react'
import { api } from '../api'
import { copyText, num, signedPct } from '../format'
import type { Recommendation } from '../types'
import { Badge, Card, Notice, Stat, Tabs } from '../ui'

type Org = 'A' | 'B' | 'C'
type Priority = '상' | '중' | '하'

interface RequestItem {
  id: string
  org: Org
  priority: Priority
  title: string
  /** 우리가 데이터에서 확인한 것(근거 수치) */
  found: string[]
  /** 서비스·업무에 미치는 영향 */
  impact: string
  /** 기관에 보낼 요청 문구 */
  ask: string
  /** 우리 쪽에서 임시로 한 대응 */
  ours: string
  attachment?: 'duplicates'
}

const ORGS: Record<Org, { name: string; data: string; contact: string }> = {
  A: {
    name: '한국체육산업개발(주)',
    data: '근무복 주문현황 · 근무복 주문현황정보 · 정원현황',
    contact: '디지털혁신팀 02-2180-3504',
  },
  B: { name: '조달청', data: '나라장터쇼핑몰 품목정보 서비스', contact: '조달데이터관리팀 042-724-7685' },
  C: { name: '한국환경산업기술원', data: '녹색제품 정보조회 서비스 GW', contact: '디지털전략실 02-2284-1167' },
}

const ITEMS: RequestItem[] = [
  {
    id: 'A1',
    org: 'A',
    priority: '상',
    title: '완전히 같은 행이 반복됩니다 — 원인 확인과 주문 식별자 추가',
    found: [
      '근무복 주문현황 1,332행 중 327행(24.5%), 근무복 주문현황정보 1,535행 중 331행(21.6%)이 날짜·품목·사이즈·수량까지 모든 칸이 같은 행입니다.',
      '2024년 이후에 몰려 있어 2025년 주문 수량의 50%가 중복 표시 행입니다.',
      '주문번호나 주문건을 구분할 컬럼이 없어 실제 반복 주문인지 데이터만으로 알 수 없습니다.',
    ],
    impact:
      '같은 해 수량이 중복 제거 기준(2025년 1,323벌)과 원자료 기준(2,651벌)으로 두 배 차이가 나서, 추천 발주 수량도 그만큼 달라집니다.',
    ask:
      '근무복 주문현황·주문현황정보 파일에 날짜·품목·사이즈·수량이 모두 같은 행이 반복해서 들어 있습니다(첨부 목록 참고). ' +
      '같은 날 같은 사이즈를 같은 수량으로 여러 번 주문한 것이 맞는지, 입력·내보내기 과정에서 겹친 것인지 확인해 주세요. ' +
      '앞으로는 주문번호(또는 주문건 식별자) 컬럼을 추가해 실제 주문과 중복을 구분할 수 있게 해 주세요. 중복이 확인되면 정정한 파일로 교체해 주세요.',
    ours: "행을 지우지 않고 표시만 하며, 모든 화면에서 '중복 제거'(기본)와 '원자료'를 바로 비교할 수 있게 했습니다. 추천 화면에는 두 기준의 수량 차이를 함께 보여줍니다.",
    attachment: 'duplicates',
  },
  {
    id: 'A2',
    org: 'A',
    priority: '상',
    title: '사이즈 코드표와 두 파일의 사이즈 대응표가 필요합니다',
    found: [
      '근무복 주문현황은 사이즈 80~125, 근무복 주문현황정보는 10~95로 체계가 다르고, 점퍼가 두 파일에 나뉘어 있습니다.',
      '주문현황정보의 사이즈 91(24행)·95(1행)는 뜻을 알 수 없고, 10~95가 호수인지 치수인지 설명이 없습니다.',
    ],
    impact:
      '점퍼는 전체 발주 금액의 절반 가까이를 차지하는데, 추천 사이즈(예: 50, 60)가 몇 호인지 몰라 주문서에 쓸 수 없고 두 파일의 사이즈를 합쳐 분석할 수도 없습니다.',
    ask:
      '근무복 사이즈 코드표(코드별 치수와 S/M/L 등 표준 사이즈 대응), 두 파일(주문현황 80~125, 주문현황정보 10~95)의 사이즈 대응표, ' +
      '코드 91·95의 의미를 알려 주세요. 가능하면 사이즈 컬럼과 함께 표준 사이즈명 컬럼을 제공해 주세요.',
    ours: "사이즈를 '코드 (출처)'로 구분해 보여주고, 뜻을 모르는 코드에는 '뜻 미확인' 표시와 경고를 붙였습니다. 대응표를 받으면 관리자 화면의 사이즈 코드 연결에서 입력하면 바로 합산됩니다.",
  },
  {
    id: 'A3',
    org: 'A',
    priority: '상',
    title: '주문 부서·직군 정보가 없어 정원현황과 연결할 수 없습니다',
    found: ['주문 데이터에는 부서·직군·직급 컬럼이 없습니다.', '성별코드 컬럼은 전부 같은 값(10)이라 성별 분석이 불가능합니다.'],
    impact: '부서·직군별 지급량과 1인당 지급량을 볼 수 없고, 전체 정원 기준으로만 계산합니다.',
    ask: '주문 행마다 주문 부서(또는 직군) 코드를 추가해 주세요. 성별코드는 실제로 구분되는 값으로 채워 주세요(현재는 모두 10).',
    ours: '정원 전체를 기준으로 인원을 보정하고, 발주 추천에서 대상 직군을 골라 인원을 직접 정할 수 있게 했습니다.',
  },
  {
    id: 'A4',
    org: 'A',
    priority: '상',
    title: '주문 취소·입고·재고·폐기 데이터가 없습니다',
    found: ["주문현황의 주문상태가 '주문' 하나뿐이고, 취소·반품·실제 입고·연말 재고·폐기 수량은 개방되지 않았습니다."],
    impact: '주문량을 곧 필요량으로 가정할 수밖에 없어, 실제로 남은 재고나 버려진 양으로 과잉 발주 효과를 확인할 수 없습니다.',
    ask: '주문 취소·반품 수량, 실제 입고 수량, 연말 기준 재고와 폐기 수량을 연 1회라도 개방해 주세요.',
    ours: '현재 재고를 담당자가 직접 입력해 발주 수량(필요 수량 − 재고)에 반영하게 했습니다.',
  },
  {
    id: 'A5',
    org: 'A',
    priority: '중',
    title: '근무복 지급 대상과 지급 기준이 필요합니다',
    found: [
      '정원현황은 2021년부터 지원직·계약직을 포함하기 시작해 215명 → 1,452명으로 늘었고, 2026-01-01 기준 1,344명(지원직 826, 계약직 303 포함)입니다.',
      '직군·직급별로 근무복을 지급하는지, 몇 벌을 얼마 주기로 주는지는 데이터에 없습니다.',
    ],
    impact: '정원 전체가 지급 대상인지 알 수 없어, 인원 보정과 1인당 지급량의 해석이 달라질 수 있습니다.',
    ask: '직군·직급별 근무복 지급 대상 여부와 지급 품목·수량·주기(예: 하계 셔츠 연 2벌)를 알려 주세요.',
    ours: '발주 추천에서 대상 직군을 선택하면 해당 직군 정원 합계가 인원 칸에 자동으로 들어갑니다.',
  },
  {
    id: 'A6',
    org: 'A',
    priority: '중',
    title: '두 파일의 품목 체계를 맞춰 주세요',
    found: [
      '같은 점퍼가 두 파일에 나뉘어 있고, 주문현황정보에서는 점퍼 코드가 10·11 두 개입니다.',
      '품목명 표기가 파일마다 다르고(예: 셔츠(수영강사), 조끼(패딩)) 컬럼 구성도 다릅니다.',
    ],
    impact: '품목을 합치는 규칙을 우리가 추정해서 만들어야 해서, 새 품목이나 이름이 바뀌면 분류가 틀릴 수 있습니다.',
    ask: '두 파일을 하나의 구조로 통합하거나, 공통 품목코드(품목군·세부 품목)를 부여해 주세요. 점퍼 코드 10·11의 구분 기준도 알려 주세요.',
    ours: "품목 분류 규칙을 설정으로 두고 두 파일의 점퍼를 합쳤습니다. 분류하지 못한 행은 '미분류'로 점검 표시를 답니다.",
  },
  {
    id: 'A7',
    org: 'A',
    priority: '중',
    title: '수량이 비어 있는 행을 보완해 주세요',
    found: ['근무복 주문현황정보에 수량이 없는 행이 41행 있습니다(2018·2020년).'],
    impact: '수량 결측 행은 집계에서 뺍니다. 그 해 주문량이 실제보다 적게 잡힐 수 있습니다.',
    ask: '수량이 비어 있는 41행(2018·2020년)의 실제 수량을 확인해 보완한 파일로 교체해 주세요.',
    ours: '결측 행은 불러오되 점검 표시를 달고 집계에서 제외합니다(데이터 점검 화면에서 확인).',
  },
  {
    id: 'A8',
    org: 'A',
    priority: '중',
    title: '정원현황의 산정 기준과 기준일자 규칙을 알려 주세요',
    found: [
      "한 해에 기준일자가 여러 개인 해가 있고, '합계' 행이 없어 직군별 값을 우리가 더해야 합니다.",
      '2021년에 정원이 215명 → 1,452명으로 급증했는데 기준이 바뀐 것인지 설명이 없습니다.',
    ],
    impact: "연도별 정원을 '그해 마지막 기준일자'로 가정해 계산합니다. 규칙이 다르면 1인당 지급량과 인원 보정이 달라집니다.",
    ask: '정원 산정 기준이 바뀐 시점과 이유(지원직·계약직 포함 시작 등), 연도별로 쓸 기준일자 규칙을 알려 주세요. 합계 행 또는 현원 데이터를 함께 개방해 주세요.',
    ours: '그해 마지막 기준일자의 전체 정원을 쓰고, 기준이 달랐던 2021년 이전은 1인당 지급량에서 뺐습니다.',
  },
  {
    id: 'A9',
    org: 'A',
    priority: '하',
    title: '갱신 방식과 주기를 알려 주세요',
    found: [
      '파일이 해마다 전체 이력을 다시 담아 나오고, 2026년 자료는 8월 27일까지, 2017년 자료는 11월부터라 1년치가 안 되는 해가 있습니다.',
      '컬럼 이름이 바뀔 때 미리 알려 주는 안내가 없습니다.',
    ],
    impact: '새 파일을 받을 때마다 기준 연도를 직접 정해 불러와야 하고, 컬럼이 바뀌면 불러오기가 실패합니다.',
    ask: '갱신 주기를 반기·분기로 줄이거나 증분(추가분) 파일 또는 Open API로 제공해 주세요. 컬럼 구성이 바뀌면 사전에 알려 주시고, 컬럼 설명(데이터 사전)을 함께 제공해 주세요.',
    ours: '기준 연도별로 최신 불러오기만 집계에 쓰고 이전 것은 기록으로 보관합니다. 컬럼 이름은 설정 화면에서 코드 수정 없이 바꿉니다.',
  },
  {
    id: 'B1',
    org: 'B',
    priority: '중',
    title: '근무복 품목 분류를 세분화해 주세요',
    found: [
      "셔츠·바지·점퍼·조끼가 모두 품명 '근무복'(세부품명번호 5310271001 하나)이고, 상의·하의·계절 구분은 물품규격명 설명 글에만 있습니다.",
      '최근 1년 등록 근무복 424건 중 점퍼는 1건뿐이고, 상·하의가 한 벌로 묶인 상품도 섞여 있습니다.',
    ],
    impact: '규격명 글의 키워드로 품목을 추정해야 해서 점퍼·조끼·패딩조끼는 표본이 부족해 단가를 시장가 대신 추정값으로 씁니다.',
    ask: '근무복의 세부 품목(셔츠·바지·점퍼·조끼 등)과 상의·하의·세트 구분을 코드나 별도 필드로 제공해 주세요.',
    ours: '규격명 키워드 규칙으로 품목군을 나누고, 표본이 5건 미만이면 시장가로 쓰지 않습니다(설정으로 조정).',
  },
  {
    id: 'B2',
    org: 'B',
    priority: '중',
    title: '목록 조회 결과가 페이지마다 중복·누락됩니다',
    found: [
      '같은 조건으로 조회해도 페이지를 넘길 때 같은 상품이 두 번 나오거나 빠집니다(전체 423건 중 한 번에 393~411건만 수집됨).',
      '한 페이지 최대 100건이고, numOfRows를 1,000으로 요청하면 10건만 옵니다.',
    ],
    impact: '여러 번 다시 읽어 합치는 방식으로 보완해야 하고, 그만큼 호출이 늘어납니다.',
    ask: '목록 조회의 정렬 기준을 고정(예: 등록일시·쇼핑계약번호 순)해 페이지 간 중복·누락이 없게 해 주세요. numOfRows 최대값도 안내해 주세요.',
    ours: '새 상품이 더 나오지 않을 때까지 반복해 읽고 계약번호로 중복을 거릅니다.',
  },
  {
    id: 'B3',
    org: 'B',
    priority: '하',
    title: '포털 상세 명세를 보완해 주세요',
    found: ['필수 파라미터 inqryDiv의 값 설명과 응답 항목 명세가 포털 화면에는 없고 별도 참고자료(Word)에만 있습니다.'],
    impact: '개발할 때 참고자료를 따로 받아야 하고, 신규 이용자가 헤매기 쉽습니다.',
    ask: '포털의 서비스 상세 화면에 요청 파라미터의 값 설명(inqryDiv 등)과 응답 항목 목록을 함께 게시해 주세요.',
    ours: '참고자료(Word)를 보고 구현했습니다.',
  },
  {
    id: 'C1',
    org: 'C',
    priority: '상',
    title: '녹색제품 정보조회 서비스가 오류를 반환합니다',
    found: [
      'getServiceList 호출 시 인증은 통과하지만 HTTP_ERROR(코드 04)가 계속 반환됩니다(2026-11-28 여러 차례 확인, 포털 명세의 샘플 값으로 호출해도 같음).',
    ],
    impact: "환경표지 인증 제품 수·기업·인증기간을 가져오지 못해 발주 추천의 녹색구매 비교 중 '인증 유효 제품' 칸이 비어 있습니다.",
    ask: '녹색제품 정보조회 서비스 GW(getServiceList) 호출 시 HTTP_ERROR(04)가 계속 발생합니다. 서비스 상태를 확인해 정상화해 주시고, 원인과 복구 예정 시점을 알려 주세요.',
    ours: "연동 코드는 완성했고 파일(CSV) 업로드 경로도 있습니다. 정상화되면 데이터 관리에서 '가져오기'만 누르면 됩니다. 환경표지 구분은 조달청 데이터로 대신 비교합니다.",
  },
  {
    id: 'C2',
    org: 'C',
    priority: '하',
    title: '분류 검색과 JSON 응답을 지원해 주세요',
    found: ['제품명으로만 검색할 수 있고(분류 코드 검색 없음) 응답이 XML뿐입니다.'],
    impact: '근무복 관련 제품을 찾으려면 여러 키워드로 반복 조회한 뒤 합쳐야 합니다.',
    ask: '대·중·소분류 코드로 검색할 수 있게 하고, JSON 응답 형식도 지원해 주세요.',
    ours: '제품명 키워드 6개(근무복·작업복·셔츠·바지·점퍼·조끼)로 조회해 합칩니다.',
  },
]

const PRIORITY_TONE: Record<Priority, 'bad' | 'warn' | 'neutral'> = { 상: 'bad', 중: 'warn', 하: 'neutral' }

const requestText = (item: RequestItem) =>
  `[${ORGS[item.org].name}] ${item.title}\n\n■ 확인된 내용\n${item.found.map((f) => `- ${f}`).join('\n')}\n\n■ 요청 사항\n${item.ask}\n\n■ 기대 효과\n${item.impact}\n`

export default function Requests() {
  const [org, setOrg] = useState<Org | 'ALL'>('ALL')
  const [copied, setCopied] = useState<string | null>(null)
  const [rec, setRec] = useState<Recommendation | null>(null)

  // A1의 '추천 수량이 얼마나 달라지나'를 지금 데이터로 보여준다
  useEffect(() => {
    api
      .post<Recommendation>('/api/uniform/recommendation', { basis: 'dedup' })
      .then(setRec)
      .catch(() => setRec(null))
  }, [])

  const shown = useMemo(() => ITEMS.filter((i) => org === 'ALL' || i.org === org), [org])
  const count = (o: Org) => ITEMS.filter((i) => i.org === o).length

  const copy = async (id: string, text: string) => {
    if (await copyText(text)) {
      setCopied(id)
      setTimeout(() => setCopied((c) => (c === id ? null : c)), 2000)
    }
  }

  return (
    <>
      <div className="page-head">
        <div>
          <h1>데이터 개선 요청</h1>
          <p className="muted">
            받아 오는 공공데이터에서 확인한 문제와 그 영향, 그리고 데이터를 제공하는 기관에 요청할 사항입니다. 요청 문구는 그대로 복사해
            보낼 수 있습니다.
          </p>
        </div>
        <button className="secondary" onClick={() => copy('ALL', ITEMS.map(requestText).join('\n――――――――――\n\n'))}>
          {copied === 'ALL' ? '복사했습니다' : '요청서 전체 복사'}
        </button>
      </div>

      <div className="grid cols-4">
        <Stat label="요청 사항" value={ITEMS.length} unit="건" note="기관 3곳" />
        <Stat label="우선순위 상" value={ITEMS.filter((i) => i.priority === '상').length} unit="건" note="서비스 결과에 바로 영향" />
        <Stat label="한국체육산업개발(주)" value={count('A')} unit="건" note="근무복 주문·정원 데이터" />
        <Stat label="조달청 · 환경산업기술원" value={count('B') + count('C')} unit="건" note="가격·녹색제품 데이터" />
      </div>

      {rec?.otherBasis && (
        <Notice tone="info">
          <strong>지금 데이터로 본 영향(요청 1번):</strong> {rec.year}년 필요 수량이 중복 제거 기준{' '}
          <strong>{num(rec.totals.quantity)}벌</strong>, 원자료 기준 <strong>{num(rec.otherBasis.quantity)}벌</strong>(
          {signedPct(rec.otherBasis.quantity / rec.totals.quantity - 1)})로 계산됩니다. 완전 중복 행을 어떻게 보느냐에 따라 추천 수량이
          이만큼 달라집니다.
        </Notice>
      )}

      <Tabs
        tabs={[
          { id: 'ALL' as const, label: `전체 (${ITEMS.length})` },
          { id: 'A' as const, label: `한국체육산업개발 (${count('A')})` },
          { id: 'B' as const, label: `조달청 (${count('B')})` },
          { id: 'C' as const, label: `한국환경산업기술원 (${count('C')})` },
        ]}
        value={org}
        onChange={setOrg}
      />

      {shown.map((item) => {
        const o = ORGS[item.org]
        return (
          <Card
            key={item.id}
            title={
              <span style={{ display: 'inline-flex', gap: 10, alignItems: 'center', flexWrap: 'wrap' }}>
                <span className="muted">{item.id}</span> {item.title}{' '}
                <Badge tone={PRIORITY_TONE[item.priority]}>우선순위 {item.priority}</Badge>
              </span>
            }
            subtitle={`${o.name} · ${o.data} · 문의 ${o.contact}`}
            actions={
              <button className="secondary" onClick={() => copy(item.id, requestText(item))}>
                {copied === item.id ? '복사했습니다' : '요청 문구 복사'}
              </button>
            }
          >
            <div className="req-grid">
              <div>
                <h3>확인된 내용</h3>
                <ul className="basis-lines" style={{ padding: 0, margin: '6px 0 12px' }}>
                  {item.found.map((f) => (
                    <li key={f}>{f}</li>
                  ))}
                </ul>
                <h3>서비스·업무에 미치는 영향</h3>
                <p style={{ margin: '6px 0 12px' }}>{item.impact}</p>
                <h3>우리 쪽 임시 대응</h3>
                <p className="muted" style={{ margin: '6px 0 0' }}>
                  {item.ours}
                </p>
              </div>
              <div className="req-ask">
                <h3>기관에 보낼 요청 문구</h3>
                <p>{item.ask}</p>
                {item.attachment === 'duplicates' && (
                  <p style={{ marginTop: 10 }}>
                    <a className="secondary-link" href="/api/uniform/duplicates/export" download>
                      첨부: 완전 중복 행 목록(CSV) 내려받기
                    </a>
                  </p>
                )}
              </div>
            </div>
          </Card>
        )
      })}

      <Notice tone="info">
        수치는 2026-08-27까지의 근무복 주문 파일과 2026-11-28 확인한 공공데이터포털 API 기준입니다. 데이터가 새로 개방되면 불러오기 후 이
        화면의 확인 내용을 다시 점검하세요.
      </Notice>
    </>
  )
}
