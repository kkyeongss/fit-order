package kr.fitorder.common.esg;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 시작할 때 지표를 한 번 계산한다. 계산식이 바뀐 버전을 배포해도 저장된 값이 새 식을 따른다.
 *
 * <p>화면(React)만 바뀐 배포처럼 계산식이 그대로일 때는 fitorder.metrics.recalculate-on-start=false로 건너뛸 수 있다
 * (느린 서버에서 재시작 부담을 줄인다. deploy.sh의 QUICK=1). 기본값은 true다.
 */
@Component
@Order(10)
public class MetricBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(MetricBootstrap.class);

    private final MetricService metrics;
    private final boolean recalculateOnStart;

    public MetricBootstrap(MetricService metrics,
                           @Value("${fitorder.metrics.recalculate-on-start:true}") boolean recalculateOnStart) {
        this.metrics = metrics;
        this.recalculateOnStart = recalculateOnStart;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!recalculateOnStart) {
            log.info("시작할 때 지표 재계산을 건너뜀(fitorder.metrics.recalculate-on-start=false)");
            return;
        }
        metrics.recalculateAll();
    }
}
