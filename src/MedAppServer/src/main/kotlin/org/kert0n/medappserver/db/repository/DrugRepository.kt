@file:Suppress("FunctionName")

package org.kert0n.medappserver.db.repository

import org.kert0n.medappserver.db.model.Drug
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface DrugRepository: JpaRepository<Drug, UUID> {
    
    // Using derived query method - JPA handles the query
    fun findAllByMedKitId(medKitId: UUID): List<Drug>
    
    // Using EntityGraph to fetch usings eagerly
    @EntityGraph(attributePaths = ["usings", "usings.user"])
    fun findByUsingsUserId(userId: UUID): List<Drug>

    @EntityGraph(attributePaths = ["usings", "usings.user"])
    fun findByIdAndUsingsUserId(drugId: UUID, userId: UUID): Drug?
    
    @EntityGraph(attributePaths = ["usings"])
    override fun findById(id: UUID): java.util.Optional<Drug>
    
    // For checking if user has access via medkit
    fun findByIdAndMedKitUsersId(drugId: UUID, userId: UUID): Drug?
}