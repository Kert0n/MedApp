@file:Suppress("FunctionName")

package org.kert0n.medappserver.db.repository

import org.kert0n.medappserver.db.model.Drug
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface DrugRepository: JpaRepository<Drug, UUID> {
    
    /**
     * Find all drugs in a specific medicine kit
     */
    @EntityGraph(attributePaths = ["medKit", "usings"])
    fun findAllByMedKitId(medKitId: UUID): List<Drug>
    
    /**
     * Find all drugs that have usings by a specific user
     */
    @EntityGraph(attributePaths = ["medKit", "usings"])
    fun findByUsingsUserId(userId: UUID): List<Drug>
    
    /**
     * Find a drug by ID only if the user has access to it through the medicine kit
     */
    @EntityGraph(attributePaths = ["medKit", "usings"])
    @Query("""
        SELECT d FROM Drug d 
        JOIN d.medKit mk 
        JOIN mk.users u 
        WHERE d.id = :drugId AND u.id = :userId
    """)
    fun findByIdAndUserId(@Param("drugId") drugId: UUID, @Param("userId") userId: UUID): Drug?
    
    /**
     * Find a drug by ID only if the user has usings for it
     */
    @EntityGraph(attributePaths = ["medKit", "usings"])
    fun findByIdAndUsingsUserId(drugId: UUID, userId: UUID): Drug?
    
    /**
     * Find all drugs accessible to a user through their medicine kits
     */
    @EntityGraph(attributePaths = ["medKit", "usings"])
    @Query("""
        SELECT d FROM Drug d 
        JOIN d.medKit mk 
        JOIN mk.users u 
        WHERE u.id = :userId
    """)
    fun findAllByUserId(@Param("userId") userId: UUID): List<Drug>
}