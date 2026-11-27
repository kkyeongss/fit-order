package kr.fitorder.module.uniform.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SizeMappingRepository extends JpaRepository<SizeMapping, Long> {

    Optional<SizeMapping> findBySourceAndRawSize(String source, String rawSize);
}
