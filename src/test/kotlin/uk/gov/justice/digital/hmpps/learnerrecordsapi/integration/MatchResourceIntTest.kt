package uk.gov.justice.digital.hmpps.learnerrecordsapi.integration

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.never
import org.mockito.kotlin.reset
import org.mockito.kotlin.spy
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.WebTestClient
import software.amazon.awssdk.services.sqs.model.PurgeQueueRequest
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.learnerrecordsapi.config.AuditEvent.MATCH_LE
import uk.gov.justice.digital.hmpps.learnerrecordsapi.config.HmppsBoldLrsExceptionHandler
import uk.gov.justice.digital.hmpps.learnerrecordsapi.config.Roles.ROLE_LEARNERS_RO
import uk.gov.justice.digital.hmpps.learnerrecordsapi.config.Roles.ROLE_LEARNERS_UI
import uk.gov.justice.digital.hmpps.learnerrecordsapi.integration.wiremock.LRSApiExtension.Companion.lrsApiMock
import uk.gov.justice.digital.hmpps.learnerrecordsapi.models.MatchStatus
import uk.gov.justice.digital.hmpps.learnerrecordsapi.models.db.MatchEntity
import uk.gov.justice.digital.hmpps.learnerrecordsapi.models.lrsapi.response.LearningEvent
import uk.gov.justice.digital.hmpps.learnerrecordsapi.models.request.ConfirmMatchRequest
import uk.gov.justice.digital.hmpps.learnerrecordsapi.models.request.ConfirmNoMatchRequest
import uk.gov.justice.digital.hmpps.learnerrecordsapi.models.request.LearnerEventsRequest
import uk.gov.justice.digital.hmpps.learnerrecordsapi.models.request.MatchType
import uk.gov.justice.digital.hmpps.learnerrecordsapi.models.response.CheckMatchResponse
import uk.gov.justice.digital.hmpps.learnerrecordsapi.models.response.CheckMatchStatus
import uk.gov.justice.digital.hmpps.learnerrecordsapi.models.response.LRSResponseType
import uk.gov.justice.digital.hmpps.learnerrecordsapi.models.response.LearnerEventsResponse
import uk.gov.justice.digital.hmpps.learnerrecordsapi.repository.MatchRepository
import uk.gov.justice.digital.hmpps.learnerrecordsapi.service.MatchService
import uk.gov.justice.hmpps.sqs.HmppsQueueService
import uk.gov.justice.hmpps.sqs.MissingQueueException
import uk.gov.justice.hmpps.sqs.audit.HmppsAuditEvent
import java.time.Instant

@TestConfiguration
class MockitoSpyConfig {
  @Autowired
  lateinit var matchRepository: MatchRepository

  @Bean
  fun matchService(): MatchService = spy(MatchService(matchRepository))
}

@Import(MockitoSpyConfig::class)
class MatchResourceIntTest : IntegrationTestBase() {

  @Autowired
  protected lateinit var jsonMapper: JsonMapper

  @Autowired
  lateinit var matchRepository: MatchRepository

  @Autowired
  protected lateinit var matchService: MatchService

  @Autowired
  protected lateinit var hmppsQueueService: HmppsQueueService

  private val auditQueue by lazy {
    hmppsQueueService.findByQueueId("audit") ?: throw MissingQueueException("HmppsQueue audit not found")
  }
  protected val auditSqsClient by lazy { auditQueue.sqsClient }
  protected val auditQueueUrl by lazy { auditQueue.queueUrl }

  val nomisId = "1234567"
  val matchedUln = "A"
  val givenName = "Test"
  val familyName = "Tester"

  private fun checkGetWebCall(
    nomisId: String,
    expectedResponseStatus: Int,
    expectedStatus: CheckMatchStatus,
    expectedUln: String? = null,
    expectedGivenName: String? = null,
    expectedFamilyName: String? = null,
  ) {
    listOf(ROLE_LEARNERS_RO, ROLE_LEARNERS_UI).forEach { role ->
      val executedRequest = webTestClient.get()
        .uri("/match/$nomisId")
        .headers(setAuthorisation(roles = listOf(role)))
        .header("X-Username", "TestUser")
        .accept(MediaType.parseMediaType("application/json"))
        .exchange()
        .expectStatus()

      val checkMatchResponse = jsonMapper.readValue(
        executedRequest
          .isEqualTo(expectedResponseStatus)
          .expectBody()
          .returnResult()
          .responseBody?.toString(Charsets.UTF_8),
        CheckMatchResponse::class.java,
      )
      assertThat(checkMatchResponse.status).isEqualTo(expectedStatus)
      if (expectedUln != null) {
        assertThat(checkMatchResponse.matchedUln).isEqualTo(expectedUln)
        assertThat(checkMatchResponse.givenName).isEqualTo(expectedGivenName)
        assertThat(checkMatchResponse.familyName).isEqualTo(expectedFamilyName)
      }
    }
  }

  private fun postMatch(nomisId: String, uln: String, expectedStatus: Int): WebTestClient.ResponseSpec = webTestClient.post()
    .uri("/match/$nomisId")
    .headers(setAuthorisation(roles = listOf(ROLE_LEARNERS_UI)))
    .header("X-Username", "TestUser")
    .bodyValue(ConfirmMatchRequest(uln, givenName, familyName, MatchType.EXACT_MATCH, "1"))
    .accept(MediaType.parseMediaType("application/json"))
    .exchange()
    .expectStatus()
    .isEqualTo(expectedStatus)

  @AfterEach
  fun cleanup() {
    matchRepository.deleteAll()
    reset(matchService)
  }

  @Test
  fun `GET match should find a match by id`() {
    matchRepository.save(
      MatchEntity(
        null,
        nomisId,
        matchedUln,
        givenName,
        familyName,
        "EXACT_MATCH",
        "1",
        MatchStatus.MATCHED.toString(),
      ),
    )

    checkGetWebCall(
      nomisId,
      200,
      CheckMatchStatus.Found,
      matchedUln,
      givenName,
      familyName,
    )
  }

  @Test
  fun `GET match should return NOT_FOUND if no match`() {
    checkGetWebCall(
      nomisId,
      404,
      CheckMatchStatus.NotFound,
    )
  }

  @Test
  fun `GET match should return NOT_FOUND record marked as such`() {
    matchRepository.save(MatchEntity(null, nomisId, "", "Sample", "Tester", "EXACT_MATCH", "1", MatchStatus.UNMATCHED.toString()))
    checkGetWebCall(
      nomisId,
      404,
      CheckMatchStatus.NotFound,
    )
  }

  @Test
  fun `GET match should return no match if record marked as such`() {
    matchRepository.save(MatchEntity(null, nomisId, "", "Sample", "Tester", "EXACT_MATCH", "1", MatchStatus.MATCH_NOT_POSSIBLE.toString()))
    checkGetWebCall(
      nomisId,
      200,
      CheckMatchStatus.NoMatch,
    )
  }

  @Test
  fun `POST to confirm match should return 201 CREATED with a response confirming a match`() {
    val (nomisId, uln) = arrayOf("1234567", "1234567890")
    val actualResponse = postMatch(nomisId, uln, 201)
    verify(matchService, times(1)).isUnmatched(any(), any())
    verify(matchService, times(1)).saveMatch(any(), any())
    actualResponse.expectStatus().isCreated
    checkSavedUlnAndStatus(nomisId, uln, MatchStatus.MATCHED)
  }

  @Test
  fun `POST to confirm match should return 409 CONFLICT if ULN already used`() {
    val (nomisId, uln) = arrayOf("1234567", "1234567890")
    matchRepository.save(
      MatchEntity(
        nomisId = "7654321",
        matchedUln = uln,
      ),
    )
    postMatch(nomisId, uln, 409)
    verify(matchService, times(1)).isUnmatched(any(), any())
    verify(matchService, times(0)).saveMatch(any(), any())
  }

  @Test
  fun `POST to confirm match should return 400 if ULN is malformed`() {
    val (nomisId, uln) = arrayOf("1234567", "1234567890abcdef")
    val actualResponse = jsonMapper.readValue(
      postMatch(nomisId, uln, 400).expectBody().returnResult().responseBody,
      HmppsBoldLrsExceptionHandler.ErrorResponse::class.java,
    )

    val expectedError = HmppsBoldLrsExceptionHandler.ErrorResponse(
      HttpStatus.BAD_REQUEST,
      errorCode = "Validation Failed",
      userMessage = "Please correct the error and retry",
      developerMessage = "Validation(s) failed for [matchingUln]",
      moreInfo = "Validation(s) failed for [matchingUln] with reason(s): [must match \"^[0-9]{1,10}\$\"]",
    )

    verify(matchService, never()).saveMatch(any(), any())
    assertThat(actualResponse).isEqualTo(expectedError)
  }

  @Test
  fun `POST to confirm match should return 500 if match service fails to save`() {
    val (nomisId, uln) = arrayOf("1234567", "1234567890")
    doThrow(RuntimeException("Database error")).`when`(matchService).saveMatch(any(), any())
    val actualResponse = jsonMapper.readValue(
      postMatch(nomisId, uln, 500).expectBody().returnResult().responseBody,
      HmppsBoldLrsExceptionHandler.ErrorResponse::class.java,
    )

    val expectedError = HmppsBoldLrsExceptionHandler.ErrorResponse(
      HttpStatus.INTERNAL_SERVER_ERROR,
      errorCode = "Unexpected error",
      userMessage = "Unexpected error: Database error",
      developerMessage = "Unexpected error: Database error",
      moreInfo = "Unexpected error",
    )

    verify(matchService, times(1)).saveMatch(any(), any())
    assertThat(actualResponse).isEqualTo(expectedError)
  }

  @Test
  fun `should emit an event that request is received for find Learning Events by Nomis ID `() {
    lrsApiMock.stubLearningEventsLinkedMatchFull()

    auditSqsClient.purgeQueue(
      PurgeQueueRequest.builder()
        .queueUrl(auditQueueUrl)
        .build(),
    )

    matchRepository.save(
      MatchEntity(
        null,
        "123456",
        "1234567890",
        "Some Given Name",
        "Some Family Name",
        "EXACT_MATCH",
        "1",
        "",
        null,
      ),
    )

    webTestClient.get()
      .uri("/match/{nomisId}/learner-events", "123456")
      .headers(setAuthorisation(roles = listOf(ROLE_LEARNERS_RO)))
      .header("X-Username", "TestUser")
      .accept(MediaType.parseMediaType("application/json"))
      .exchange()
      .expectStatus()
      .is2xxSuccessful
      .expectBody()
      .returnResult()
      .responseBody

    val receivedEvent = jsonMapper.readValue(
      auditSqsClient.receiveMessage(
        ReceiveMessageRequest.builder().queueUrl(auditQueueUrl).build(),
      ).get().messages()[0].body(),
      HmppsAuditEvent::class.java,
    )

    assertThat(receivedEvent.what).isEqualTo(MATCH_LE)
    assertThat(receivedEvent.who).isEqualTo("TestUser")
    assertThat(receivedEvent.service).isEqualTo("hmpps-learner-records-api")
    assertThat(receivedEvent.`when`).isBeforeOrEqualTo(Instant.now())
  }

  @Test
  fun `should return Found if the Given Nomis ID does match or exists and return Learning Events`() {
    lrsApiMock.stubLearningEventsExactMatchFull()

    matchRepository.save(
      MatchEntity(
        null,
        "123456",
        "1234567890",
        "Some Given Name",
        "Some Family Name",
        "EXACT_MATCH",
        "1",
        "",
        null,
      ),
    )

    val learningEventRequest = LearnerEventsRequest(
      "Some Given Name",
      "Some Family Name",
      "1234567890",
    )

    val expectedResponse = LearnerEventsResponse(
      learningEventRequest,
      LRSResponseType.EXACT_MATCH,
      "1234567890",
      "1234567890",
      listOf(
        LearningEvent(
          id = "28538264",
          achievementProviderUkprn = "90000051",
          achievementProviderName = "TEST90000051",
          awardingOrganisationName = "Test Provider Ltd",
          qualificationType = "",
          subjectCode = "K/501/5773",
          achievementAwardDate = "2010-01-01",
          credits = "2",
          source = "QCFU",
          dateLoaded = "2014-05-21 14:49:01",
          underDataChallenge = "N",
          level = "Entry Level",
          status = "F",
          subject = "Introduction to Construction Work: Entry 3",
          grade = "Pass",
          awardingOrganisationUkprn = "90000051",
        ),
      ),
    )

    val actualResponse = jsonMapper.readValue(
      webTestClient.get()
        .uri("/match/{nomisId}/learner-events", "123456")
        .headers(setAuthorisation(roles = listOf(ROLE_LEARNERS_RO)))
        .header("X-Username", "TestUser")
        .exchange()
        .expectStatus()
        .isOk
        .expectBody()
        .returnResult()
        .responseBody,
      LearnerEventsResponse::class.java,
    )

    assertThat(actualResponse).usingRecursiveComparison().isEqualTo(expectedResponse)
  }

  @Test
  fun `should return Not Found if the Given Nomis ID does not exist`() {
    val expectedResponse = HmppsBoldLrsExceptionHandler.ErrorResponse(
      status = HttpStatus.NOT_FOUND,
      errorCode = "Match not found",
      userMessage = "No Match found for given NomisId 123456",
      developerMessage = "Individual with this NomisId has not been matched to a ULN yet",
      moreInfo = "Individual with this NomisId has not been matched to a ULN yet",
    )

    val actualResponse = jsonMapper.readValue(
      webTestClient.get()
        .uri("/match/{nomisId}/learner-events", "123456")
        .headers(setAuthorisation(roles = listOf(ROLE_LEARNERS_RO)))
        .header("X-Username", "TestUser")
        .accept(MediaType.parseMediaType("application/json"))
        .exchange()
        .expectStatus()
        .isNotFound
        .expectBody()
        .returnResult()
        .responseBody,
      HmppsBoldLrsExceptionHandler.ErrorResponse::class.java,
    )
    assertThat(actualResponse).isEqualTo(expectedResponse)
  }

  @Test
  fun `should return No Content if the Given Nomis ID can't be matched`() {
    val expectedResponse = HmppsBoldLrsExceptionHandler.ErrorResponse(
      status = HttpStatus.BAD_REQUEST,
      errorCode = "Match not possible",
      userMessage = "Not possible to match given NomisId 456789",
      developerMessage = "Individual with this NomisId does not have a ULN",
      moreInfo = "Individual with this NomisId does not have a ULN",
    )

    matchRepository.save(
      MatchEntity(
        null,
        "456789",
        "",
        "",
        "",
        "NO_MATCH_RETURNED_FROM_LRS",
        "0",
        MatchStatus.MATCH_NOT_POSSIBLE.toString(),
      ),
    )

    val actualResponse = jsonMapper.readValue(
      webTestClient.get()
        .uri("/match/{nomisId}/learner-events", "456789")
        .headers(setAuthorisation(roles = listOf(ROLE_LEARNERS_RO)))
        .header("X-Username", "TestUser")
        .accept(MediaType.parseMediaType("application/json"))
        .exchange()
        .expectStatus()
        .isBadRequest
        .expectBody()
        .returnResult()
        .responseBody,
      HmppsBoldLrsExceptionHandler.ErrorResponse::class.java,
    )
    assertThat(actualResponse).isEqualTo(expectedResponse)
  }

  private fun postNoMatch(nomisId: String, expectedStatus: Int): WebTestClient.ResponseSpec = webTestClient.post()
    .uri("/match/$nomisId/no-match")
    .headers(setAuthorisation(roles = listOf(ROLE_LEARNERS_UI)))
    .header("X-Username", "TestUser")
    .bodyValue(ConfirmNoMatchRequest(MatchType.NO_MATCH_RETURNED_FROM_LRS, "0"))
    .accept(MediaType.parseMediaType("application/json"))
    .exchange()
    .expectStatus()
    .isEqualTo(expectedStatus)

  private fun checkSavedUlnAndStatus(nomisId: String, expected: String?, status: MatchStatus) {
    val entity = matchRepository.findFirstByNomisIdOrderByIdDesc(nomisId)
    if (expected != null) {
      assertThat(entity?.matchedUln).isEqualTo(expected)
    }
    assertThat(entity?.matchStatus ?: MatchStatus.UNMATCHED.toString()).isEqualTo(status.toString())
  }

  @Test
  fun `POST to confirm no match should return 201 CREATED with a response confirming a match`() {
    val nomisId = "A1417AE"
    val actualResponse = postNoMatch(nomisId, 201)
    verify(matchService, times(1)).saveNoMatch(any(), any())
    actualResponse.expectStatus().isCreated
    checkSavedUlnAndStatus(nomisId, null, MatchStatus.MATCH_NOT_POSSIBLE)
  }

  private fun postUnmatch(nomisId: String, expectedStatus: Int): WebTestClient.ResponseSpec = webTestClient.post()
    .uri("/match/$nomisId/unmatch")
    .headers(setAuthorisation(roles = listOf(ROLE_LEARNERS_UI)))
    .header("X-Username", "TestUser")
    .accept(MediaType.parseMediaType("application/json"))
    .exchange()
    .expectStatus()
    .isEqualTo(expectedStatus)

  @Test
  fun `POST to confirm un-match should return 201 CREATED with a response confirming a match`() {
    val nomisId = "1234567"
    val actualResponse = postUnmatch(nomisId, 201)
    verify(matchService, times(1)).unMatch(any())
    actualResponse.expectStatus().isCreated
    checkSavedUlnAndStatus(nomisId, null, MatchStatus.UNMATCHED)
  }
}
