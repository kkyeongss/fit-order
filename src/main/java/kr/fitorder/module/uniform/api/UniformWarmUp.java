package kr.fitorder.module.uniform.api;

import jakarta.annotation.PreDestroy;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import kr.fitorder.common.Basis;
import kr.fitorder.common.EsgDataChangedEvent;
import kr.fitorder.common.ResultsChangedEvent;
import kr.fitorder.module.uniform.analysis.RecommendationService;
import kr.fitorder.module.uniform.analysis.RecommendationService.Recommendation;
import kr.fitorder.module.uniform.analysis.RecommendationService.Request;
import kr.fitorder.module.uniform.analysis.UniformReportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 화면이 처음 쓰는 계산을 미리 해 둔다(서버가 뜬 직후, 그리고 데이터가 바뀐 직후).
 * CPU가 느린 작은 서버에서는 JVM이 달궈지기 전 첫 계산이 10초 가까이 걸려, 처음 여는 사람이 오래 기다리기 때문이다.
 * 결과는 ResultCache에 쌓이고, 실패해도 서비스에는 영향이 없다(데이터가 없으면 건너뜀).
 * 끄기: fitorder.warmup=false (테스트가 그렇게 한다)
 */
@Component
@Order(20)
@ConditionalOnProperty(name = "fitorder.warmup", havingValue = "true", matchIfMissing = true)
class UniformWarmUp implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(UniformWarmUp.class);
    /** 변경이 연달아 일어나도 한 번만 하도록 잠깐 기다린다. */
    private static final int AFTER_CHANGE_DELAY_SECONDS = 3;

    private final UniformReportService reports;
    private final RecommendationService recommendations;
    private final AtomicBoolean queued = new AtomicBoolean();
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "uniform-warmup");
        t.setDaemon(true);
        return t;
    });

    UniformWarmUp(UniformReportService reports, RecommendationService recommendations) {
        this.reports = reports;
        this.recommendations = recommendations;
    }

    @Override
    public void run(ApplicationArguments args) {
        schedule(0);
    }

    @TransactionalEventListener(fallbackExecution = true)
    void onDataChanged(EsgDataChangedEvent event) {
        schedule(AFTER_CHANGE_DELAY_SECONDS);
    }

    @TransactionalEventListener(fallbackExecution = true)
    void onResultsChanged(ResultsChangedEvent event) {
        schedule(AFTER_CHANGE_DELAY_SECONDS);
    }

    private void schedule(int delaySeconds) {
        if (queued.compareAndSet(false, true)) {
            executor.schedule(() -> {
                queued.set(false);
                warm();
            }, delaySeconds, TimeUnit.SECONDS);
        }
    }

    void warm() {
        long start = System.currentTimeMillis();
        try {
            for (Basis basis : Basis.values()) {
                reports.summary(basis);
                reports.sizes(basis, null);
                recommendations.backtest(basis);
            }
            reports.headcount();
            recommendations.recommend(new Request(null, null, null, Basis.RAW));
            Recommendation rec = recommendations.recommend(new Request(null, null, null, Basis.DEDUP));
            RecommendationExcel.write(rec);   // 엑셀 라이브러리 클래스를 미리 읽어 둔다
            log.info("화면 계산 워밍업 완료: {}ms", System.currentTimeMillis() - start);
        } catch (RuntimeException e) {
            log.info("워밍업을 건너뜀: {}", e.getMessage());
        }
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }
}
