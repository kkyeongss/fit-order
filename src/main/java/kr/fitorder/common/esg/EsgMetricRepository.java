package kr.fitorder.common.esg;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EsgMetricRepository extends JpaRepository<EsgMetric, String> {

    List<EsgMetric> findByItemCodeOrderByCode(String itemCode);
}
