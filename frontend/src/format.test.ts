import { describe, expect, it } from 'vitest'
import { carbon, num, pct, pctValue, splitSizeSource, won } from './format'

describe('숫자 표시', () => {
  it('천 단위 쉼표와 단위를 붙인다', () => {
    expect(num(1402)).toBe('1,402')
    expect(num(1402.456, 1)).toBe('1,402.5')
    expect(won(4423336.4)).toBe('4,423,336원')
  })

  it('비율은 %로, 서버가 이미 %로 준 값은 그대로', () => {
    expect(pct(0.1612)).toBe('16.1%')
    expect(pct(0.5, 0)).toBe('50%')
    expect(pctValue(16.7)).toBe('16.7%')
  })

  it('없는 값은 -', () => {
    expect(num(null)).toBe('-')
    expect(won(undefined)).toBe('-')
    expect(pct(null)).toBe('-')
  })

  it('탄소는 1톤 미만이면 kg, 이상이면 t', () => {
    expect(carbon(471)).toBe('471 kg CO₂e')
    expect(carbon(2171.18)).toBe('2.17 t CO₂e')
  })
})

describe('사이즈 출처 꼬리표', () => {
  it('모두 같은 출처면 꼬리표를 떼고 출처를 알려준다', () => {
    const sp = splitSizeSource(['95 (근무복 주문현황)', '100 (근무복 주문현황)'])
    expect(sp.source).toBe('근무복 주문현황')
    expect(sp.label('95 (근무복 주문현황)')).toBe('95')
  })

  it('출처가 섞여 있으면 그대로 둔다', () => {
    const sp = splitSizeSource(['95 (근무복 주문현황)', '50 (근무복 주문현황정보)'])
    expect(sp.source).toBeNull()
    expect(sp.label('50 (근무복 주문현황정보)')).toBe('50 (근무복 주문현황정보)')
  })

  it('표준 사이즈로 매핑된 이름이 섞여 있어도 그대로 둔다', () => {
    const sp = splitSizeSource(['L', '100 (근무복 주문현황)'])
    expect(sp.source).toBeNull()
  })
})
