package org.kert0n.medappserver.db.repository

import org.kert0n.medappserver.db.model.Using
import org.kert0n.medappserver.db.model.UsingKey
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.*

interface UsingRepository: JpaRepository<Using, UsingKey> {
    
    // JPQL for explicit queries
    @EntityGraph(attributePaths = ["user", "drug"])
    @Query("""
        SELECT u FROM Using u
        WHERE u.user.id = :userId
    """)
    fun findAllByUserId(@Param("userId") userId: UUID): List<Using>
    
    @Query("""
        SELECT u FROM Using u
        WHERE u.drug.id = :drugId
    """)
    fun findAllByDrugId(@Param("drugId") drugId: UUID): List<Using>
    
    @EntityGraph(attributePaths = ["user", "drug"])
    @Query("""
        SELECT u FROM Using u
        WHERE u.user.id = :userId AND u.drug.id = :drugId
    """)
    fun findByUserIdAndDrugId(@Param("userId") userId: UUID, @Param("drugId") drugId: UUID): Using?
    
    // JPQL with fetch for eager loading
    @Modifying
    @Query("""
        DELETE FROM Using u
        WHERE u.user.id = :userId AND u.drug.medKit.id = :medKitId
    """)
    fun deleteByUserIdAndMedKitId(@Param("userId") userId: UUID, @Param("medKitId") medKitId: UUID): Int
}
