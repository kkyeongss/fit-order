package kr.fitorder.common;

import java.util.List;
import java.util.OptionalInt;

/** 공통 레코드를 항목별 원본 테이블에 저장한다. 원천 데이터 하나당 하나. */
public interface SourceLoader {

    /** 원천 코드. application.yml의 fitorder.ingestion.sources 키와 같다. */
    String sourceCode();

    LoadSummary load(LoadContext context, List<RawRecord> records);

    /** 기준연을 입력하지 않았을 때 쓸 기본값. */
    default OptionalInt suggestBaseYear(List<RawRecord> records) {
        return OptionalInt.empty();
    }
}
