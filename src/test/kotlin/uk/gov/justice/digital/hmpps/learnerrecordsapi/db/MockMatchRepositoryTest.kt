package uk.gov.justice.digital.hmpps.learnerrecordsapi.db

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.learnerrecordsapi.models.db.MatchEntity
import java.time.LocalDateTime

class MockMatchRepositoryTest {

  private val e1 = MatchEntity(
    nomisId = "A",
    matchedUln = "1",
    dateCreated = LocalDateTime.now(),
    id = 1,
  )

  private val e2 = MatchEntity(
    nomisId = "A",
    matchedUln = "",
    dateCreated = LocalDateTime.now().plusSeconds(1),
    id = 2,
  )

  private val e3 = MatchEntity(
    nomisId = "B",
    matchedUln = "2",
    dateCreated = LocalDateTime.now(),
    id = 3,
  )

  private val repository = MockMatchRepository(listOf(e1, e2, e3))

  @Test
  fun `should return latest match`() {
    assertThat(repository.findFirstByNomisIdOrderByIdDesc("A")).isEqualTo(e2)
    assertThat(repository.findFirstByNomisIdOrderByIdDesc("B")).isEqualTo(e3)
  }

  @Test
  fun `should return all distinct nomisIds`() {
    assertThat(repository.findAllNomisIdsExcept("")).isEqualTo(setOf("A", "B"))
    assertThat(repository.findAllNomisIdsExcept("A")).isEqualTo(setOf("B"))
    assertThat(repository.findAllNomisIdsExcept("B")).isEqualTo(setOf("A"))
  }
}
