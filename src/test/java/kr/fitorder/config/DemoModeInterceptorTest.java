package kr.fitorder.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class DemoModeInterceptorTest {

    @Test
    void 관리자의_데이터_설정_계정_변경은_막고_조회와_미리보기_시연_초기화는_허용한다() {
        assertThat(DemoModeInterceptor.blocked("POST", "/api/admin/uploads")).isTrue();
        assertThat(DemoModeInterceptor.blocked("POST", "/api/admin/sources/headcount/fetch")).isTrue();
        assertThat(DemoModeInterceptor.blocked("PUT", "/api/admin/sources/headcount")).isTrue();
        assertThat(DemoModeInterceptor.blocked("PUT", "/api/admin/size-mappings")).isTrue();
        assertThat(DemoModeInterceptor.blocked("PUT", "/api/admin/carbon-factors")).isTrue();
        assertThat(DemoModeInterceptor.blocked("POST", "/api/admin/users")).isTrue();
        assertThat(DemoModeInterceptor.blocked("PUT", "/api/esg/items/uniform")).isTrue();
        assertThat(DemoModeInterceptor.blocked("POST", "/api/auth/password")).isTrue();

        assertThat(DemoModeInterceptor.blocked("POST", "/api/admin/demo/reset")).isFalse();
        assertThat(DemoModeInterceptor.blocked("POST", "/api/admin/sources/headcount/preview")).isFalse();
        // 담당자 기능(단가·재고·발주안)은 허용
        assertThat(DemoModeInterceptor.blocked("PUT", "/api/uniform/prices")).isFalse();
        assertThat(DemoModeInterceptor.blocked("PUT", "/api/uniform/stock")).isFalse();
        assertThat(DemoModeInterceptor.blocked("POST", "/api/uniform/plans")).isFalse();
        assertThat(DemoModeInterceptor.blocked("POST", "/api/uniform/recommendation")).isFalse();
    }

    @Test
    void 시연_모드가_켜져_있을_때만_403으로_답한다() throws Exception {
        var blocked = new MockHttpServletRequest("POST", "/api/admin/uploads");
        blocked.setRequestURI("/api/admin/uploads");
        var response = new MockHttpServletResponse();

        assertThat(new DemoModeInterceptor(true).preHandle(blocked, response, new Object())).isFalse();
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("공개 시연 서버");

        var off = new MockHttpServletResponse();
        assertThat(new DemoModeInterceptor(false).preHandle(blocked, off, new Object())).isTrue();
        var read = new MockHttpServletRequest("GET", "/api/admin/quality");
        read.setRequestURI("/api/admin/quality");
        assertThat(new DemoModeInterceptor(true).preHandle(read, new MockHttpServletResponse(), new Object())).isTrue();
    }
}
