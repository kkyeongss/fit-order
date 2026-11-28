package kr.fitorder.module.uniform.analysis;

/** 연도 구간(양 끝 포함). 지표 기간 이름은 '2023-2025', 한 해면 '2024'. */
public record Period(int from, int to) {

    public Period {
        if (from > to) {
            throw new IllegalArgumentException("기간이 거꾸로입니다: " + from + "-" + to);
        }
    }

    public static Period year(int year) {
        return new Period(year, year);
    }

    public boolean contains(int year) {
        return from <= year && year <= to;
    }

    public int length() {
        return to - from + 1;
    }

    public String label() {
        return from == to ? String.valueOf(from) : from + "-" + to;
    }

    /** 이전 구간과 최근 구간. 미스매치율은 이전 구간 비율로 최근 구간을 주문했을 때의 어긋남이다. */
    public record Comparison(Period previous, Period recent) {

        public String label() {
            return recent.label();
        }
    }
}
