package org.kert0n.medappserver.db.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import jakarta.validation.constraints.NotNull
import java.util.UUID

@Entity
@Table(
    name = "user_drugs",
    indexes = [
        Index(name = "ix_user_drugs_name", columnList = "name")
    ]
)
class Drug (
    @Id
    @Column(name = "id", nullable = false)
    var id: UUID = UUID.randomUUID(),
    @NotNull
    @Column(name = "name", nullable = false)
    var name: String,
    @NotNull
    @Column(name = "quantity", nullable = false)
    var quantity: Double,
    @NotNull
    @Column(name = "quantity_unit", nullable = false)
    var quantityUnit: String,
    @Column(name = "form_type")
    var formType: String?,
    @Column(name = "category")
    var category: String?,
    @Column(name = "manufacturer")
    var manufacturer: String?,
    @Column(name = "country")
    var country: String?,
    @Column(name = "description")
    var description: String?,
    @ManyToOne(fetch = FetchType.LAZY)
    val medKit: MedKit,
    @OneToMany(mappedBy = "drug",fetch = FetchType.LAZY)
    val usings: MutableSet<Using> = mutableSetOf(),

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

data class DrugDTO(
    val id: UUID,
    val name: String,
    val quantity: Double,
    val plannedQuantity: Double,
    val quantityUnit: String,
    val formType: String,
    val category: String,
    val manufacturer: String,
    val country: String,
    val description: String,
    val medKit:UUID,
)

data class DrugPostDTO(
    val name: String,
    val quantity: Double,
    val quantityUnit: String,
    val formType: String,
    val category: String,
    val manufacturer: String,
    val country: String,
    val description: String,
    val owner:UUID,
)

