package kr.fitorder.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.Set;
import kr.fitorder.common.Json;
import org.springframework.web.servlet.HandlerInterceptor;

/** 시연 모드에서 데이터를 바꾸는 관리자 요청을 막는다. 조회·미리보기·시연 초기화는 허용한다. */
public class DemoModeInterceptor implements HandlerInterceptor {

    private static final Set<String> READ_METHODS = Set.of("GET", "HEAD", "OPTIONS");

    private final boolean enabled;

    public DemoModeInterceptor(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!enabled || READ_METHODS.contains(request.getMethod()) || !blocked(request.getMethod(), request.getRequestURI())) {
            return true;
        }
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(Json.object(Map.of("detail",
                "공개 시연 서버에서는 이 기능(데이터·설정·계정 변경)이 막혀 있습니다. 조회와 미리보기, 담당자 기능(단가·재고·발주안)만 쓸 수 있습니다.",
                "title", "Forbidden")));
        return false;
    }

    static boolean blocked(String method, String path) {
        if (path.startsWith("/api/admin/")) {
            return !path.equals("/api/admin/demo/reset") && !path.matches("/api/admin/sources/[^/]+/preview");
        }
        if (method.equals("PUT") && path.startsWith("/api/esg/")) {
            return true;
        }
        return path.equals("/api/auth/password");
    }
}
