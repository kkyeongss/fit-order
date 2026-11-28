package kr.fitorder.common.esg;

import java.util.Collection;
import java.util.List;
import kr.fitorder.common.Basis;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface EsgMetricValueRepository extends JpaRepository<EsgMetricValue, Long> {

    List<EsgMetricValue> findByMetricCodeInAndBasisOrderByMetricCodeAscPeriodAscDimensionAsc(
            Collection<String> metricCodes, Basis basis);

    @Modifying
    @Query("delete from EsgMetricValue v where v.metricCode = :metricCode and v.basis = :basis")
    void deleteByMetricCodeAndBasis(String metricCode, Basis basis);
}
