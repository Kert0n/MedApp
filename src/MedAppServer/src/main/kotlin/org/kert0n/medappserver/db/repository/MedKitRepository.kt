package org.kert0n.medappserver.db.repository

import org.kert0n.medappserver.db.model.MedKit
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.*

interface MedKitRepository: JpaRepository<MedKit, UUID> {
    
    @Query("""
        SELECT mk FROM MedKit mk
        JOIN mk.users u
        WHERE u.id = :userId
    """)
    fun findByUsersId(@Param("userId") userId: UUID): List<MedKit>

    @Query("""
        SELECT mk FROM MedKit mk
        LEFT JOIN FETCH mk.drugs
        WHERE mk.id = :id
    """)
    fun findByIdWithDrugs(@Param("id") id: UUID): MedKit?

    @Query("""
        SELECT mk FROM MedKit mk
        LEFT JOIN FETCH mk.users
        WHERE mk.id = :id
    """)
    fun findByIdWithUsers(@Param("id") id: UUID): MedKit?

    @Query("""
        SELECT mk FROM MedKit mk
        JOIN mk.users u
        WHERE mk.id = :id AND u.id = :userId
    """)
    fun findByIdAndUserId(@Param("id") id: UUID, @Param("userId") userId: UUID): MedKit?

    @Query("""
        SELECT mk.id, SIZE(mk.users), SIZE(mk.drugs)
        FROM MedKit mk
        JOIN mk.users u
        WHERE u.id = :userId
        GROUP BY mk.id
    """)
    fun findMedKitSummariesByUserId(@Param("userId") userId: UUID): List<Array<Any>>
}