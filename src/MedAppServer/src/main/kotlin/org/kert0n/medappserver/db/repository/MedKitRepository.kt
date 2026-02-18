package org.kert0n.medappserver.db.repository

import org.kert0n.medappserver.db.model.MedKit
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface MedKitRepository: JpaRepository<MedKit, UUID> {
    
    /**
     * Find all medicine kits that a user has access to
     */
    fun findByUsersId(userId: UUID): List<MedKit>
    
    /**
     * Find a medicine kit by ID only if the user has access to it
     */
    @Query("""
        SELECT mk FROM MedKit mk 
        JOIN mk.users u 
        WHERE mk.id = :medKitId AND u.id = :userId
    """)
    fun findByIdAndUserId(@Param("medKitId") medKitId: UUID, @Param("userId") userId: UUID): MedKit?
}