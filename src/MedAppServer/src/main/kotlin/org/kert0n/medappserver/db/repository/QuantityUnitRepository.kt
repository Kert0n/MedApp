package org.kert0n.medappserver.db.repository

import org.kert0n.medappserver.db.model.parsed.QuantityUnit
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface QuantityUnitRepository : JpaRepository<QuantityUnit, UUID> {
    fun findByName(name: String): QuantityUnit?
}
