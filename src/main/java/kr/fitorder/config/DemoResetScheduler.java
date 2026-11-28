package kr.fitorder.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/** 시연 모드일 때만: 매일 새벽 4시(한국 시간)에 방문자가 입력한 값을 지운다. */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "fitorder.demo.enabled", havingValue = "true")
public class DemoResetScheduler {

    private final DemoResetService reset;

    public DemoResetScheduler(DemoResetService reset) {
        this.reset = reset;
    }

    @Scheduled(cron = "0 0 4 * * *", zone = "Asia/Seoul")
    void nightly() {
        reset.reset("system");
    }
}
