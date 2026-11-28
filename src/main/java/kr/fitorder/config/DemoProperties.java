package kr.fitorder.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 공개 시연 서버 보호 모드(fitorder.demo.enabled=true, 환경변수 FITORDER_DEMO).
 * 켜면 관리자 기능 중 데이터를 바꾸는 것(적재, 원천 설정, 사이즈 매핑, 항목 설정, 계정, 비밀번호)을 막고,
 * 담당자 기능(단가·재고·발주안)은 허용하되 매일 새벽에 처음 상태로 되돌린다.
 * 공개된 시연 계정으로 심사 중에 데이터가 망가지는 것을 막기 위한 장치다.
 */
@ConfigurationProperties(prefix = "fitorder.demo")
public record DemoProperties(boolean enabled) {
}
