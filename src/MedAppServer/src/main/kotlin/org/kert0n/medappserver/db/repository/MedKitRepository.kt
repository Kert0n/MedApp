package org.kert0n.medappserver.db.repository

import org.kert0n.medappserver.db.model.MedKit
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.*

interface MedKitRepository: JpaRepository<MedKit, UUID> {
    
    // JPQL - explicit join condition
    @Query("""
        SELECT mk FROM MedKit mk
        JOIN mk.users u
        WHERE u.id = :userId
    """)
    fun findByUsersId(@Param("userId") userId: UUID): List<MedKit>

    // JPQL with fetch for eager loading drugs
    @Query("""
        SELECT mk FROM MedKit mk
        LEFT JOIN FETCH mk.drugs
        WHERE mk.id = :id
    """)
    fun findByIdWithDrugs(@Param("id") id: UUID): MedKit?

    // JPQL with fetch for eager loading users
    @Query("""
        SELECT mk FROM MedKit mk
        LEFT JOIN FETCH mk.users
        WHERE mk.id = :id
    """)
    fun findByIdWithUsers(@Param("id") id: UUID): MedKit?
    fun findByIdAndUsers(id: UUID, userId: UUID): MedKit?
}