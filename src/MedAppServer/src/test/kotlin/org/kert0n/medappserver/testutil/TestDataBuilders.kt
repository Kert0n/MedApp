package org.kert0n.medappserver.testutil

import org.kert0n.medappserver.controller.DrugCreateDTO
import org.kert0n.medappserver.controller.DrugUpdateDTO
import org.kert0n.medappserver.db.model.*
import java.util.*

/**
 * Test data builders for creating consistent test entities
 */

class UserBuilder {
    private var id: UUID = UUID.randomUUID()
    private var hashedKey: String = "hashed_test_key_123"

    fun withId(id: UUID) = apply { this.id = id }
    fun withHashedKey(key: String) = apply { this.hashedKey = key }
    
    fun build(): User {
        return User(id = id, hashedKey = hashedKey)
    }
}

class MedKitBuilder {
    private var id: UUID = UUID.randomUUID()

    fun withId(id: UUID) = apply { this.id = id }
    
    fun build(): MedKit {
        return MedKit(id = id)
    }
}

class DrugBuilder(private val medKitId: UUID) {
    private var id: UUID = UUID.randomUUID()
    private var name: String = "Test Drug"
    private var quantity: Double = 100.0
    private var quantityUnit: String = "mg"
    private var formType: String? = "tablet"
    private var category: String? = "painkiller"
    private var manufacturer: String? = "Test Pharma"
    private var country: String? = "TestLand"
    private var description: String? = "Test description"

    fun withId(id: UUID) = apply { this.id = id }
    fun withName(name: String) = apply { this.name = name }
    fun withQuantity(quantity: Double) = apply { this.quantity = quantity }
    fun withQuantityUnit(unit: String) = apply { this.quantityUnit = unit }
    fun withFormType(formType: String?) = apply { this.formType = formType }
    fun withCategory(category: String?) = apply { this.category = category }
    fun withManufacturer(manufacturer: String?) = apply { this.manufacturer = manufacturer }
    fun withCountry(country: String?) = apply { this.country = country }
    fun withDescription(description: String?) = apply { this.description = description }
    
    fun build(): Drug {
        return Drug(
            id = id,
            name = name,
            quantity = quantity,
            quantityUnit = quantityUnit,
            formType = formType,
            category = category,
            manufacturer = manufacturer,
            country = country,
            description = description,
            medKitId = medKitId
        )
    }
}

class UsingBuilder(private val userId: UUID, private val drugId: UUID) {
    private var plannedAmount: Double = 30.0

    fun withPlannedAmount(amount: Double) = apply { this.plannedAmount = amount }
    
    fun build(): Using {
        return Using(
            userId = userId,
            drugId = drugId,
            plannedAmount = plannedAmount
        )
    }
}

class DrugCreateDTOBuilder {
    private var name: String = "Test Drug"
    private var quantity: Double = 100.0
    private var quantityUnit: String = "mg"
    private var medKitId: UUID = UUID.randomUUID()
    private var formType: String? = "tablet"
    private var category: String? = "painkiller"
    private var manufacturer: String? = "Test Pharma"
    private var country: String? = "TestLand"
    private var description: String? = "Test description"

    fun withName(name: String) = apply { this.name = name }
    fun withQuantity(quantity: Double) = apply { this.quantity = quantity }
    fun withQuantityUnit(unit: String) = apply { this.quantityUnit = unit }
    fun withMedKitId(id: UUID) = apply { this.medKitId = id }
    fun withFormType(formType: String?) = apply { this.formType = formType }
    fun withCategory(category: String?) = apply { this.category = category }
    fun withManufacturer(manufacturer: String?) = apply { this.manufacturer = manufacturer }
    fun withCountry(country: String?) = apply { this.country = country }
    fun withDescription(description: String?) = apply { this.description = description }
    
    fun build(): DrugCreateDTO {
        return DrugCreateDTO(
            name = name,
            quantity = quantity,
            quantityUnit = quantityUnit,
            medKitId = medKitId,
            formType = formType,
            category = category,
            manufacturer = manufacturer,
            country = country,
            description = description
        )
    }
}

class DrugUpdateDTOBuilder {
    private var name: String? = null
    private var quantity: Double? = null
    private var quantityUnit: String? = null
    private var formType: String? = null
    private var category: String? = null
    private var manufacturer: String? = null
    private var country: String? = null
    private var description: String? = null

    fun withName(name: String) = apply { this.name = name }
    fun withQuantity(quantity: Double) = apply { this.quantity = quantity }
    fun withQuantityUnit(unit: String) = apply { this.quantityUnit = unit }
    fun withFormType(formType: String) = apply { this.formType = formType }
    fun withCategory(category: String) = apply { this.category = category }
    fun withManufacturer(manufacturer: String) = apply { this.manufacturer = manufacturer }
    fun withCountry(country: String) = apply { this.country = country }
    fun withDescription(description: String) = apply { this.description = description }
    
    fun build(): DrugUpdateDTO {
        return DrugUpdateDTO(
            name = name,
            quantity = quantity,
            quantityUnit = quantityUnit,
            formType = formType,
            category = category,
            manufacturer = manufacturer,
            country = country,
            description = description
        )
    }
}

// Helper functions for quick access
fun userBuilder() = UserBuilder()
fun medKitBuilder() = MedKitBuilder()
fun drugBuilder(medKitId: UUID) = DrugBuilder(medKitId)
fun usingBuilder(userId: UUID, drugId: UUID) = UsingBuilder(userId, drugId)
fun drugCreateDTOBuilder() = DrugCreateDTOBuilder()
fun drugUpdateDTOBuilder() = DrugUpdateDTOBuilder()
