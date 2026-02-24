package org.kert0n.medappserver.db.model.parsed

import java.util.*

class QuantityUnit(
    var id: UUID = UUID.randomUUID(),
    var name: String
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as QuantityUnit

        return id == other.id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }
}