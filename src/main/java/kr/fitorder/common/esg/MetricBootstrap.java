package kr.fitorder.common.esg;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** 시작할 때 지표를 한 번 계산한다. 계산식이 바뀐 버전을 배포해도 저장된 값이 새 식을 따른다. */
@Component
@Order(10)
public class MetricBootstrap implements ApplicationRunner {

    private final MetricService metrics;

    public MetricBootstrap(MetricService metrics) {
        this.metrics = metrics;
    }

    @Override
    public void run(ApplicationArguments args) {
        metrics.recalculateAll();
    }
}
