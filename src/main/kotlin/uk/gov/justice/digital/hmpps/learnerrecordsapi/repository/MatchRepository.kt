package uk.gov.justice.digital.hmpps.learnerrecordsapi.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.learnerrecordsapi.models.db.MatchEntity
import java.time.LocalDateTime

@Repository
interface MatchRepository : JpaRepository<MatchEntity, Long> {

  fun findFirstByNomisIdOrderByIdDesc(
    nomisId: String,
  ): MatchEntity?

  @Query(
    """
      SELECT m FROM MatchEntity m 
      WHERE m.nomisId = :nomisId 
      AND (:fromDate IS NULL OR m.dateCreated >= :fromDate) 
      AND (:toDate IS NULL OR m.dateCreated <= :toDate)
    """,
  )
  fun findForSubjectAccessRequest(
    @Param("nomisId") nomisId: String,
    @Param("fromDate") fromDate: LocalDateTime?,
    @Param("toDate") toDate: LocalDateTime?,
  ): List<MatchEntity>

  @Query(
    """
      SELECT DISTINCT m.nomisId FROM MatchEntity m
      WHERE m.nomisId <> :nomisId
    """,
  )
  fun findAllNomisIdsExcept(
    @Param("nomisId") nomisId: String,
  ): Set<String>

  @Query(
    """
        SELECT
            CASE WHEN EXISTS
                (
                    SELECT m FROM MatchEntity m
                    WHERE m.nomisId <> :nomisId AND m.matchedUln = :uln
                    AND m.id = (
                        SELECT MAX(m2.id)
                        FROM MatchEntity m2
                        WHERE m2.nomisId = m.nomisId
                    )
                )
            THEN TRUE
            ELSE FALSE
            END
      """,
  )
  fun existsMatchWithDifferentId(@Param("nomisId") nomisId: String, @Param("uln") uln: String): Boolean
}
