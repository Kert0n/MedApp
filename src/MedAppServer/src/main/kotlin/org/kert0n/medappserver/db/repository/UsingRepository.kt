package org.kert0n.medappserver.db.repository

import org.kert0n.medappserver.db.model.Using
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface UsingRepository: JpaRepository<Using, UUID> {
    fun findAllByUserId(userId: UUID): List<Using>
    fun findAllByDrugId(drugId: UUID): List<Using>

}