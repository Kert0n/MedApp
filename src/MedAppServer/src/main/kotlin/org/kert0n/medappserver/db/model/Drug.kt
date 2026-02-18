package org.kert0n.medappserver.db.model

import io.swagger.v3.oas.annotations.media.Schema
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
    var medKit: MedKit,
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
    @Schema(description = "Drug unique identifier", example = "123e4567-e89b-12d3-a456-426614174000")
    val id: UUID,
    @Schema(description = "Drug name", example = "Aspirin")
    val name: String,
    @Schema(description = "Current available quantity", example = "100.0")
    val quantity: Double,
    @Schema(description = "Total planned quantity across all users", example = "30.0")
    val plannedQuantity: Double,
    @Schema(description = "Quantity unit", example = "tablets")
    val quantityUnit: String,
    @Schema(description = "Drug form type", example = "tablet")
    val formType: String,
    @Schema(description = "Drug category", example = "Pain relief")
    val category: String,
    @Schema(description = "Manufacturer name", example = "Bayer")
    val manufacturer: String,
    @Schema(description = "Country of origin", example = "Germany")
    val country: String,
    @Schema(description = "Drug description", example = "Pain reliever and fever reducer")
    val description: String,
    @Schema(description = "Medicine kit UUID", example = "123e4567-e89b-12d3-a456-426614174001")
    val medKit:UUID,
)

data class DrugPostDTO(
    @Schema(description = "Drug name", example = "Aspirin", required = true)
    val name: String,
    @Schema(description = "Initial quantity", example = "100.0", required = true)
    val quantity: Double,
    @Schema(description = "Quantity unit", example = "tablets", required = true)
    val quantityUnit: String,
    @Schema(description = "Drug form type", example = "tablet")
    val formType: String,
    @Schema(description = "Drug category", example = "Pain relief")
    val category: String,
    @Schema(description = "Manufacturer name", example = "Bayer")
    val manufacturer: String,
    @Schema(description = "Country of origin", example = "Germany")
    val country: String,
    @Schema(description = "Drug description", example = "Pain reliever")
    val description: String,
    @Schema(description = "Medicine kit UUID to add drug to", example = "123e4567-e89b-12d3-a456-426614174001", required = true)
    val owner:UUID,
)

