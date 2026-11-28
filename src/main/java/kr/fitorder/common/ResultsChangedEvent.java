package kr.fitorder.common;

/**
 * 화면에 보이는 계산 결과의 입력이 바뀌었다(지표는 다시 계산할 필요 없는 것, 예: 녹색제품 목록).
 * 지표까지 다시 계산해야 하는 변경은 EsgDataChangedEvent를 쓴다(이것도 계산 결과를 버리게 한다).
 */
public record ResultsChangedEvent(String reason) {
}
