package com.aishield.history;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AnalysisRecordRepository extends JpaRepository<AnalysisRecord, UUID> {

    Page<AnalysisRecord> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);

    Optional<AnalysisRecord> findByIdAndUserId(UUID id, String userId);

    @Modifying
    @Query("delete from AnalysisRecord r where r.userId = :userId")
    int deleteAllByUserId(@Param("userId") String userId);
}
