package org.kert0n.medappserver.db.model.parsed

import java.util.*

class VidalDrug(
    var id: UUID = UUID.randomUUID(),
    var name: String,
    var formType: FormType? = null,
    var quantity: Int? = null,
    var quantityUnit: QuantityUnit? = null,
    var activeSubstance: String? = null,
    var category: String? = null,
    var manufacturer: String,
    var country: String? = null,
    var description: String? = null,
    var otc: Boolean
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as VidalDrug

        return id == other.id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }
}