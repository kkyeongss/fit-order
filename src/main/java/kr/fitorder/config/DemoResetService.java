package kr.fitorder.config;

import kr.fitorder.admin.AuditService;
import kr.fitorder.common.EsgDataChangedEvent;
import kr.fitorder.common.ResultsChangedEvent;
import kr.fitorder.module.uniform.UniformModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 시연 서버를 처음 상태로: 방문자가 입력한 단가·재고·발주안을 지운다(적재 데이터와 계정은 그대로).
 * 단가를 지웠으면 지표가 다시 계산된다.
 */
@Service
public class DemoResetService {

    private static final Logger log = LoggerFactory.getLogger(DemoResetService.class);

    public record Result(int prices, int stock, int plans) {

        boolean any() {
            return prices + stock + plans > 0;
        }
    }

    private final JdbcTemplate jdbc;
    private final AuditService audit;
    private final ApplicationEventPublisher events;

    public DemoResetService(JdbcTemplate jdbc, AuditService audit, ApplicationEventPublisher events) {
        this.jdbc = jdbc;
        this.audit = audit;
        this.events = events;
    }

    @Transactional
    public Result reset(String user) {
        Result result = new Result(jdbc.update("delete from unit_price"), jdbc.update("delete from stock"),
                jdbc.update("delete from order_plan"));
        if (result.any()) {
            audit.record(user, "DEMO_RESET", "demo", "단가 " + result.prices() + "건, 재고 " + result.stock() + "건, 발주안 "
                    + result.plans() + "건 삭제");
            events.publishEvent(result.prices() > 0 ? new EsgDataChangedEvent(UniformModule.CODE, "시연 초기화")
                    : new ResultsChangedEvent("시연 초기화"));
            log.info("시연 데이터 초기화: {}", result);
        }
        return result;
    }
}
