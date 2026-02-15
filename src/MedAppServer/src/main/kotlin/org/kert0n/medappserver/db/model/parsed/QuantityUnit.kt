package org.kert0n.medappserver.db.model.parsed

import jakarta.persistence.*
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.util.*

@Entity
@Table(
    name = "quantity_units", uniqueConstraints = [UniqueConstraint(
        name = "quantity_units_name_key",
        columnNames = ["name"]
    )]
)
open class QuantityUnit(
    @Id
    @Column(name = "id", nullable = false)
    open var id: UUID = UUID.randomUUID(),

    @Size(max = 30)
    @NotNull
    @Column(name = "name", nullable = false, length = 30)
    open var name: String

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