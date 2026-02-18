package org.kert0n.medappserver.db.repository

import org.kert0n.medappserver.db.model.Using
import org.kert0n.medappserver.db.model.UsingKey
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface UsingRepository: JpaRepository<Using, UsingKey> {
    
    /**
     * Find all usings for a specific user
     */
    @EntityGraph(attributePaths = ["user", "drug"])
    fun findAllByUserId(userId: UUID): List<Using>
    
    /**
     * Find all usings for a specific drug
     */
    @EntityGraph(attributePaths = ["user", "drug"])
    fun findAllByDrugId(drugId: UUID): List<Using>
    
    /**
     * Find a using by user ID and drug ID
     */
    @EntityGraph(attributePaths = ["user", "drug"])
    fun findByUserIdAndDrugId(userId: UUID, drugId: UUID): Using?
    
    /**
     * Delete all usings for a specific drug
     */
    @Modifying
    @Query("DELETE FROM Using u WHERE u.drug.id = :drugId")
    fun deleteAllByDrugId(@Param("drugId") drugId: UUID)
    
    /**
     * Delete a using by user ID and drug ID
     */
    @Modifying
    @Query("DELETE FROM Using u WHERE u.user.id = :userId AND u.drug.id = :drugId")
    fun deleteByUserIdAndDrugId(@Param("userId") userId: UUID, @Param("drugId") drugId: UUID)
}