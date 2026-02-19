@file:Suppress("FunctionName")

package org.kert0n.medappserver.db.repository

import org.kert0n.medappserver.db.model.Drug
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.*

interface DrugRepository: JpaRepository<Drug, UUID> {

    @Query("""
        SELECT d FROM Drug d
        WHERE d.medKit.id = :medKitId
    """)
    fun findAllByMedKitId(@Param("medKitId") medKitId: UUID): List<Drug>

    @Query("""
        SELECT DISTINCT d FROM Drug d 
        JOIN d.usings u
        JOIN u.user usr
        WHERE usr.id = :userId
    """)
    fun findByUsingsUserId(@Param("userId") userId: UUID): List<Drug>

    @Query("""
        SELECT DISTINCT d FROM Drug d 
        JOIN d.medKit mk
        JOIN mk.users u
        LEFT JOIN FETCH d.usings
        WHERE d.id = :drugId AND u.id = :userId
    """)
    fun findByIdAndMedKitUsersId(@Param("drugId") drugId: UUID, @Param("userId") userId: UUID): Drug?
    
    @EntityGraph(attributePaths = ["usings"])
    override fun findById(id: UUID): java.util.Optional<Drug>

    @Query("""
        SELECT COALESCE(SUM(u.plannedAmount), 0)
        FROM Using u
        WHERE u.drug.id = :drugId
    """)
    fun findPlannedQuantityByDrugId(@Param("drugId") drugId: UUID): Double

    interface QuantitySummary {
        val actualQuantity: Double
        val plannedQuantity: Double
    }

    @Query("""
        SELECT d.quantity AS actualQuantity, COALESCE(SUM(u.plannedAmount), 0) AS plannedQuantity
        FROM Drug d
        LEFT JOIN d.usings u
        WHERE d.id = :drugId
        GROUP BY d.quantity
    """)
    fun findQuantitySummaryByDrugId(@Param("drugId") drugId: UUID): QuantitySummary?

    interface DrugWithPlannedQuantity {
        val drug: Drug
        val plannedQuantity: Double
    }

    @Query("""
        SELECT d AS drug, COALESCE(SUM(u.plannedAmount), 0) AS plannedQuantity
        FROM Drug d
        LEFT JOIN d.usings u
        WHERE d.medKit.id = :medKitId
        GROUP BY d
    """)
    fun findAllWithPlannedQuantityByMedKitId(@Param("medKitId") medKitId: UUID): List<DrugWithPlannedQuantity>
}
