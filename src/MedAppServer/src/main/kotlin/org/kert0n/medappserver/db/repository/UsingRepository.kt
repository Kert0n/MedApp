package org.kert0n.medappserver.db.repository

import org.kert0n.medappserver.db.model.Using
import org.kert0n.medappserver.db.model.UsingKey
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface UsingRepository: JpaRepository<Using, UsingKey> {
    
    /**
     * Find all usings for a specific user
     */
    fun findAllByUserId(userId: UUID): List<Using>
    
    /**
     * Find all usings for a specific drug
     */
    fun findAllByDrugId(drugId: UUID): List<Using>
    
    /**
     * Find a using by user ID and drug ID
     */
    fun findByUserIdAndDrugId(userId: UUID, drugId: UUID): Using?
    
    /**
     * Delete all usings for a specific drug
     */
    fun deleteAllByDrugId(drugId: UUID)
    
    /**
     * Delete a using by user ID and drug ID
     */
    fun deleteByUserIdAndDrugId(userId: UUID, drugId: UUID)
}