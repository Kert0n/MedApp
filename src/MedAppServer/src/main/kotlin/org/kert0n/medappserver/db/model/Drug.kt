package org.kert0n.medappserver.db.model

import java.util.*

class Drug (
    var id: UUID = UUID.randomUUID(),
    var name: String,
    var quantity: Double,
    var quantityUnit: String,
    var formType: String? = null,
    var category: String? = null,
    var manufacturer: String? = null,
    var country: String? = null,
    var description: String? = null,
    var medKitId: UUID
){

    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Drug

        return id == other.id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }
}

