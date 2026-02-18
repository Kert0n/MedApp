package org.kert0n.medappserver.db.repository

import org.kert0n.medappserver.db.model.User
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface UserRepository: JpaRepository<User, UUID> {
    
    // Using derived query methods
    fun findByMedKitsId(medId: UUID): List<User>
    
    fun findByUsingsDrugId(drugId: UUID): List<User>
    
    // Using EntityGraph for eager fetch
    @EntityGraph(attributePaths = ["medKits"])
    override fun findById(id: UUID): java.util.Optional<User>
}