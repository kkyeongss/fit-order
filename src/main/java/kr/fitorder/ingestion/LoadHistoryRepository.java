package kr.fitorder.ingestion;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface LoadHistoryRepository extends JpaRepository<LoadHistory, Long> {

    boolean existsByFileHashAndStatus(String fileHash, LoadStatus status);

    Optional<LoadHistory> findFirstBySourceAndActiveTrue(String source);

    List<LoadHistory> findAllByOrderByLoadedAtDesc();

    @Modifying(flushAutomatically = true)
    @Query("update LoadHistory h set h.active = false where h.source = :source and h.active = true")
    int deactivateAll(String source);
}
