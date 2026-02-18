package org.kert0n.medappserver.db.model

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.persistence.*
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import jakarta.validation.constraints.DecimalMin
import java.util.UUID

@Entity
@Table(
    name = "user_drugs",
    indexes = [
        Index(name = "ix_user_drugs_name", columnList = "name"),
        Index(name = "ix_user_drugs_med_kit_id", columnList = "med_kit_id")
    ]
)
class Drug (
    @Id
    @Column(name = "id", nullable = false)
    var id: UUID = UUID.randomUUID(),
    
    @NotNull
    @Size(max = 300)
    @Column(name = "name", nullable = false, length = 300)
    var name: String,
    
    @NotNull
    @Column(name = "quantity", nullable = false)
    var quantity: Double,
    
    @NotNull
    @Size(max = 50)
    @Column(name = "quantity_unit", nullable = false, length = 50)
    var quantityUnit: String,
    
    @Size(max = 100)
    @Column(name = "form_type", length = 100)
    var formType: String?,
    
    @Size(max = 200)
    @Column(name = "category", length = 200)
    var category: String?,
    
    @Size(max = 300)
    @Column(name = "manufacturer", length = 300)
    var manufacturer: String?,
    
    @Size(max = 100)
    @Column(name = "country", length = 100)
    var country: String?,
    
    @Column(name = "description", length = Integer.MAX_VALUE)
    var description: String?,
    
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "med_kit_id", nullable = false)
    var medKit: MedKit,
    
    @OneToMany(mappedBy = "drug", fetch = FetchType.LAZY, cascade = [CascadeType.ALL], orphanRemoval = true)
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

@Schema(description = "Drug information with planned quantity")
data class DrugDTO(
    @Schema(description = "Drug ID")
    val id: UUID,
    @Schema(description = "Drug name", example = "Aspirin")
    val name: String,
    @Schema(description = "Current quantity", example = "100.0")
    val quantity: Double,
    @Schema(description = "Total planned quantity across treatment plans", example = "30.0")
    val plannedQuantity: Double,
    @Schema(description = "Quantity unit", example = "mg")
    val quantityUnit: String,
    @Schema(description = "Form type", example = "tablet")
    val formType: String?,
    @Schema(description = "Category", example = "painkiller")
    val category: String?,
    @Schema(description = "Manufacturer", example = "Bayer")
    val manufacturer: String?,
    @Schema(description = "Country", example = "Germany")
    val country: String?,
    @Schema(description = "Description")
    val description: String?,
    @Schema(description = "Medicine kit ID")
    val medKitId: UUID
)

@Schema(description = "Request to create a new drug")
data class DrugCreateDTO(
    @field:NotNull
    @field:Size(min = 1, max = 300)
    @Schema(description = "Drug name", example = "Aspirin", required = true)
    val name: String,
    
    @field:NotNull
    @field:DecimalMin("0.0")
    @Schema(description = "Quantity", example = "100.0", required = true, minimum = "0")
    val quantity: Double,
    
    @field:NotNull
    @field:Size(min = 1, max = 50)
    @Schema(description = "Quantity unit", example = "mg", required = true)
    val quantityUnit: String,
    
    @field:NotNull
    @Schema(description = "Medicine kit ID", required = true)
    val medKitId: UUID,
    
    @field:Size(max = 100)
    @Schema(description = "Form type", example = "tablet")
    val formType: String? = null,
    
    @field:Size(max = 200)
    @Schema(description = "Category", example = "painkiller")
    val category: String? = null,
    
    @field:Size(max = 300)
    @Schema(description = "Manufacturer", example = "Bayer")
    val manufacturer: String? = null,
    
    @field:Size(max = 100)
    @Schema(description = "Country", example = "Germany")
    val country: String? = null,
    
    @Schema(description = "Description")
    val description: String? = null
)

@Schema(description = "Request to update a drug")
data class DrugUpdateDTO(
    @field:Size(min = 1, max = 300)
    @Schema(description = "Drug name", example = "Aspirin")
    val name: String? = null,
    
    @field:DecimalMin("0.0")
    @Schema(description = "Quantity", example = "100.0", minimum = "0")
    val quantity: Double? = null,
    
    @field:Size(min = 1, max = 50)
    @Schema(description = "Quantity unit", example = "mg")
    val quantityUnit: String? = null,
    
    @field:Size(max = 100)
    @Schema(description = "Form type", example = "tablet")
    val formType: String? = null,
    
    @field:Size(max = 200)
    @Schema(description = "Category", example = "painkiller")
    val category: String? = null,
    
    @field:Size(max = 300)
    @Schema(description = "Manufacturer", example = "Bayer")
    val manufacturer: String? = null,
    
    @field:Size(max = 100)
    @Schema(description = "Country", example = "Germany")
    val country: String? = null,
    
    @Schema(description = "Description")
    val description: String? = null
)

