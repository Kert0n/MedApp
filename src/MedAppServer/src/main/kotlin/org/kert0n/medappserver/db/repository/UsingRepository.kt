package org.kert0n.medappserver.db.repository

import org.kert0n.medappserver.db.model.Using
import org.kert0n.medappserver.db.model.UsingKey
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface UsingRepository: JpaRepository<Using, UsingKey> {
    
    // Using derived query methods
    fun findAllByUserId(userId: UUID): List<Using>
    
    fun findAllByDrugId(drugId: UUID): List<Using>
    
    fun findByUserIdAndDrugId(userId: UUID, drugId: UUID): Using?
    
    // Using EntityGraph for eager loading
    @EntityGraph(attributePaths = ["drug"])
    fun findWithDrugByUserId(userId: UUID): List<Using>
}