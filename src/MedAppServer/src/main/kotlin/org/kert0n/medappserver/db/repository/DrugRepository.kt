@file:Suppress("FunctionName")

package org.kert0n.medappserver.db.repository

import org.kert0n.medappserver.db.model.Drug
import org.kert0n.medappserver.db.model.Using
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface DrugRepository: JpaRepository<Drug, UUID> {
    fun findAllByMedKitId(medKitId: UUID): List<Drug>
    fun findByUsingsUserId(userId: UUID): List<Drug>

    fun findByIdAndUsingsUserId(drugId: UUID,userId:UUID): Drug?
}