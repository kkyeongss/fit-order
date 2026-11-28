package kr.fitorder.config;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 관리자가 시연 데이터를 바로 처음 상태로 되돌린다(시연 모드가 아니어도 쓸 수 있다). */
@RestController
@RequestMapping("/api/admin/demo")
public class DemoController {

    private final DemoResetService reset;

    public DemoController(DemoResetService reset) {
        this.reset = reset;
    }

    @PostMapping("/reset")
    public DemoResetService.Result reset(Authentication auth) {
        return reset.reset(auth.getName());
    }
}
