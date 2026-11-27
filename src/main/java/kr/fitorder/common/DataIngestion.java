package kr.fitorder.common;

/** 원천 데이터를 공통 레코드로 읽는다. 지금은 CSV 업로드와 Open API, 나중에 배치 구현을 추가한다. */
public interface DataIngestion {

    LoadMethod method();

    /** 컬럼 매핑이 맞는지는 확인하지 않는다(적재 흐름과 미리보기가 각자 확인). */
    RawData read(IngestionRequest request);
}
