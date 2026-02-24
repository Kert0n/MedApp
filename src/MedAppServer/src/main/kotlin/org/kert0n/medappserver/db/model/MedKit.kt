package org.kert0n.medappserver.db.model

import java.util.*


class MedKit(
    var id: UUID = UUID.randomUUID()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MedKit

        return id == other.id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }
}

