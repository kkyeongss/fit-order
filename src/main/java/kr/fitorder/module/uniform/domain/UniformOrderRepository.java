package kr.fitorder.module.uniform.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface UniformOrderRepository extends JpaRepository<UniformOrder, Long> {

    interface SizeUsage {
        String getSource();

        String getRawSize();

        long getRowCount();

        String getStandardSize();
    }

    /** 활성 적재 데이터에 나오는 사이즈 코드와 현재 매핑. */
    @Query(nativeQuery = true, value = """
            select o.source as "source", o.raw_size as "rawSize", count(*) as "rowCount",
                   m.standard_size as "standardSize"
            from uniform_order_active o
            left join size_mapping m on m.source = o.source and m.raw_size = o.raw_size
            where o.raw_size is not null
            group by o.source, o.raw_size, m.standard_size
            """)
    List<SizeUsage> findSizeUsage();
}
