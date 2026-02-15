package org.kert0n.medappserver.db.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.validation.constraints.NotNull
import org.springframework.context.annotation.Description
import java.util.UUID

@Entity
class Drug (
    @Id
    var id: UUID = UUID.randomUUID(),
    @NotNull
    @Column(nullable = false)
    var name: String,
    @NotNull
    @Column(nullable = false)
    var quantity: Double,
    @NotNull
    @Column(nullable = false)
    var quantityUnit: String,
    var formType: String?,
    var category: String?,
    var manufacturer: String?,
    var country: String?,
    var description: String? ,

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