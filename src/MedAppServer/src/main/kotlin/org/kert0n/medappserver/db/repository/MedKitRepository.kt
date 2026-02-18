package org.kert0n.medappserver.db.repository

import org.kert0n.medappserver.db.model.MedKit
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

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
}