package kr.fitorder.common;

import java.util.List;

/** 원천 데이터를 공통 레코드로 읽는다. 지금은 CSV 업로드, 나중에 API·배치 구현을 추가한다. */
public interface DataIngestion {

    LoadMethod method();

    List<RawRecord> read(IngestionRequest request);
}
