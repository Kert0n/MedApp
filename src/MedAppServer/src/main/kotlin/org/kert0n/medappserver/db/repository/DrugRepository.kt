@file:Suppress("FunctionName")

package org.kert0n.medappserver.db.repository

import org.kert0n.medappserver.db.model.Drug
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.*

interface DrugRepository: JpaRepository<Drug, UUID> {

    fun findAllByMedKitId(medKitId: UUID): List<Drug>

    @Query("""
        SELECT DISTINCT d FROM Drug d 
        LEFT JOIN FETCH d.usings u
        LEFT JOIN FETCH u.user
        WHERE u.user.id = :userId
    """)
    fun findByUsingsUserId(@Param("userId") userId: UUID): List<Drug>

    @Query("""
        SELECT d FROM Drug d 
        JOIN d.medKit mk
        JOIN mk.users u
        WHERE d.id = :drugId AND u.id = :userId
    """)
    fun findByIdAndMedKitUsersId(@Param("drugId") drugId: UUID, @Param("userId") userId: UUID): Drug?
    
    // EntityGraph for simple eager loading
    @EntityGraph(attributePaths = ["usings"])
    override fun findById(id: UUID): java.util.Optional<Drug>

    fun findQuantity(id: UUID): Double

    fun findPlannedAndActualQuantity(id:UUID):Pair<Double, Double>

}