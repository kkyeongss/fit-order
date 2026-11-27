package kr.fitorder.ingestion;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DataSourceRepository extends JpaRepository<DataSource, String> {

    List<DataSource> findAllByOrderByItemCodeAscCodeAsc();
}
