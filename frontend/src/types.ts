// 서버 응답 모양. 백엔드 record와 같은 이름을 쓴다.

export type Basis = 'dedup' | 'raw'
export type GroupCode = 'SHIRT' | 'JACKET' | 'JUMPER' | 'PADDING_VEST' | 'PANTS' | 'VEST'
export type PriceSource = 'MANUAL' | 'MARKET' | 'ESTIMATE'
export type LoadMethod = 'UPLOAD' | 'API' | 'BATCH'

export interface YearSummary {
  year: number
  complete: boolean
  quantity: number
  uniqueQuantity: number
  duplicateQuantity: number
  orderDays: number
  byGroup: Record<string, number>
  /** 그해 주문 수량 × 현재 단가로 본 추정 구매 금액(실제 지출액 아님) */
  amount: number
}
export interface DataAsOf {
  lastOrderDate: string | null
  loads: { source: string; name: string; baseYear: number | null; fileName: string | null; loadedAt: string }[]
}
export interface Summary {
  basis: string
  completeYears: number[]
  annualAverage: { fromYear: number | null; toYear: number | null; quantity: number; orderDays: number }
  years: YearSummary[]
  months: { month: number; quantity: number; share: number }[]
  perCapita: { year: number; quantity: number; headcount: number; baseDate: string; perCapita: number }[]
  dataAsOf: DataAsOf
}

export interface MetricValues {
  code: string
  name: string
  unit: string
  basis: string
  calculatedAt: string | null
  values: { period: string; dimension: string; value: number }[]
}

export interface Period {
  from: number
  to: number
}
export interface SizeShare {
  size: string
  previousQuantity: number
  previousShare: number
  recentQuantity: number
  recentShare: number
  gap: number
}
export interface SizeAnalysis {
  group: GroupCode
  label: string
  comparison: { previous: Period; recent: Period } | null
  mismatchRate: number | null
  sizes: SizeShare[]
  years: { year: number; complete: boolean; quantity: number; shares: Record<string, number> }[]
}

export interface HeadcountView {
  years: { year: number; baseDate: string; headcount: number }[]
  latest: { year: number; baseDate: string; headcount: number } | null
  latestByJobFamily: { jobFamily: string; headcount: number }[]
}

export interface SizeLine {
  source: string
  rawSize: string | null
  size: string
  ratio: number
  base: number
  safety: number
  /** 필요 수량(안전재고 포함) */
  quantity: number
  stock: number
  /** 재고를 뺀 발주 수량 */
  orderQuantity: number
  /** 정기 발주 회차별 발주 수량(순번은 totals.rounds와 같다) */
  rounds: number[]
  unknownCode: boolean
}
export interface Savings {
  group: GroupCode
  mismatchRate: number
  annualQuantity: number
  savedQuantity: number
  unitPrice: number
  priceSource: PriceSource
  savedAmount: number
  carbonKg: number
}
export interface ItemPlan {
  group: GroupCode
  label: string
  total: { years: number[]; quantity: number; rule: string }
  headcountYear: number | null
  baseHeadcount: number | null
  headcountFactor: number
  baseQuantity: number
  safetyRate: number
  safetyQuantity: number
  quantity: number
  sizeRule: string
  sizes: SizeLine[]
  unitPrice: number
  priceSource: PriceSource
  amount: number
  savings: Savings | null
  stockTotal: number
  orderQuantity: number
  orderAmount: number
  rounds: { index: number; quantity: number; amount: number }[]
  warnings: string[]
  history: { year: number; quantity: number }[]
  totalOverride: number | null
}
export interface MarketPrice {
  group: GroupCode
  label: string
  sampleCount: number
  medianPrice: number
  q1Price: number
  q3Price: number
  ecoCount: number
  ecoMedianPrice: number | null
  usable: boolean
  method: 'API' | 'UPLOAD'
  periodFrom: string | null
  periodTo: string | null
  fetchedBy: string
  fetchedAt: string
}
export interface GreenSummary {
  group: GroupCode | null
  label: string
  validCount: number
  companyCount: number
  examples: string[]
}
export interface Recommendation {
  year: number
  basis: string
  headcount: number | null
  latestHeadcount: { year: number; baseDate: string; headcount: number } | null
  items: ItemPlan[]
  totals: {
    quantity: number
    safetyQuantity: number
    amount: number
    savedQuantity: number
    savedAmount: number
    carbonKg: number
    priceEstimated: boolean
    stockTotal: number
    orderQuantity: number
    orderAmount: number
    range: { low: number; high: number; errorRate: number }
    rounds: { month: number; share: number; quantity: number; amount: number }[]
  }
  orderMonths: { month: number; quantity: number; share: number }[]
  improvementRate: number
  marketPrices: MarketPrice[]
  greenProducts: GreenSummary[]
  warnings: string[]
  yearRange: { from: number; to: number }
  otherBasis: { basis: string; quantity: number; orderQuantity: number; orderAmount: number } | null
  lastOrderDate: string | null
}

export interface BacktestAverage {
  method: string
  label: string
  totalError: number
  sizeError: number
}
export interface BacktestGroup {
  group: GroupCode | null
  label: string
  averages: BacktestAverage[]
}
export interface Backtest {
  fromYear: number
  toYear: number
  groups: BacktestGroup[]
  total: BacktestGroup
}

export interface LoadHistory {
  id: number
  itemCode: string
  source: string
  baseYear: number | null
  fileName: string | null
  rowCount: number
  duplicateCount: number
  flaggedCount: number
  status: 'SUCCESS' | 'FAILED'
  message: string | null
  active: boolean
  loadedBy: string
  loadedAt: string
}

export interface SourceField {
  name: string
  label: string
  required: boolean
}
export interface SourceView {
  code: string
  itemCode: string
  name: string
  method: LoadMethod
  url: string | null
  perPage: number
  charset: string
  columns: Record<string, string>
  fields: SourceField[]
  apiKeyConfigured: boolean
  lastLoad: { at: string; status: string; message: string | null; rowCount: number } | null
  activeBaseYear: number | null
  updatedBy: string
  updatedAt: string
}
export interface Preview {
  columns: string[]
  sampleRows: Record<string, string>[]
  rowCount: number
  mapping: Record<string, string>
  missingColumns: string[]
  unmappedFields: string[]
}

export interface QualitySample {
  lineNo: number
  rawValues: string
}
export interface SourceQuality {
  source: string
  name: string
  baseYear: number | null
  fileName: string | null
  loadedAt: string
  rowCount: number
  duplicateRows: number
  flaggedRows: number
  flags: { flag: string; label: string; rows: number; samples: QualitySample[] }[]
  sizeCodes: number
  unmappedSizeCodes: number
}

export interface PriceView {
  group: GroupCode
  label: string
  price: number
  source: PriceSource
  sourceLabel: string
  note: string
  validFrom: string | null
  enteredBy: string | null
}
export interface SizeRow {
  source: string
  sourceName: string
  rawSize: string
  rowCount: number
  standardSize: string | null
}
export interface MarketFetchResult {
  itemCount: number
  unclassifiedCount: number
  unclassifiedExamples: string[]
  prices: MarketPrice[]
}
export interface GreenProductView {
  product: { productId: string; name: string; company: string | null; certTo: string | null; certStatus: string | null }
  group: GroupCode | null
  valid: boolean
}
export interface GreenFetchResult {
  productCount: number
  validCount: number
  groups: GreenSummary[]
}

export interface EsgItem {
  code: string
  name: string
  category: 'E' | 'S' | 'G'
  enabled: boolean
  loadMethod: LoadMethod
  sources: { code: string; name: string; method: LoadMethod; hasUrl: boolean }[]
  metrics: { code: string; name: string; unit: string }[]
}

export interface StockRow {
  group: GroupCode
  source: string
  rawSize: string
  quantity: number
  updatedBy: string
  updatedAt: string
}

export interface GroupSaving {
  group: GroupCode
  label: string
  mismatchRate: number
  annualQuantity: number
  savedQuantity: number
  savedAmount: number
  carbonKg: number
  unitPrice: number
  priceSource: PriceSource
  carbonFactor: number
  carbonSource: string
}
export interface SavingsReport {
  comparison: { previous: Period; recent: Period } | null
  improvementRate: number
  scenarios: { rate: number; savedQuantity: number; savedAmount: number; carbonKg: number }[]
  groups: GroupSaving[]
  savedAmount: number
  savedQuantity: number
  carbonKg: number
  estimatedPriceShare: number
  estimatedGroups: string[]
}

export interface PlanSummary {
  id: number
  planYear: number
  basis: 'DEDUP' | 'RAW'
  headcount: number | null
  note: string | null
  savedBy: string
  savedAt: string
  quantity: number
  orderQuantity: number
  orderAmount: number
  itemCount: number
}
export interface PlanCompare {
  plan: PlanSummary
  hasActual: boolean
  yearComplete: boolean
  lastOrderDate: string | null
  plannedQuantity: number
  actualQuantity: number
  difference: number
  differencePercent: number | null
  sizeMismatchRate: number
  items: {
    group: string
    label: string
    planned: number
    actual: number
    difference: number
    differencePercent: number | null
    sizeMismatchRate: number
    sizes: { size: string; planned: number; actual: number; difference: number }[]
  }[]
  message: string | null
}

export interface AuditRow {
  id: number
  username: string
  action: string
  target: string
  detail: string | null
  createdAt: string
}
export interface UserView {
  username: string
  role: 'USER' | 'ADMIN'
  roleLabel: string
  enabled: boolean
  createdAt: string
}
export interface CarbonFactor {
  group: GroupCode
  label: string
  kgCo2e: number
  source: string
}
