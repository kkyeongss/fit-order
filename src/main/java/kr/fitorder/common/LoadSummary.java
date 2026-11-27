package kr.fitorder.common;

/** 적재 결과 요약. flaggedCount는 품질 플래그가 달린 행 수(중복 제외). */
public record LoadSummary(int rowCount, int duplicateCount, int flaggedCount) {
}
