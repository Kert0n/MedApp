package org.kert0n.medappserver.integration

import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Test
import org.kert0n.medappserver.controller.UsingCreateDTO
import org.kert0n.medappserver.db.model.*
import org.kert0n.medappserver.db.repository.*
import org.kert0n.medappserver.services.DrugService
import org.kert0n.medappserver.services.MedKitService
import org.kert0n.medappserver.services.UsingService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import java.util.*
import kotlin.test.*

/**
 * User Story Integration Tests
 * 
 * These tests simulate realistic user journeys through the application,
 * testing end-to-end workflows and edge cases through actual user scenarios.
 * 
 * Stories covered:
 * 1. New user creates and manages medkit
 * 2. User shares medkit with friend via share key (multi-user scenario)
 * 3. Multiple users coordinate shared medkit
 * 4. User deletes medkit with migration
 * 5. Edge cases through realistic workflows
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class UserStoryIntegrationTests {

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var medKitRepository: MedKitRepository

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

    /**
     * Story 1: Anna creates her first medkit and adds some drugs
     * 
     * Validates: User registration, medkit creation, drug management, consumption tracking
     */
    @Test
    fun `Story 1 - New user Anna creates and manages her medkit`() {
        // Anna signs up
        val anna = User(
            id = UUID.randomUUID(),
            hashedKey = "anna_hashed_key_${UUID.randomUUID()}"
        )
        userRepository.save(anna)
        entityManager.flush()

        // Creates medkit
        val homeMedkit = medKitService.createNew(anna.id)
        assertNotNull(homeMedkit)

        // Adds drugs using repository directly (simulating controller layer)
        val aspirin = Drug(
            id = UUID.randomUUID(),
            name = "Aspirin",
            quantity = 100.0,
            quantityUnit = "tablets",
            formType = "tablet",
            category = "painkiller",
            manufacturer = null,
            country = null,
            description = null,
            medKit = homeMedkit
        )
        drugRepository.save(aspirin)

        val ibuprofen = Drug(
            id = UUID.randomUUID(),
            name = "Ibuprofen",
            quantity = 50.0,
            quantityUnit = "tablets",
            formType = "tablet",
            category = "painkiller",
            manufacturer = null,
            country = null,
            description = null,
            medKit = homeMedkit
        )
        drugRepository.save(ibuprofen)
        entityManager.flush()

        // Anna takes 2 tablets of Aspirin
        drugService.consumeDrug(aspirin.id, 2.0, anna.id)
        entityManager.flush()
        entityManager.clear()

        // Check inventory
        val updatedAspirin = drugRepository.findById(aspirin.id).orElse(null)
        assertNotNull(updatedAspirin)
        assertEquals(98.0, updatedAspirin.quantity, "Should have 98 tablets left")

        val drugs = drugRepository.findAllByMedKitId(homeMedkit.id)
        assertEquals(2, drugs.size, "Should have 2 drugs in medkit")
        
        println("✅ Story 1 passed: Anna successfully created medkit and managed drugs")
    }

    /**
     * Story 2: Anna shares her medkit with Bob (her roommate)
     * 
     * Validates: Multi-user medkit sharing, bidirectional relationships, data visibility
     */
    @Test
    fun `Story 2 - Anna shares medkit with roommate Bob`() {
        // Anna's medkit
        val anna = User(id = UUID.randomUUID(), hashedKey = "anna_${UUID.randomUUID()}")
        userRepository.save(anna)
        val medkit = medKitService.createNew(anna.id)
        
        val vitamins = Drug(
            id = UUID.randomUUID(),
            name = "Vitamin C",
            quantity = 30.0,
            quantityUnit = "tablets",
            formType = null,
            category = null,
            manufacturer = null,
            country = null,
            description = null,
            medKit = medkit
        )
        drugRepository.save(vitamins)
        entityManager.flush()

        // Bob signs up
        val bob = User(id = UUID.randomUUID(), hashedKey = "bob_${UUID.randomUUID()}")
        userRepository.save(bob)
        entityManager.flush()

        // Anna shares with Bob via share key
        val shareKey = medKitService.generateMedKitShareKey(medkit.id, anna.id)
        medKitService.joinMedKitByKey(shareKey, bob.id)
        entityManager.flush()
        entityManager.clear()

        // Both can see it
        val annaMedkits = medKitService.findAllByUser(anna.id)
        val bobMedkits = medKitService.findAllByUser(bob.id)
        
        assertEquals(1, annaMedkits.size)
        assertEquals(1, bobMedkits.size)
        assertEquals(annaMedkits[0].id, bobMedkits[0].id, "Should be the same medkit")

        // Verify the medkit has 2 users
        val sharedMedkit = medKitRepository.findById(medkit.id).orElse(null)
        assertNotNull(sharedMedkit)
        assertEquals(2, sharedMedkit.users.size, "Medkit should have 2 users")
        
        println("✅ Story 2 passed: Anna successfully shared medkit with Bob")
    }

    /**
     * Story 3: Bob leaves shared medkit - his data is cleaned up
     * 
     * Validates: User removal, cascade operations, data integrity
     */
    @Test
    fun `Story 3 - Bob leaves shared medkit, cleanup works correctly`() {
        // Setup shared medkit
        val anna = User(id = UUID.randomUUID(), hashedKey = "anna_${UUID.randomUUID()}")
        val bob = User(id = UUID.randomUUID(), hashedKey = "bob_${UUID.randomUUID()}")
        userRepository.save(anna)
        userRepository.save(bob)
        
        val medkit = medKitService.createNew(anna.id)
        val shareKey = medKitService.generateMedKitShareKey(medkit.id, anna.id)
        medKitService.joinMedKitByKey(shareKey, bob.id)
        
        val drug = Drug(
            id = UUID.randomUUID(),
            name = "Test Drug",
            quantity = 100.0,
            quantityUnit = "ml",
            formType = null,
            category = null,
            manufacturer = null,
            country = null,
            description = null,
            medKit = medkit
        )
        drugRepository.save(drug)
        entityManager.flush()
        entityManager.clear()

        // Verify both users have access
        val loadedMedkit = medKitRepository.findById(medkit.id).get()
        assertEquals(2, loadedMedkit.users.size)

        // Bob leaves (drugs stay)
        medKitService.removeUserFromMedKit(medkit.id, bob.id, deleteAllDrugs = false)
        entityManager.flush()
        entityManager.clear()

        // Medkit still exists with Anna only
        val updatedMedkit = medKitRepository.findById(medkit.id).orElse(null)
        assertNotNull(updatedMedkit)
        assertEquals(1, updatedMedkit.users.size, "Only Anna should be in medkit")
        assertTrue(updatedMedkit.users.any { it.id == anna.id })

        // Drug still exists
        val remainingDrug = drugRepository.findById(drug.id).orElse(null)
        assertNotNull(remainingDrug, "Drug should still exist")
        
        println("✅ Story 3 passed: Bob left medkit, cleanup successful")
    }

    /**
     * Story 4: Migrating drugs when deleting a medkit
     * 
     * Validates: Drug migration, medkit deletion, data preservation
     */
    @Test
    fun `Story 4 - User migrates drugs when deleting old medkit`() {
        // Create user and first medkit
        val user = User(id = UUID.randomUUID(), hashedKey = "user_${UUID.randomUUID()}")
        userRepository.save(user)
        val oldMedkit = medKitService.createNew(user.id)

        // Add drugs
        val drug1 = Drug(
            id = UUID.randomUUID(),
            name = "Drug A",
            quantity = 50.0,
            quantityUnit = "tablets",
            formType = null,
            category = null,
            manufacturer = null,
            country = null,
            description = null,
            medKit = oldMedkit
        )
        val drug2 = Drug(
            id = UUID.randomUUID(),
            name = "Drug B",
            quantity = 100.0,
            quantityUnit = "ml",
            formType = null,
            category = null,
            manufacturer = null,
            country = null,
            description = null,
            medKit = oldMedkit
        )
        drugRepository.save(drug1)
        drugRepository.save(drug2)
        entityManager.flush()

        // Create new medkit for migration
        val newMedkit = medKitService.createNew(user.id)
        entityManager.flush()

        // Verify user has 2 medkits
        assertEquals(2, medKitService.findAllByUser(user.id).size)

        // Delete old medkit and move drugs
        medKitService.delete(oldMedkit.id, user.id, newMedkit.id)
        entityManager.flush()
        entityManager.clear()

        // Verify migration
        val drugsInNew = drugRepository.findAllByMedKitId(newMedkit.id)
        assertEquals(2, drugsInNew.size, "All drugs should be in new medkit")
        val drugNames = drugsInNew.map { drug -> drug.name }
        assertTrue(drugNames.contains("Drug A"))
        assertTrue(drugNames.contains("Drug B"))

        // Old medkit should be gone
        val oldMedkitCheck = medKitRepository.findById(oldMedkit.id).orElse(null)
        assertNull(oldMedkitCheck, "Old medkit should be deleted")
        
        // User should have only 1 medkit now
        assertEquals(1, medKitService.findAllByUser(user.id).size)
        
        println("✅ Story 4 passed: Drugs successfully migrated to new medkit")
    }

    /**
     * Story 5: Edge case - consuming all drug quantity
     * 
     * Validates: Boundary conditions, zero quantity handling
     */
    @Test
    fun `Story 5 - User consumes all available drug quantity`() {
        val user = User(id = UUID.randomUUID(), hashedKey = "user_${UUID.randomUUID()}")
        userRepository.save(user)
        
        val medkit = medKitService.createNew(user.id)
        val drug = Drug(
            id = UUID.randomUUID(),
            name = "Limited Drug",
            quantity = 30.0,
            quantityUnit = "tablets",
            formType = null,
            category = null,
            manufacturer = null,
            country = null,
            description = null,
            medKit = medkit
        )
        drugRepository.save(drug)
        entityManager.flush()

        // Consume all in steps
        drugService.consumeDrug(drug.id, 10.0, user.id)
        drugService.consumeDrug(drug.id, 10.0, user.id)
        drugService.consumeDrug(drug.id, 10.0, user.id)
        entityManager.flush()
        entityManager.clear()

        // Drug quantity should be exactly zero
        val updatedDrug = drugRepository.findById(drug.id).orElse(null)
        assertNotNull(updatedDrug)
        assertEquals(0.0, updatedDrug.quantity, "All drug should be consumed")
        
        println("✅ Story 5 passed: All drug quantity consumed correctly")
    }

    /**
     * Story 6: Complex workflow with treatment plans
     * 
     * Validates: Treatment plan creation, intake recording, planned quantity tracking
     */
    @Test
    fun `Story 6 - User creates treatment plan and records intakes`() {
        val user = User(id = UUID.randomUUID(), hashedKey = "user_${UUID.randomUUID()}")
        userRepository.save(user)
        
        val medkit = medKitService.createNew(user.id)
        val drug = Drug(
            id = UUID.randomUUID(),
            name = "Treatment Drug",
            quantity = 100.0,
            quantityUnit = "tablets",
            formType = null,
            category = null,
            manufacturer = null,
            country = null,
            description = null,
            medKit = medkit
        )
        drugRepository.save(drug)
        entityManager.flush()

        // Create treatment plan for 30 tablets
        val plan = usingService.createTreatmentPlan(
            userId = user.id,
            createDTO = UsingCreateDTO(
                drugId = drug.id,
                plannedAmount = 30.0
            )
        )
        assertNotNull(plan)
        entityManager.flush()

        // Verify plan was created
        val createdPlan = usingRepository.findByUserIdAndDrugId(user.id, drug.id)
        assertNotNull(createdPlan, "Plan should be created")
        assertEquals(30.0, createdPlan.plannedAmount, "Planned amount should be 30")

        // Record some intakes
        usingService.recordIntake(user.id, drug.id, 5.0)
        usingService.recordIntake(user.id, drug.id, 5.0)
        entityManager.flush()
        entityManager.clear()

        // Verify drug quantity decreased
        val updatedDrug = drugRepository.findById(drug.id).orElse(null)
        assertNotNull(updatedDrug)
        assertEquals(90.0, updatedDrug.quantity, "Drug quantity should be 90 after 10 consumed")
        
        println("✅ Story 6 passed: Treatment plan and intakes work correctly")
    }

    /**
     * Story 7: Multiple users share a medkit and create separate treatment plans for the same drug
     * 
     * Validates: Multi-user treatment plans, planned quantity accounting, fair sharing
     */
    @Test
    fun `Story 7 - Multiple users create treatment plans on shared drug`() {
        // Setup: Anna and Bob share a medkit with 100 tablets of Vitamin C
        val anna = User(id = UUID.randomUUID(), hashedKey = "anna_${UUID.randomUUID()}")
        val bob = User(id = UUID.randomUUID(), hashedKey = "bob_${UUID.randomUUID()}")
        userRepository.save(anna)
        userRepository.save(bob)

        val medkit = medKitService.createNew(anna.id)
        val shareKey = medKitService.generateMedKitShareKey(medkit.id, anna.id)
        medKitService.joinMedKitByKey(shareKey, bob.id)

        val vitaminC = Drug(
            id = UUID.randomUUID(),
            name = "Vitamin C",
            quantity = 100.0,
            quantityUnit = "tablets",
            formType = "tablet",
            category = null,
            manufacturer = null,
            country = null,
            description = null,
            medKit = medkit
        )
        drugRepository.save(vitaminC)
        entityManager.flush()

        // Anna creates a treatment plan for 40 tablets
        usingService.createTreatmentPlan(anna.id, UsingCreateDTO(vitaminC.id, 40.0))
        entityManager.flush()

        // Bob creates a treatment plan for 50 tablets (should succeed: 100 - 40 = 60 available)
        usingService.createTreatmentPlan(bob.id, UsingCreateDTO(vitaminC.id, 50.0))
        entityManager.flush()

        // Total planned = 90, should match sumPlannedAmount
        val totalPlanned = drugRepository.sumPlannedAmount(vitaminC.id)
        assertEquals(90.0, totalPlanned, "Total planned should be 90")

        // Verify each user has their own plan
        val annaUsing = usingRepository.findByUserIdAndDrugId(anna.id, vitaminC.id)
        val bobUsing = usingRepository.findByUserIdAndDrugId(bob.id, vitaminC.id)
        assertNotNull(annaUsing)
        assertNotNull(bobUsing)
        assertEquals(40.0, annaUsing.plannedAmount)
        assertEquals(50.0, bobUsing.plannedAmount)

        println("✅ Story 7 passed: Multiple users created treatment plans on shared drug")
    }

    /**
     * Story 8: Drug quantity reduction cascades to treatment plans
     * 
     * Validates: handleQuantityReduction logic, proportional reduction of plans
     */
    @Test
    fun `Story 8 - Reducing drug quantity adjusts treatment plans proportionally`() {
        val user = User(id = UUID.randomUUID(), hashedKey = "user_${UUID.randomUUID()}")
        userRepository.save(user)
        val medkit = medKitService.createNew(user.id)

        val drug = Drug(
            id = UUID.randomUUID(),
            name = "Paracetamol",
            quantity = 100.0,
            quantityUnit = "tablets",
            formType = null,
            category = null,
            manufacturer = null,
            country = null,
            description = null,
            medKit = medkit
        )
        drugRepository.save(drug)
        entityManager.flush()

        // Create plan for 80 tablets
        usingService.createTreatmentPlan(user.id, UsingCreateDTO(drug.id, 80.0))
        entityManager.flush()
        entityManager.clear()

        // Consume 50 tablets (drug goes to 50, but plan is 80 > 50)
        // handleQuantityReduction should scale the plan down
        drugService.consumeDrug(drug.id, 50.0, user.id)
        entityManager.flush()
        entityManager.clear()

        val updatedDrug = drugRepository.findById(drug.id).orElse(null)
        assertNotNull(updatedDrug)
        assertEquals(50.0, updatedDrug.quantity)

        // Plan should be reduced proportionally: 80 * (50/80) = 50
        val updatedPlan = usingRepository.findByUserIdAndDrugId(user.id, drug.id)
        assertNotNull(updatedPlan)
        assertTrue(updatedPlan.plannedAmount <= 50.0, "Plan should be reduced to fit available quantity")

        println("✅ Story 8 passed: Drug quantity reduction cascaded to treatment plans")
    }

    /**
     * Story 9: Cannot create treatment plan exceeding available quantity
     * 
     * Validates: Planned quantity validation, error handling
     */
    @Test
    fun `Story 9 - Cannot over-plan drug quantity`() {
        val user = User(id = UUID.randomUUID(), hashedKey = "user_${UUID.randomUUID()}")
        userRepository.save(user)
        val medkit = medKitService.createNew(user.id)

        val drug = Drug(
            id = UUID.randomUUID(),
            name = "Ibuprofen",
            quantity = 50.0,
            quantityUnit = "tablets",
            formType = null,
            category = null,
            manufacturer = null,
            country = null,
            description = null,
            medKit = medkit
        )
        drugRepository.save(drug)
        entityManager.flush()

        // Try to create a plan for 60 tablets when only 50 available
        assertFailsWith<org.springframework.web.server.ResponseStatusException> {
            usingService.createTreatmentPlan(user.id, UsingCreateDTO(drug.id, 60.0))
        }

        // Create a plan for 30
        usingService.createTreatmentPlan(user.id, UsingCreateDTO(drug.id, 30.0))
        entityManager.flush()

        // Another user tries to plan 25 (only 20 available: 50 - 30 = 20)
        val user2 = User(id = UUID.randomUUID(), hashedKey = "user2_${UUID.randomUUID()}")
        userRepository.save(user2)
        val shareKey = medKitService.generateMedKitShareKey(medkit.id, user.id)
        medKitService.joinMedKitByKey(shareKey, user2.id)
        entityManager.flush()

        assertFailsWith<org.springframework.web.server.ResponseStatusException> {
            usingService.createTreatmentPlan(user2.id, UsingCreateDTO(drug.id, 25.0))
        }

        // But 20 should work
        usingService.createTreatmentPlan(user2.id, UsingCreateDTO(drug.id, 20.0))
        entityManager.flush()

        assertEquals(50.0, drugRepository.sumPlannedAmount(drug.id))

        println("✅ Story 9 passed: Cannot over-plan drug quantity")
    }

    /**
     * Story 10: Complete family medkit lifecycle
     * 
     * Validates: Full end-to-end workflow from creation to cleanup
     */
    @Test
    fun `Story 10 - Complete family medkit lifecycle`() {
        // Mom creates a family medkit
        val mom = User(id = UUID.randomUUID(), hashedKey = "mom_${UUID.randomUUID()}")
        val dad = User(id = UUID.randomUUID(), hashedKey = "dad_${UUID.randomUUID()}")
        val child = User(id = UUID.randomUUID(), hashedKey = "child_${UUID.randomUUID()}")
        userRepository.save(mom)
        userRepository.save(dad)
        userRepository.save(child)
        entityManager.flush()

        val familyKit = medKitService.createNew(mom.id)
        val dadKey = medKitService.generateMedKitShareKey(familyKit.id, mom.id)
        medKitService.joinMedKitByKey(dadKey, dad.id)
        val childKey = medKitService.generateMedKitShareKey(familyKit.id, mom.id)
        medKitService.joinMedKitByKey(childKey, child.id)
        entityManager.flush()

        // Add family medications
        val aspirin = Drug(
            id = UUID.randomUUID(), name = "Children's Aspirin",
            quantity = 200.0, quantityUnit = "tablets", formType = "chewable",
            category = "painkiller", manufacturer = null, country = null,
            description = null, medKit = familyKit
        )
        val vitamins = Drug(
            id = UUID.randomUUID(), name = "Multivitamins",
            quantity = 90.0, quantityUnit = "tablets", formType = "tablet",
            category = "supplement", manufacturer = null, country = null,
            description = null, medKit = familyKit
        )
        drugRepository.save(aspirin)
        drugRepository.save(vitamins)
        entityManager.flush()

        // Everyone gets treatment plans for vitamins: 30 each
        usingService.createTreatmentPlan(mom.id, UsingCreateDTO(vitamins.id, 30.0))
        usingService.createTreatmentPlan(dad.id, UsingCreateDTO(vitamins.id, 30.0))
        usingService.createTreatmentPlan(child.id, UsingCreateDTO(vitamins.id, 30.0))
        entityManager.flush()

        // Total planned = 90 (full supply)
        assertEquals(90.0, drugRepository.sumPlannedAmount(vitamins.id))

        // Everyone takes their daily vitamin
        usingService.recordIntake(mom.id, vitamins.id, 1.0)
        usingService.recordIntake(dad.id, vitamins.id, 1.0)
        usingService.recordIntake(child.id, vitamins.id, 1.0)
        entityManager.flush()
        entityManager.clear()

        // Check vitamins after 1 day
        val updatedVitamins = drugRepository.findById(vitamins.id).orElse(null)
        assertNotNull(updatedVitamins)
        assertEquals(87.0, updatedVitamins.quantity, "Should be 90 - 3 = 87")

        // 3 users in the medkit
        val medkit = medKitRepository.findById(familyKit.id).orElse(null)
        assertNotNull(medkit)
        assertEquals(3, medkit.users.size)

        // Child leaves the medkit
        medKitService.removeUserFromMedKit(familyKit.id, child.id)
        entityManager.flush()
        entityManager.clear()

        // Medkit still has mom and dad
        val updatedKit = medKitRepository.findById(familyKit.id).orElse(null)
        assertNotNull(updatedKit)
        assertEquals(2, updatedKit.users.size)

        println("✅ Story 10 passed: Complete family medkit lifecycle")
    }

    /**
     * Story 11: Moving drugs between medkits preserves treatment plans
     * 
     * Validates: Drug move, treatment plan integrity
     */
    @Test
    fun `Story 11 - Moving drug between medkits`() {
        val user = User(id = UUID.randomUUID(), hashedKey = "user_${UUID.randomUUID()}")
        userRepository.save(user)

        val homeKit = medKitService.createNew(user.id)
        val travelKit = medKitService.createNew(user.id)

        val painkiller = Drug(
            id = UUID.randomUUID(), name = "Ibuprofen",
            quantity = 60.0, quantityUnit = "tablets", formType = "tablet",
            category = "painkiller", manufacturer = null, country = null,
            description = null, medKit = homeKit
        )
        drugRepository.save(painkiller)
        entityManager.flush()

        // Create treatment plan
        usingService.createTreatmentPlan(user.id, UsingCreateDTO(painkiller.id, 20.0))
        entityManager.flush()

        // Move drug to travel kit
        drugService.moveDrug(painkiller.id, travelKit, user.id)
        entityManager.flush()
        entityManager.clear()

        // Drug is in travel kit
        val movedDrug = drugRepository.findById(painkiller.id).orElse(null)
        assertNotNull(movedDrug)
        assertEquals(travelKit.id, movedDrug.medKit.id)

        // Home kit is empty
        val homeKitDrugs = drugRepository.findAllByMedKitId(homeKit.id)
        assertTrue(homeKitDrugs.isEmpty())

        // Travel kit has the drug
        val travelKitDrugs = drugRepository.findAllByMedKitId(travelKit.id)
        assertEquals(1, travelKitDrugs.size)

        // Treatment plan still exists
        val plan = usingRepository.findByUserIdAndDrugId(user.id, painkiller.id)
        assertNotNull(plan, "Treatment plan should survive drug move")
        assertEquals(20.0, plan.plannedAmount)

        println("✅ Story 11 passed: Drug moved between medkits with treatment plan intact")
    }

    /**
     * Story 12: Update treatment plan correctly checks available quantity
     * 
     * Validates: updateTreatmentPlan bug fix (was double-counting current user's plan)
     */
    @Test
    fun `Story 12 - Updating treatment plan correctly checks available quantity`() {
        val anna = User(id = UUID.randomUUID(), hashedKey = "anna_${UUID.randomUUID()}")
        val bob = User(id = UUID.randomUUID(), hashedKey = "bob_${UUID.randomUUID()}")
        userRepository.save(anna)
        userRepository.save(bob)

        val medkit = medKitService.createNew(anna.id)
        val shareKey = medKitService.generateMedKitShareKey(medkit.id, anna.id)
        medKitService.joinMedKitByKey(shareKey, bob.id)

        val drug = Drug(
            id = UUID.randomUUID(), name = "Medicine X",
            quantity = 100.0, quantityUnit = "ml", formType = "liquid",
            category = null, manufacturer = null, country = null,
            description = null, medKit = medkit
        )
        drugRepository.save(drug)
        entityManager.flush()

        // Anna plans 40, Bob plans 30 (total 70, available 30)
        usingService.createTreatmentPlan(anna.id, UsingCreateDTO(drug.id, 40.0))
        usingService.createTreatmentPlan(bob.id, UsingCreateDTO(drug.id, 30.0))
        entityManager.flush()

        // Anna should be able to increase her plan to 70 (available for her = 100 - 30 (bob) = 70)
        val updated = usingService.updateTreatmentPlan(
            anna.id, drug.id,
            org.kert0n.medappserver.controller.UsingUpdateDTO(70.0)
        )
        assertEquals(70.0, updated.plannedAmount)
        entityManager.flush()

        // Total planned should now be 100 (70 + 30)
        assertEquals(100.0, drugRepository.sumPlannedAmount(drug.id))

        // Anna should NOT be able to increase to 71 (exceeds available)
        assertFailsWith<org.springframework.web.server.ResponseStatusException> {
            usingService.updateTreatmentPlan(
                anna.id, drug.id,
                org.kert0n.medappserver.controller.UsingUpdateDTO(71.0)
            )
        }

        println("✅ Story 12 passed: Treatment plan update correctly checks available quantity")
    }

    /**
     * Story 13: Deleting a drug cascades to remove associated treatment plans
     * 
     * Validates: Cascade delete behavior, orphan removal
     */
    @Test
    fun `Story 13 - Deleting drug removes its treatment plans`() {
        val user = User(id = UUID.randomUUID(), hashedKey = "user_${UUID.randomUUID()}")
        userRepository.save(user)

        val medkit = medKitService.createNew(user.id)
        val drug = Drug(
            id = UUID.randomUUID(), name = "Expired Drug",
            quantity = 50.0, quantityUnit = "tablets", formType = null,
            category = null, manufacturer = null, country = null,
            description = null, medKit = medkit
        )
        drugRepository.save(drug)
        entityManager.flush()

        // Create treatment plan
        usingService.createTreatmentPlan(user.id, UsingCreateDTO(drug.id, 25.0))
        entityManager.flush()
        entityManager.clear()

        // Verify plan exists
        val plan = usingRepository.findByUserIdAndDrugId(user.id, drug.id)
        assertNotNull(plan)

        // Delete the drug
        drugService.delete(drug.id, user.id)
        entityManager.flush()
        entityManager.clear()

        // Drug should be gone
        val deletedDrug = drugRepository.findById(drug.id).orElse(null)
        assertNull(deletedDrug)

        // Treatment plan should also be gone (cascade)
        val deletedPlan = usingRepository.findByUserIdAndDrugId(user.id, drug.id)
        assertNull(deletedPlan)

        println("✅ Story 13 passed: Deleting drug removed its treatment plans")
    }
}
