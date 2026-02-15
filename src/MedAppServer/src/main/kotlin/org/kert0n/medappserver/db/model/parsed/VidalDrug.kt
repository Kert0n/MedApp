package org.kert0n.medappserver.db.model.parsed

import jakarta.persistence.*
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.util.*

@Entity
@Table(
    name = "drugs", indexes = [
        Index(
            name = "ix_drugs_drug_id",
            columnList = "drug_id",
            unique = true
        ),
        Index(
            name = "ix_drugs_name",
            columnList = "name"
        ),
        Index(
            name = "idx_drugs_form_type_id",
            columnList = "form_type_id"
        ),
        Index(
            name = "idx_drugs_quantity_unit_id",
            columnList = "quantity_unit_id"
        ),
        Index(
            name = "ix_drugs_active_substance",
            columnList = "active_substance"
        ),
        Index(
            name = "ix_drugs_manufacturer",
            columnList = "manufacturer"
        )]
)
open class VidalDrug(
    @Id
    @Column(name = "id", nullable = false)
    open var id: UUID = UUID.randomUUID(),

    @Size(max = 300)
    @NotNull
    @Column(name = "name", nullable = false, length = 300)
    open var name: String,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "form_type_id")
    open var formType: FormType? = null,

    @Column(name = "quantity")
    open var quantity: Int? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "quantity_unit_id")
    open var quantityUnit: QuantityUnit? = null,

    @Size(max = 300)
    @Column(name = "active_substance", length = 300)
    open var activeSubstance: String? = null,

    @Size(max = 300)
    @Column(name = "category", length = 300)
    open var category: String? = null,

    @Size(max = 300)
    @Column(name = "manufacturer", length = 300)
    open var manufacturer: String,

    @Size(max = 100)
    @Column(name = "country", length = 100)
    open var country: String? = null,

    @Column(name = "description", length = Integer.MAX_VALUE)
    open var description: String? = null,

    @Column(name = "otc",nullable = false)
    @NotNull
    open var otc: Boolean



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