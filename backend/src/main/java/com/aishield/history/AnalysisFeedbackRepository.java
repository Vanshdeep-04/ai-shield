package com.aishield.history;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AnalysisFeedbackRepository extends JpaRepository<AnalysisFeedback, UUID> {

    Optional<AnalysisFeedback> findByAnalysisIdAndUserId(UUID analysisId, String userId);

    List<AnalysisFeedback> findByAnalysisIdIn(Collection<UUID> analysisIds);

    @Modifying
    @Query("delete from AnalysisFeedback f where f.analysisId = :analysisId")
    int deleteByAnalysisId(@Param("analysisId") UUID analysisId);

    @Modifying
    @Query("delete from AnalysisFeedback f where f.userId = :userId")
    int deleteAllByUserId(@Param("userId") String userId);
}
