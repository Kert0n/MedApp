package org.kert0n.medappserver.integration

import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.kert0n.medappserver.controller.DrugCreateDTO
import org.kert0n.medappserver.controller.DrugUpdateDTO
import org.kert0n.medappserver.controller.UsingCreateDTO
import org.kert0n.medappserver.controller.UsingUpdateDTO
import org.kert0n.medappserver.db.model.Drug
import org.kert0n.medappserver.db.model.User
import org.kert0n.medappserver.db.repository.DrugRepository
import org.kert0n.medappserver.db.repository.MedKitRepository
import org.kert0n.medappserver.db.repository.UserRepository
import org.kert0n.medappserver.db.repository.UsingRepository
import org.kert0n.medappserver.services.models.DrugService
import org.kert0n.medappserver.services.models.MedKitService
import org.kert0n.medappserver.services.models.UsingService
import org.kert0n.medappserver.services.orchestrators.MedKitDrugServices
import org.kert0n.medappserver.testutil.DatabaseTestHelper
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.repository.findByIdOrNull
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
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


    @Autowired private lateinit var dbHelper: DatabaseTestHelper
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
    private lateinit var medKitDrugServices: MedKitDrugServices

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
        medKitDrugServices.removeUserFromMedKit(medkit.id, bob.id)
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

        // Create new medkit for migration
        val newMedkit = medKitService.createNew(user.id)
        entityManager.flush()
        entityManager.clear()

        // Verify user has 2 medkits
        assertEquals(2, medKitService.findAllByUser(user.id).size)

        // Delete old medkit and move drugs
        medKitDrugServices.delete(oldMedkit.id, user.id, newMedkit.id)
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
        // Must be deleted
        assertNull(updatedDrug)
        
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
        entityManager.clear()
        // Total planned = 90, should match sumPlannedAmount
        assertEquals(90.0, drugRepository.findByIdOrNull(vitaminC.id)?.totalPlannedAmount, "Total planned should be 90")

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
        assertFailsWith<ResponseStatusException> {
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
        entityManager.clear()
        assertFailsWith<ResponseStatusException> {
            usingService.createTreatmentPlan(user2.id, UsingCreateDTO(drug.id, 25.0))
        }

        // But 20 should work
        usingService.createTreatmentPlan(user2.id, UsingCreateDTO(drug.id, 20.0))
        entityManager.flush()
        entityManager.clear()
        assertEquals(50.0, drugRepository.findByIdOrNull(drug.id)?.totalPlannedAmount)

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
        entityManager.clear()
        // Total planned = 90 (full supply)
        assertEquals(90.0, drugRepository.findByIdOrNull(vitamins.id)?.totalPlannedAmount)

        // Everyone takes their daily vitamin
        usingService.recordIntake(mom.id, vitamins.id, 1.0)
        entityManager.flush()
        usingService.recordIntake(dad.id, vitamins.id, 1.0)
        entityManager.flush()
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
        medKitDrugServices.removeUserFromMedKit(familyKit.id, child.id)
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
        medKitDrugServices.moveDrug(painkiller.id, travelKit.id, user.id)
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
            UsingUpdateDTO(70.0)
        )
        assertEquals(70.0, updated.plannedAmount)
        entityManager.flush()
        entityManager.clear()
        // Total planned should now be 100 (70 + 30)
        assertEquals(100.0, drugRepository.findByIdOrNull(drug.id)?.totalPlannedAmount)

        // Anna should NOT be able to increase to 71 (exceeds available)
        assertFailsWith<ResponseStatusException> {
            usingService.updateTreatmentPlan(
                anna.id, drug.id,
                UsingUpdateDTO(71.0)
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

    /**
     * Story 14: Migrating a drug to a private medkit strips access from former shared users
     * * Validates: Migration Security Audit (The "Void Pointer" fix)
     */
    @Test
    fun `Story 14 - Moving shared drug to private medkit removes other users treatment plans`() {
        // Setup: Anna, Bob, and Charlie share an Old MedKit
        val anna = userRepository.save(User(id = UUID.randomUUID(), hashedKey = "anna_${UUID.randomUUID()}"))
        val bob = userRepository.save(User(id = UUID.randomUUID(), hashedKey = "bob_${UUID.randomUUID()}"))
        val charlie = userRepository.save(User(id = UUID.randomUUID(), hashedKey = "charlie_${UUID.randomUUID()}"))

        val oldKit = medKitService.createNew(anna.id)
        medKitService.joinMedKitByKey(medKitService.generateMedKitShareKey(oldKit.id, anna.id), bob.id)
        medKitService.joinMedKitByKey(medKitService.generateMedKitShareKey(oldKit.id, anna.id), charlie.id)

        // Setup: Anna and Bob share a New MedKit (Charlie is excluded)
        val newKit = medKitService.createNew(anna.id)
        medKitService.joinMedKitByKey(medKitService.generateMedKitShareKey(newKit.id, anna.id), bob.id)

        // Add drug to old kit
        val drug = drugRepository.save(
            Drug(
                id = UUID.randomUUID(), name = "Special Meds", quantity = 90.0,
                quantityUnit = "pills", medKit = oldKit, formType = null,
                category = null,
                manufacturer = null,
                country = null,
                description = null
            )
        )

        // Everyone creates a plan for 30 pills
        usingService.createTreatmentPlan(anna.id, UsingCreateDTO(drug.id, 30.0))
        usingService.createTreatmentPlan(bob.id, UsingCreateDTO(drug.id, 30.0))
        usingService.createTreatmentPlan(charlie.id, UsingCreateDTO(drug.id, 30.0))

        entityManager.flush()
        entityManager.clear()

        // Anna deletes the old kit and migrates to the new kit
        medKitDrugServices.delete(oldKit.id, anna.id, newKit.id)

        entityManager.flush()
        entityManager.clear()

        // Verify: Anna and Bob still have their plans. Charlie's plan was deleted.
        assertNotNull(usingRepository.findByUserIdAndDrugId(anna.id, drug.id), "Anna should keep her plan")
        assertNotNull(usingRepository.findByUserIdAndDrugId(bob.id, drug.id), "Bob should keep his plan")
        assertNull(
            usingRepository.findByUserIdAndDrugId(charlie.id, drug.id),
            "Charlie's plan MUST be deleted for security"
        )

        println("✅ Story 14 passed: Migration security successfully audited treatment plans")
    }

    /**
     * Story 15: Heavy consumption scales down shared treatment plans proportionally
     * * Validates: handleQuantityReduction logic precision
     */
    @Test
    fun `Story 15 - Consuming below reserved threshold scales plans proportionally`() {
        val anna = userRepository.save(User(id = UUID.randomUUID(), hashedKey = "anna_${UUID.randomUUID()}"))
        val bob = userRepository.save(User(id = UUID.randomUUID(), hashedKey = "bob_${UUID.randomUUID()}"))

        val kit = medKitService.createNew(anna.id)
        medKitService.joinMedKitByKey(medKitService.generateMedKitShareKey(kit.id, anna.id), bob.id)

        // Drug has 100 total
        val drug = drugRepository.save(
            Drug(
                id = UUID.randomUUID(), name = "Shared Vitamins", quantity = 100.0,
                quantityUnit = "pills", medKit = kit, formType = null,
                category = null,
                manufacturer = null,
                country = null,
                description = null
            )
        )

        // Anna plans 60, Bob plans 40. Total planned = 100.
        usingService.createTreatmentPlan(anna.id, UsingCreateDTO(drug.id, 60.0))
        usingService.createTreatmentPlan(bob.id, UsingCreateDTO(drug.id, 40.0))

        entityManager.flush()
        entityManager.clear()

        // Bob consumes 50 pills (ignoring his plan limit for emergency)
        // Drug quantity drops to 50.
        // Factor should be: 50 / 100 = 0.5
        drugService.consumeDrug(drug.id, 50.0, bob.id)

        entityManager.flush()
        entityManager.clear()

        val annaPlan = usingRepository.findByUserIdAndDrugId(anna.id, drug.id)!!
        val bobPlan = usingRepository.findByUserIdAndDrugId(bob.id, drug.id)!!

        // Plans should be exactly halved
        assertEquals(30.0, annaPlan.plannedAmount, "Anna's plan should scale from 60 to 30")
        assertEquals(20.0, bobPlan.plannedAmount, "Bob's plan should scale from 40 to 20")

        println("✅ Story 15 passed: Treatment plans scaled proportionally after heavy consumption")
    }

    /**
     * Story 16: Partial migration prevents orphan removal
     * * Validates: Explicit `targetMedKit.drugs.add(drug)` fix
     */
    @Test
    fun `Story 16 - Moving single drug preserves it from orphan removal`() {
        val user = userRepository.save(User(id = UUID.randomUUID(), hashedKey = "user_${UUID.randomUUID()}"))

        val sourceKit = medKitService.createNew(user.id)
        val targetKit = medKitService.createNew(user.id)

        val drugToMove = drugRepository.save(
            Drug(
                id = UUID.randomUUID(), name = "Moving Pill", quantity = 10.0,
                quantityUnit = "pills", medKit = sourceKit, formType = null,
                category = null,
                manufacturer = null,
                country = null,
                description = null
            )
        )

        val drugToStay = drugRepository.save(
            Drug(
                id = UUID.randomUUID(), name = "Staying Pill", quantity = 10.0,
                quantityUnit = "pills", medKit = sourceKit, formType = null,
                category = null,
                manufacturer = null,
                country = null,
                description = null
            )
        )

        entityManager.flush()
        entityManager.clear()

        // Move ONLY one drug
        medKitDrugServices.moveDrug(drugToMove.id, targetKit.id, user.id)

        entityManager.flush()
        entityManager.clear()

        // Verify it wasn't deleted by orphan removal during the move
        val movedDrug = drugRepository.findById(drugToMove.id).orElse(null)
        assertNotNull(movedDrug, "Moved drug must not be deleted")
        assertEquals(targetKit.id, movedDrug.medKit.id, "Drug should point to new kit")

        val stayingDrug = drugRepository.findById(drugToStay.id).orElse(null)
        assertNotNull(stayingDrug, "Staying drug must not be affected")
        assertEquals(sourceKit.id, stayingDrug.medKit.id)

        println("✅ Story 16 passed: Moving a single drug prevented orphan removal")
    }

    /**
     * Story 17: The Roommate Saga (The Ultimate Stress Test)
     * * Validates:
     * - Multi-user sharing and permissions
     * - Proportional quantity reduction of Usings during heavy consumption
     * - Security stripping of Usings during single-drug moves
     * - Security stripping of Usings during full kit migrations
     * - Orphan removal prevention during migrations
     * - Auto-deletion of MedKits when empty
     * - JPA L1 Cache integrity across complex interwoven workflows
     */
    @Test
    fun `Story 17 - The Roommate Saga complex interwoven workflow`() {
        // ==========================================
        // PHASE 1: Setup and Sharing
        // ==========================================
        val alice = userRepository.save(User(id = UUID.randomUUID(), hashedKey = "alice_${UUID.randomUUID()}"))
        val bob = userRepository.save(User(id = UUID.randomUUID(), hashedKey = "bob_${UUID.randomUUID()}"))
        val charlie = userRepository.save(User(id = UUID.randomUUID(), hashedKey = "charlie_${UUID.randomUUID()}"))

        val homeKit = medKitService.createNew(alice.id)
        medKitService.joinMedKitByKey(medKitService.generateMedKitShareKey(homeKit.id, alice.id), bob.id)
        medKitService.joinMedKitByKey(medKitService.generateMedKitShareKey(homeKit.id, alice.id), charlie.id)

        val allergyMeds = drugRepository.save(
            Drug(
                id = UUID.randomUUID(), name = "Allergy Meds", quantity = 60.0,
                quantityUnit = "pills", medKit = homeKit, formType = null,
                category = null,
                manufacturer = null,
                country = null,
                description = null
            )
        )
        val painkillers = drugRepository.save(
            Drug(
                id = UUID.randomUUID(), name = "Painkillers", quantity = 100.0,
                quantityUnit = "pills", medKit = homeKit, formType = null,
                category = null,
                manufacturer = null,
                country = null,
                description = null
            )
        )

        // Emulate end of HTTP request
        entityManager.flush()
        entityManager.clear()

        // ==========================================
        // PHASE 2: Everyone makes Treatment Plans
        // ==========================================
        // Allergy Meds: 60 total. Alice (20), Bob (20), Charlie (20) = 60 planned.
        usingService.createTreatmentPlan(alice.id, UsingCreateDTO(allergyMeds.id, 20.0))
        usingService.createTreatmentPlan(bob.id, UsingCreateDTO(allergyMeds.id, 20.0))
        usingService.createTreatmentPlan(charlie.id, UsingCreateDTO(allergyMeds.id, 20.0))

        // Painkillers: 100 total. Bob plans 30, Charlie plans 30.
        usingService.createTreatmentPlan(bob.id, UsingCreateDTO(painkillers.id, 30.0))
        usingService.createTreatmentPlan(charlie.id, UsingCreateDTO(painkillers.id, 30.0))

        entityManager.flush()
        entityManager.clear()

        // ==========================================
        // PHASE 3: Heavy Consumption & Auto-Scaling
        // ==========================================
        // Bob consumes 30 Allergy Meds. Stock drops from 60 to 30.
        // Total planned was 60. Stock is now 30. Scale factor = 30/60 = 0.5.
        // All plans (20) should auto-scale down to 10.
        drugService.consumeDrug(allergyMeds.id, 30.0, bob.id)

        entityManager.flush()
        entityManager.clear()

        val updatedAllergyMeds = drugRepository.findById(allergyMeds.id).get()
        assertEquals(30.0, updatedAllergyMeds.quantity, "Stock should be 30")

        val aliceAllergyPlan = usingRepository.findByUserIdAndDrugId(alice.id, allergyMeds.id)!!
        assertEquals(10.0, aliceAllergyPlan.plannedAmount, "Alice's plan should auto-scale to 10")

        // ==========================================
        // PHASE 4: Single Drug Move (Security Audit)
        // ==========================================
        // Alice makes a private travel kit and takes the Painkillers.
        val travelKit = medKitService.createNew(alice.id)

        entityManager.flush()
        entityManager.clear()

        medKitDrugServices.moveDrug(painkillers.id, travelKit.id, alice.id)

        entityManager.flush()
        entityManager.clear()

        // Verify Bob and Charlie lost their Painkiller plans because they can't see the Travel Kit
        assertNull(usingRepository.findByUserIdAndDrugId(bob.id, painkillers.id), "Bob's plan must be deleted")
        assertNull(usingRepository.findByUserIdAndDrugId(charlie.id, painkillers.id), "Charlie's plan must be deleted")

        val movedPainkillers = drugRepository.findById(painkillers.id).get()
        assertEquals(travelKit.id, movedPainkillers.medKit.id, "Drug successfully moved")

        // ==========================================
        // PHASE 5: Kill & Migrate (The Final Boss)
        // ==========================================
        // Alice deletes Home Kit. She moves remaining Allergy Meds to a new "Duo Kit" with just Bob.
        val duoKit = medKitService.createNew(alice.id)
        medKitService.joinMedKitByKey(medKitService.generateMedKitShareKey(duoKit.id, alice.id), bob.id)

        entityManager.flush()
        entityManager.clear()

        // Perform the complex deletion migration
        medKitDrugServices.delete(homeKit.id, alice.id, duoKit.id)

        entityManager.flush()
        entityManager.clear()

        // Verify Home Kit is dead
        assertNull(medKitRepository.findByIdOrNull(homeKit.id), "Home kit must be completely deleted")

        // Verify Allergy Meds moved safely without orphan removal
        val migratedAllergyMeds = drugRepository.findById(allergyMeds.id).orElse(null)
        assertNotNull(migratedAllergyMeds, "Allergy meds must survive the migration")
        assertEquals(duoKit.id, migratedAllergyMeds.medKit.id, "Allergy meds are in Duo Kit")

        // Verify Charlie's Allergy Meds plan was stripped because he isn't in Duo Kit
        assertNull(
            usingRepository.findByUserIdAndDrugId(charlie.id, allergyMeds.id),
            "Charlie's last plan must be deleted"
        )

        // Verify Alice and Bob kept their 10.0 scaled plans
        val finalAlicePlan = usingRepository.findByUserIdAndDrugId(alice.id, allergyMeds.id)!!
        assertEquals(10.0, finalAlicePlan.plannedAmount, "Alice kept her plan through migration")

        // ==========================================
        // PHASE 6: Last User Standing Auto-Cleanup
        // ==========================================
        // Bob leaves Duo Kit
        medKitDrugServices.removeUserFromMedKit(duoKit.id, bob.id)

        entityManager.flush()
        entityManager.clear()

        val duoKitCheck1 = medKitRepository.findById(duoKit.id).get()
        assertEquals(1, duoKitCheck1.users.size, "Only Alice remains")

        // Alice leaves Duo Kit. Because she is the last user, the kit should auto-delete.
        // (Using medKitService directly as medKitDrugServices might check for users first)
        val aliceFresh = userRepository.findById(alice.id).get()
        medKitService.removeUserFromMedKit(duoKitCheck1, aliceFresh)

        entityManager.flush()
        entityManager.clear()

        assertNull(medKitRepository.findByIdOrNull(duoKit.id), "Duo kit must auto-delete when last user leaves")
        assertNull(
            drugRepository.findByIdOrNull(allergyMeds.id),
            "Cascade should kill the drugs inside the abandoned kit"
        )

        println("✅ Story 17 passed: The Roommate Saga completed without a single JPA integrity violation")
    }
    @Test
    fun `Story 18 - Full Lifecycle Alterations, Movement, and Deletion`() {
        // ── Setup ──
        val alice = dbHelper.freshUser("alice")
        val bob = dbHelper.freshUser("bob")

        val sourceKit = medKitService.createNew(alice.id)
        val targetKit = medKitService.createNew(alice.id) // Only Alice has access to this one
        medKitService.joinMedKitByKey(medKitService.generateMedKitShareKey(sourceKit.id, alice.id), bob.id)

        // Alice adds 100 tablets to sourceKit
        val createDrugDto = DrugCreateDTO(
            name = "LifePill", quantity = 100.0, quantityUnit = "tablets",
            medKitId = sourceKit.id, formType = null, category = null,
            manufacturer = null, country = null, description = null
        )
        val drug = medKitDrugServices.createDrugInMedkit(createDrugDto, alice.id)
        dbHelper.flushAndClear()

        // Alice and Bob create treatment plans (40 each, total 80)
        usingService.createTreatmentPlan(alice.id, UsingCreateDTO(drug.id, 40.0))
        usingService.createTreatmentPlan(bob.id, UsingCreateDTO(drug.id, 40.0))
        dbHelper.flushAndClear()

        // ── Phase 1: Alter Using ──
        // Bob increases his plan from 40 to 60.
        // Allowed because 100 stock - 40 Alice = 60 available.
        usingService.updateTreatmentPlan(bob.id, drug.id, UsingUpdateDTO(plannedAmount = 60.0))
        dbHelper.flushAndClear()

        assertEquals(60.0, dbHelper.userPlan(bob.id, drug.id)!!, 0.001, "Bob's plan updated to 60")
        assertEquals(40.0, dbHelper.userPlan(alice.id, drug.id)!!, 0.001, "Alice's plan unchanged at 40")

        // ── Phase 2: Alter Drug (The Spill) ──
        // Alice updates the drug quantity from 100 to 50.
        // This MUST trigger `handleQuantityReduction`. Factor = 50 / 100 = 0.5.
        val updateDrugDto = DrugUpdateDTO(quantity = 50.0)
        drugService.update(drug.id, updateDrugDto, alice.id)
        dbHelper.flushAndClear()

        assertEquals(50.0, dbHelper.drugQuantity(drug.id)!!, 0.001, "Drug quantity updated to 50")
        assertEquals(20.0, dbHelper.userPlan(alice.id, drug.id)!!, 0.001, "Alice scaled down (40 -> 20)")
        assertEquals(30.0, dbHelper.userPlan(bob.id, drug.id)!!, 0.001, "Bob scaled down (60 -> 30)")

        // ── Phase 3: Move Drug ──
        // Alice moves the drug to targetKit (where Bob has no access).
        medKitDrugServices.moveDrug(drug.id, targetKit.id, alice.id)
        dbHelper.flushAndClear()

        val movedDrug = drugService.findById(drug.id)
        assertEquals(targetKit.id, movedDrug.medKit.id, "Drug successfully moved to targetKit")

        // The ultimate security check: Bob's plan must be gone
        assertNull(dbHelper.userPlan(bob.id, drug.id), "Bob's plan MUST be stripped due to lost access")
        assertEquals(20.0, dbHelper.userPlan(alice.id, drug.id)!!, 0.001, "Alice's plan remains intact")

        // ── Phase 4: Privacy-by-Default Deletion ──
        // Alice deletes the drug completely.
        drugService.delete(drug.id, alice.id)
        dbHelper.flushAndClear()

        // Verify absolute destruction
        assertNull(dbHelper.drugQuantity(drug.id), "Drug record completely purged")
        assertNull(dbHelper.userPlan(alice.id, drug.id), "Alice's plan completely purged along with the drug")

        println("✅ Story 18 passed: Updates, dynamic scaling, access stripping on move, and total deletion worked perfectly.")
    }

    private fun createTestUser(name: String): User {
        // Using repository directly to bypass any complex auth logic in UserService if necessary
        return userRepository.save(User(id = UUID.randomUUID(), hashedKey = name))
    }

    @Test
    fun `Story 19 - Roommate can move drug even without personal treatment plan`() {
        // SETUP: Alice owns a kit, Bob is a roommate
        val alice = createTestUser("alice")
        val bob = createTestUser("bob")

        val kitA = medKitService.createNew(alice.id)
        val shareKey = medKitService.generateMedKitShareKey(kitA.id, alice.id)
        medKitService.joinMedKitByKey(shareKey, bob.id)

        // Alice creates a drug
        val drug = drugService.create(DrugCreateDTO("Shared Meds", 10.0, "pcs", kitA.id), kitA, alice.id)

        // Bob creates a private kit
        val kitB = medKitService.createNew(bob.id)

        // ACT: Bob moves the drug to his private kit
        // This fails if the query uses an INNER JOIN on the 'usings' table
        assertDoesNotThrow {
            medKitDrugServices.moveDrug(drug.id, kitB.id, bob.id)
        }

        // VERIFY: Drug moved
        val updatedDrug = drugRepository.findById(drug.id).get()
        assertEquals(kitB.id, updatedDrug.medKit.id)
    }

    @Test
    fun `Verify movement strips unauthorized usings`() {
        // SETUP: Shared kit with Alice and Bob
        val alice = createTestUser("alice")
        val bob = createTestUser("bob")
        val kitA = medKitService.createNew(alice.id)
        medKitService.joinMedKitByKey(medKitService.generateMedKitShareKey(kitA.id, alice.id), bob.id)

        val drug = drugService.create(DrugCreateDTO("Audit Meds", 10.0, "pcs", kitA.id), kitA, alice.id)

        // Both have plans
        usingService.createTreatmentPlan(alice.id, UsingCreateDTO(drug.id, 5.0))
        usingService.createTreatmentPlan(bob.id, UsingCreateDTO(drug.id, 2.0))

        // Alice has a private kit (Bob is NOT in this one)
        val kitB = medKitService.createNew(alice.id)
        entityManager.flush()
        entityManager.clear()
        // ACT: Move drug to private kit
        medKitDrugServices.moveDrug(drug.id, kitB.id, alice.id)
        entityManager.flush()
        entityManager.clear()
        // VERIFY: Bob's plan is purged, Alice's remains
        val alicePlan = usingRepository.findAllByUserIdWithDrug(alice.id).find { it.drug.id == drug.id }
        val bobPlan = usingRepository.findAllByUserIdWithDrug(bob.id).find { it.drug.id == drug.id }
        assertNotNull(alicePlan, "Alice should keep her plan")
        assertNull(bobPlan, "Bob's plan must be deleted because he lost access to the drug")
    }

    @Test
    fun `Verify drug migration during MedKit deletion`() {
        // SETUP: Alice has Kit A and Kit B
        val alice = createTestUser("alice")
        val kitA = medKitService.createNew(alice.id)
        val kitB = medKitService.createNew(alice.id)
        entityManager.flush()
        entityManager.clear()
        val drug =
            medKitDrugServices.createDrugInMedkit(DrugCreateDTO("Migrating Meds", 10.0, "pcs", kitA.id), alice.id)

        // ACT: Delete Kit A and migrate drugs to Kit B
        entityManager.flush()
        entityManager.clear()
        medKitDrugServices.delete(kitA.id, alice.id, kitB.id)
        entityManager.flush()
        entityManager.clear()
        // VERIFY: Kit A is gone, but the drug survives in Kit B
        val survivingDrug = drugRepository.findById(drug.id).orElse(null)

        assertNotNull(survivingDrug, "Drug should not have been deleted")
        assertEquals(kitB.id, survivingDrug.medKit.id, "Drug should be re-parented to Kit B")
    }
}
