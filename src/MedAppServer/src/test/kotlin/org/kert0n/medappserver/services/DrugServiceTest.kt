package org.kert0n.medappserver.services

import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.api.assertThrows
import org.kert0n.medappserver.controller.DrugUpdateDTO
import org.kert0n.medappserver.controller.UsingCreateDTO
import org.kert0n.medappserver.db.model.Drug
import org.kert0n.medappserver.db.model.User
import org.kert0n.medappserver.db.repository.DrugRepository
import org.kert0n.medappserver.db.repository.UserRepository
import org.kert0n.medappserver.db.repository.UsingRepository
import org.kert0n.medappserver.services.models.DrugService
import org.kert0n.medappserver.services.models.MedKitService
import org.kert0n.medappserver.services.models.UsingService
import org.kert0n.medappserver.testutil.DatabaseTestHelper
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.repository.findByIdOrNull
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNull


@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DrugServiceTest {

    @Autowired
    private lateinit var userRepository: UserRepository
    @Autowired
    private lateinit var drugRepository: DrugRepository
    @Autowired
    private lateinit var usingRepository: UsingRepository
    @Autowired
    private lateinit var entityManager: EntityManager
    @Autowired
    private lateinit var drugService: DrugService
    @Autowired
    private lateinit var medKitService: MedKitService
    @Autowired
    private lateinit var usingService: UsingService
    @Autowired private lateinit var dbHelper: DatabaseTestHelper
 
    @Test
    fun `DrugService - Update with all nulls hits empty branches`() {
        val alice = dbHelper.freshUser("alice")
        val kit = medKitService.createNew(alice.id)
        val drug = dbHelper.freshDrug(kit, 10.0)
        dbHelper.flushAndClear()

        // This covers the negative branch of every single `?.let` on lines 87-101
        val emptyUpdate = DrugUpdateDTO(null, null, null, null, null, null, null, null)
        drugService.update(drug.id, emptyUpdate, alice.id)
        dbHelper.flushAndClear()

        val updated = drugService.findById(drug.id)
        assertEquals(10.0, updated.quantity)
    }

    @Test
    fun `DrugService - Update increasing quantity bypasses reduction`() {
        val alice = dbHelper.freshUser("alice")
        val kit = medKitService.createNew(alice.id)
        val drug = dbHelper.freshDrug(kit, 10.0)
        dbHelper.flushAndClear()

        // Covers the `if (it < oldQuantity)` false branch on line 92
        val increaseUpdate = DrugUpdateDTO(quantity = 20.0)
        drugService.update(drug.id, increaseUpdate, alice.id)
        dbHelper.flushAndClear()

        assertEquals(20.0, dbHelper.drugQuantity(drug.id))
    }

    @Test
    fun `DrugService - Access Denied throws 404`() {
        val alice = dbHelper.freshUser("alice")
        val maliciousEve = dbHelper.freshUser("eve") // Not in the MedKit
        val kit = medKitService.createNew(alice.id)
        val drug = dbHelper.freshDrug(kit, 10.0)
        dbHelper.flushAndClear()

        // Covers lines 38 and 46 `?: throw` branches
        assertThrows<ResponseStatusException> {
            drugService.findByIdForUser(drug.id, maliciousEve.id)
        }
        assertThrows<ResponseStatusException> {
            drugService.findByIdForUserForUpdate(drug.id, maliciousEve.id)
        }
    }
    @Test
    fun `DrugService - Update decreasing quantity triggers reduction logic`() {
        val alice = dbHelper.freshUser("alice")
        val kit = medKitService.createNew(alice.id)
        val drug = dbHelper.freshDrug(kit, 100.0)
        dbHelper.flushAndClear()

        // Create a plan of 80.0
        usingService.createTreatmentPlan(alice.id, UsingCreateDTO(drug.id, 80.0))
        dbHelper.flushAndClear()

        // ACTION: Update quantity to 40.0 (which is < oldQuantity 100.0)
        // This perfectly hits the `if (it < oldQuantity)` branch.
        val reductionUpdate = DrugUpdateDTO(
            name = null, quantity = 40.0, quantityUnit = null,
            formType = null, category = null, manufacturer = null,
            country = null, description = null
        )
        drugService.update(drug.id, reductionUpdate, alice.id)
        dbHelper.flushAndClear()

        // VERIFICATION:
        // Since stock dropped to 40, and plan was 80, handleQuantityReduction MUST have fired.
        // Factor = 40 / 80 = 0.5. Alice's plan should now be 40.0.
        assertEquals(40.0, dbHelper.drugQuantity(drug.id)!!, 0.001, "Drug quantity updated")
        assertEquals(40.0, dbHelper.userPlan(alice.id, drug.id)!!, 0.001, "Plan scaled down via branch execution")
    }
    @Test
    fun `DrugService - Update branches (all nulls and quantity increase)`() {
        val alice = dbHelper.freshUser("alice")
        val kit = medKitService.createNew(alice.id)
        val drug = dbHelper.freshDrug(kit, 50.0)
        dbHelper.flushAndClear()

        // 1. Branch: Increase quantity (Bypasses handleQuantityReduction) + all other fields present
        val fullUpdate = DrugUpdateDTO(
            name = "New Name", quantity = 100.0, quantityUnit = "ml",
            formType = "liquid", category = "cat", manufacturer = "man",
            country = "co", description = "desc"
        )
        drugService.update(drug.id, fullUpdate, alice.id)
        dbHelper.flushAndClear()

        assertEquals(100.0, dbHelper.drugQuantity(drug.id))

        // 2. Branch: All nulls (Bypasses all ?.let blocks entirely)
        val emptyUpdate = DrugUpdateDTO(null, null, null, null, null, null, null, null)
        drugService.update(drug.id, emptyUpdate, alice.id)
        dbHelper.flushAndClear()

        // Unchanged
        assertEquals(100.0, dbHelper.drugQuantity(drug.id))
    }

    @Test
    fun `DrugService - Access Denied throws 404 on protected methods`() {
        val alice = dbHelper.freshUser("alice")
        val eve = dbHelper.freshUser("eve") // Eve has no access to Alice's kit
        val kit = medKitService.createNew(alice.id)
        val drug = dbHelper.freshDrug(kit, 50.0)
        dbHelper.flushAndClear()

        // Hits the false branch of `?: throw` in findByIdForUser
        assertThrows<ResponseStatusException> {
            drugService.findByIdForUser(drug.id, eve.id)
        }
        // Hits the false branch of `?: throw` in findByIdForUserForUpdate
        assertThrows<ResponseStatusException> {
            drugService.findByIdForUserForUpdate(drug.id, eve.id)
        }
    }

    @Test
    fun `DrugService - moveDrug strips access from unauthorized users`() {
        val alice = dbHelper.freshUser("alice")
        val bob = dbHelper.freshUser("bob")
        val sourceKit = medKitService.createNew(alice.id)
        medKitService.joinMedKitByKey(medKitService.generateMedKitShareKey(sourceKit.id, alice.id), bob.id)

        // Target kit belongs ONLY to Alice
        val targetKit = medKitService.createNew(alice.id)
        val drug = dbHelper.freshDrug(sourceKit, 50.0)
        dbHelper.flushAndClear()

        usingService.createTreatmentPlan(alice.id, UsingCreateDTO(drug.id, 10.0))
        usingService.createTreatmentPlan(bob.id, UsingCreateDTO(drug.id, 10.0))
        dbHelper.flushAndClear()

        // Action: Move drug. Bob loses access.
        // This hits the `if (!hasAccess)` true branch inside the removeIf lambda
        drugService.moveDrug(drug.id, targetKit, alice.id)
        dbHelper.flushAndClear()

        assertNull(dbHelper.userPlan(bob.id, drug.id), "Bob's plan must be deleted due to lost access")
        assertNotNull(dbHelper.userPlan(alice.id, drug.id), "Alice's plan should remain")
    }
}