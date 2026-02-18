package org.kert0n.medappserver.db.repository

import org.kert0n.medappserver.db.model.MedKit
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface MedKitRepository: JpaRepository<MedKit, UUID> {
    
    // Using derived query method
    fun findByUsersId(userId: UUID): List<MedKit>
    
    // Using EntityGraph for eager loading
    @EntityGraph(attributePaths = ["drugs"])
    override fun findById(id: UUID): java.util.Optional<MedKit>
    
    @EntityGraph(attributePaths = ["users"])
    fun findWithUsersById(id: UUID): MedKit?
}