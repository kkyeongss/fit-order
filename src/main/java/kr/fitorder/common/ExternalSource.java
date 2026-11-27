package kr.fitorder.common;

import java.util.List;

/**
 * 외부 데이터(단가·인증제품 등) 조회.
 * 공개망에서는 API 호출 구현, 망분리 내부망에서는 파일 업로드 구현을 설정으로 고른다.
 */
public interface ExternalSource<T> {

    String code();

    List<T> fetch();
}
