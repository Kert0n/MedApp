package org.kert0n.medappserver.db.repository

import org.kert0n.medappserver.db.model.MedKit
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.*

interface MedKitRepository: JpaRepository<MedKit, UUID> {
    
    @Query("""
        SELECT DISTINCT mk FROM MedKit mk
        JOIN mk.users u
        WHERE u.id = :userId
    """)
    fun findByUsersId(@Param("userId") userId: UUID): List<MedKit>

    @Query("""
        SELECT DISTINCT mk FROM MedKit mk
        LEFT JOIN FETCH mk.drugs
        WHERE mk.id = :id
    """)
    fun findByIdWithDrugs(@Param("id") id: UUID): MedKit?

    @Query("""
        SELECT DISTINCT mk FROM MedKit mk
        LEFT JOIN FETCH mk.users
        WHERE mk.id = :id
    """)
    fun findByIdWithUsers(@Param("id") id: UUID): MedKit?

    @Query("""
        SELECT mk FROM MedKit mk
        JOIN mk.users u
        WHERE mk.id = :medKitId AND u.id = :userId
    """)
    fun findByIdAndUsers(@Param("medKitId") medKitId: UUID, @Param("userId") userId: UUID): MedKit?

    interface MedKitSummary {
        val id: UUID
        val userCount: Long
        val drugCount: Long
    }

    @Query("""
        SELECT mk.id AS id,
               COUNT(DISTINCT mu.id) AS userCount,
               COUNT(DISTINCT d.id) AS drugCount
        FROM MedKit mk
        JOIN mk.users u
        LEFT JOIN mk.users mu
        LEFT JOIN mk.drugs d
        WHERE u.id = :userId
        GROUP BY mk.id
    """)
    fun findSummariesByUserId(@Param("userId") userId: UUID): List<MedKitSummary>
}
