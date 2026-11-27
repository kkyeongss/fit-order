package kr.fitorder.common;

import java.math.BigDecimal;

/**
 * 지표 값 하나.
 *
 * @param period    기간('2024', '2024-04')
 * @param dimension 세부 구분(품목 등). 전체는 ""
 */
public record MetricValue(String period, String dimension, BigDecimal value) {
}
