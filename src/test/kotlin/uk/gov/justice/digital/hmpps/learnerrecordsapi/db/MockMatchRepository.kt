package uk.gov.justice.digital.hmpps.learnerrecordsapi.db

import org.springframework.data.domain.Example
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.repository.query.FluentQuery
import uk.gov.justice.digital.hmpps.learnerrecordsapi.models.db.MatchEntity
import uk.gov.justice.digital.hmpps.learnerrecordsapi.repository.MatchRepository
import java.time.LocalDateTime
import java.util.*
import java.util.function.Function

class MockMatchRepository(val entities: List<MatchEntity>) : MatchRepository {

  override fun findFirstByNomisIdOrderByIdDesc(nomisId: String): MatchEntity? = entities
    .filter { it.nomisId == nomisId }
    .maxByOrNull { it.id!! }

  override fun findAllNomisIdsExcept(nomisId: String): Set<String> = entities
    .map { it.nomisId }
    .filter { it != nomisId }
    .toSet()

  override fun existsMatchWithDifferentId(nomisId: String, uln: String): Boolean = entities
    .map { it.nomisId }
    .filter { it != nomisId }
    .any { otherNomisId ->
      entities
        .filter { it.nomisId == otherNomisId }
        .maxByOrNull { it.id!! }
        ?.matchedUln == uln
    }

  override fun findForSubjectAccessRequest(
    nomisId: String,
    fromDate: LocalDateTime?,
    toDate: LocalDateTime?,
  ): List<MatchEntity> {
    TODO("Not yet implemented")
  }

  override fun <S : MatchEntity> save(entity: S): S {
    TODO("Not yet implemented")
  }

  override fun <S : MatchEntity> saveAll(entities: MutableIterable<S>): MutableList<S> {
    TODO("Not yet implemented")
  }

  override fun <S : MatchEntity> findAll(example: Example<S>): MutableList<S> {
    TODO("Not yet implemented")
  }

  override fun <S : MatchEntity> findAll(example: Example<S>, sort: Sort): MutableList<S> {
    TODO("Not yet implemented")
  }

  override fun findAll(): MutableList<MatchEntity> {
    TODO("Not yet implemented")
  }

  override fun findAll(sort: Sort): MutableList<MatchEntity> {
    TODO("Not yet implemented")
  }

  override fun findAll(pageable: Pageable): Page<MatchEntity> {
    TODO("Not yet implemented")
  }

  override fun <S : MatchEntity> findAll(example: Example<S>, pageable: Pageable): Page<S> {
    TODO("Not yet implemented")
  }

  override fun findAllById(ids: MutableIterable<Long>): MutableList<MatchEntity> {
    TODO("Not yet implemented")
  }

  override fun count(): Long {
    TODO("Not yet implemented")
  }

  override fun <S : MatchEntity> count(example: Example<S>): Long {
    TODO("Not yet implemented")
  }

  override fun delete(entity: MatchEntity) {
    TODO("Not yet implemented")
  }

  override fun deleteAllById(ids: MutableIterable<Long>) {
    TODO("Not yet implemented")
  }

  override fun deleteAll(entities: MutableIterable<MatchEntity>) {
    TODO("Not yet implemented")
  }

  override fun deleteAll() {
    TODO("Not yet implemented")
  }

  override fun <S : MatchEntity> findOne(example: Example<S>): Optional<S> {
    TODO("Not yet implemented")
  }

  override fun <S : MatchEntity> exists(example: Example<S>): Boolean {
    TODO("Not yet implemented")
  }

  override fun <S : MatchEntity, R : Any?> findBy(
    example: Example<S>,
    queryFunction: Function<FluentQuery.FetchableFluentQuery<S>, R>,
  ): R {
    TODO("Not yet implemented")
  }

  override fun flush() {
    TODO("Not yet implemented")
  }

  override fun <S : MatchEntity> saveAndFlush(entity: S): S {
    TODO("Not yet implemented")
  }

  override fun <S : MatchEntity> saveAllAndFlush(entities: MutableIterable<S>): MutableList<S> {
    TODO("Not yet implemented")
  }

  override fun deleteAllInBatch(entities: MutableIterable<MatchEntity>) {
    TODO("Not yet implemented")
  }

  override fun deleteAllInBatch() {
    TODO("Not yet implemented")
  }

  override fun deleteAllByIdInBatch(ids: MutableIterable<Long>) {
    TODO("Not yet implemented")
  }

  override fun getReferenceById(id: Long): MatchEntity {
    TODO("Not yet implemented")
  }

  @Deprecated("Deprecated in Java")
  override fun getById(id: Long): MatchEntity {
    TODO("Not yet implemented")
  }

  @Deprecated("Deprecated in Java")
  override fun getOne(id: Long): MatchEntity {
    TODO("Not yet implemented")
  }

  override fun deleteById(id: Long) {
    TODO("Not yet implemented")
  }

  override fun existsById(id: Long): Boolean {
    TODO("Not yet implemented")
  }

  override fun findById(id: Long): Optional<MatchEntity> {
    TODO("Not yet implemented")
  }
}
