package kr.fitorder.common;

/**
 * 적재 한 번의 정보.
 *
 * @param spec 이번 적재에 쓴 원천 설정(컬럼 매핑 포함)
 */
public record LoadContext(long loadId, String sourceCode, int baseYear, SourceSpec spec) {

    public LoadContext(long loadId, String sourceCode, int baseYear) {
        this(loadId, sourceCode, baseYear, null);
    }
}
