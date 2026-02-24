package org.kert0n.medappserver.db.model

import java.time.Instant
import java.util.*


class Using(
    var userId: UUID,
    var drugId: UUID,
    var plannedAmount: Double,
    var lastModified: Instant = Instant.now(),
    var createdAt: Instant = Instant.now()
) {
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Using

        return userId == other.userId && drugId == other.drugId
    }

    override fun hashCode(): Int {
        return Objects.hash(userId, drugId)
    }
}

