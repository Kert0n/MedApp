@file:Suppress("FunctionName")

package org.kert0n.medappserver.db.repository

import org.kert0n.medappserver.db.model.Drug
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.*

interface DrugRepository: JpaRepository<Drug, UUID> {

    @Query("SELECT d FROM Drug d WHERE d.medKit.id = :medKitId")
    fun findAllByMedKitId(@Param("medKitId") medKitId: UUID): List<Drug>

    @Query("""
        SELECT DISTINCT d FROM Drug d 
        JOIN d.usings u
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

    @Query("SELECT COALESCE(SUM(u.plannedAmount), 0.0) FROM Using u WHERE u.drug.id = :drugId")
    fun sumPlannedAmount(@Param("drugId") drugId: UUID): Double

}