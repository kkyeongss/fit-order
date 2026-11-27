package kr.fitorder.common;

/** 적재 한 번의 정보. */
public record LoadContext(long loadId, String sourceCode, int baseYear) {
}
