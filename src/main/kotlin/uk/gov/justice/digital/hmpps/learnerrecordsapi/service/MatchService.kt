package uk.gov.justice.digital.hmpps.learnerrecordsapi.service

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.learnerrecordsapi.models.MatchStatus
import uk.gov.justice.digital.hmpps.learnerrecordsapi.models.db.MatchEntity
import uk.gov.justice.digital.hmpps.learnerrecordsapi.models.request.ConfirmMatchRequest
import uk.gov.justice.digital.hmpps.learnerrecordsapi.models.request.ConfirmNoMatchRequest
import uk.gov.justice.digital.hmpps.learnerrecordsapi.models.response.CheckMatchResponse
import uk.gov.justice.digital.hmpps.learnerrecordsapi.models.response.CheckMatchStatus
import uk.gov.justice.digital.hmpps.learnerrecordsapi.repository.MatchRepository
import uk.gov.justice.hmpps.kotlin.sar.HmppsSubjectAccessRequestContent
import java.time.LocalDate

@Service
class MatchService(
  private val matchRepository: MatchRepository,
) {

  fun findMatch(nomisId: String): CheckMatchResponse? {
    val entity = matchRepository.findFirstByNomisIdOrderByIdDesc(nomisId)
    if (entity == null || entity.matchStatus == MatchStatus.UNMATCHED.toString()) {
      return null
    }
    return entity.let {
      CheckMatchResponse(
        matchedUln = it.matchedUln,
        givenName = it.givenName,
        familyName = it.familyName,
        status = if (it.matchStatus == MatchStatus.MATCH_NOT_POSSIBLE.toString()) {
          CheckMatchStatus.NoMatch
        } else {
          CheckMatchStatus.Found
        },
      )
    }
  }

  fun saveMatch(nomisId: String, confirmMatchRequest: ConfirmMatchRequest): MatchStatus? {
    if (findMatch(nomisId)?.matchedUln == confirmMatchRequest.matchingUln) {
      return MatchStatus.MATCHED
    }
    val entity = confirmMatchRequest.asMatchEntity(nomisId)
    return MatchStatus.fromString(matchRepository.save(entity).matchStatus)
  }

  fun saveNoMatch(nomisId: String, confirmNoMatchRequest: ConfirmNoMatchRequest): MatchStatus? {
    if (findMatch(nomisId)?.status == CheckMatchStatus.NoMatch) {
      return MatchStatus.MATCH_NOT_POSSIBLE
    }
    val entity = confirmNoMatchRequest.asMatchEntity(nomisId)
    return MatchStatus.fromString(matchRepository.save(entity).matchStatus)
  }

  fun unMatch(nomisId: String): MatchStatus? {
    if (findMatch(nomisId) == null) {
      return MatchStatus.UNMATCHED
    }
    val entity = MatchEntity(
      nomisId = nomisId,
      matchStatus = MatchStatus.UNMATCHED.toString(),
    )
    return MatchStatus.fromString(matchRepository.save(entity).matchStatus)
  }

  fun isUnmatched(nomisId: String, uln: String): Boolean = !matchRepository.existsMatchWithDifferentId(nomisId, uln)

  fun getDataForSubjectAccessRequest(
    nomisId: String,
    fromDate: LocalDate?,
    toDate: LocalDate?,
  ): HmppsSubjectAccessRequestContent? {
    val foundData = matchRepository.findForSubjectAccessRequest(
      nomisId,
      fromDate?.atStartOfDay(),
      toDate?.plusDays(1)?.atStartOfDay()?.minusNanos(1L),
    )
    return when (foundData.size) {
      0 -> null
      else -> HmppsSubjectAccessRequestContent(content = foundData)
    }
  }
}
