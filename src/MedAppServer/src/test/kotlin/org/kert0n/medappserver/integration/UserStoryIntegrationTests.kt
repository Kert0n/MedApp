package org.kert0n.medappserver.integration

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.kert0n.medappserver.controller.UsingCreateDTO
import org.kert0n.medappserver.db.model.*
import org.kert0n.medappserver.db.repository.*
import org.kert0n.medappserver.db.table.*
import org.kert0n.medappserver.services.DrugService
import org.kert0n.medappserver.services.MedKitDrugServices
import org.kert0n.medappserver.services.MedKitService
import org.kert0n.medappserver.services.UsingService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.util.*
import kotlin.test.*

/**
 * User Story Integration Tests
 * 
 * These tests simulate realistic user journeys through the application,
 * testing end-to-end workflows and edge cases through actual user scenarios.
 */
@SpringBootTest
@ActiveProfiles("test")
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
    private lateinit var db: Database

    @Autowired
    private lateinit var drugService: DrugService

    @Autowired
    private lateinit var medKitService: MedKitService

    @Autowired
    private lateinit var medKitDrugServices: MedKitDrugServices

    @Autowired
    private lateinit var usingService: UsingService

    @BeforeEach
    fun cleanup() {
        transaction(db) {
            Usings.deleteAll()
            UserDrugs.deleteAll()
            UserMedKits.deleteAll()
            MedKits.deleteAll()
            Users.deleteAll()
        }
    }

    /**
     * Story 1: Anna creates her first medkit and adds some drugs
     */
    @Test
    fun `Story 1 - New user Anna creates and manages her medkit`() {
        // Anna signs up
        val anna = User(
            id = UUID.randomUUID(),
            hashedKey = "anna_hashed_key_${UUID.randomUUID()}"
        )
        transaction(db) { userRepository.save(anna) }

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
            medKitId = homeMedkit.id
        )
        transaction(db) { drugRepository.save(aspirin) }

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
            medKitId = homeMedkit.id
        )
        transaction(db) { drugRepository.save(ibuprofen) }

        // Anna takes 2 tablets of Aspirin
        drugService.consumeDrug(aspirin.id, 2.0, anna.id)

        // Check inventory
        val updatedAspirin = transaction(db) { drugRepository.findById(aspirin.id) }
        assertNotNull(updatedAspirin)
        assertEquals(98.0, updatedAspirin.quantity, "Should have 98 tablets left")

        val drugs = transaction(db) { drugRepository.findAllByMedKitId(homeMedkit.id) }
        assertEquals(2, drugs.size, "Should have 2 drugs in medkit")
        
        println("✅ Story 1 passed: Anna successfully created medkit and managed drugs")
    }

    /**
     * Story 2: Anna shares her medkit with Bob (her roommate)
     */
    @Test
    fun `Story 2 - Anna shares medkit with roommate Bob`() {
        // Anna's medkit
        val anna = User(id = UUID.randomUUID(), hashedKey = "anna_${UUID.randomUUID()}")
        transaction(db) { userRepository.save(anna) }
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
            medKitId = medkit.id
        )
        transaction(db) { drugRepository.save(vitamins) }

        // Bob signs up
        val bob = User(id = UUID.randomUUID(), hashedKey = "bob_${UUID.randomUUID()}")
        transaction(db) { userRepository.save(bob) }

        // Anna shares with Bob via share key
        val shareKey = medKitService.generateMedKitShareKey(medkit.id, anna.id)
        medKitService.joinMedKitByKey(shareKey, bob.id)

        // Both can see it
        val annaMedkits = medKitService.findAllByUser(anna.id)
        val bobMedkits = medKitService.findAllByUser(bob.id)
        
        assertEquals(1, annaMedkits.size)
        assertEquals(1, bobMedkits.size)
        assertEquals(annaMedkits[0].id, bobMedkits[0].id, "Should be the same medkit")

        // Verify the medkit has 2 users
        val userCount = transaction(db) { medKitRepository.countUsersInMedKit(medkit.id) }
        assertEquals(2L, userCount, "Medkit should have 2 users")
        
        println("✅ Story 2 passed: Anna successfully shared medkit with Bob")
    }

    /**
     * Story 3: Bob leaves shared medkit - his data is cleaned up
     */
    @Test
    fun `Story 3 - Bob leaves shared medkit, cleanup works correctly`() {
        // Setup shared medkit
        val anna = User(id = UUID.randomUUID(), hashedKey = "anna_${UUID.randomUUID()}")
        val bob = User(id = UUID.randomUUID(), hashedKey = "bob_${UUID.randomUUID()}")
        transaction(db) {
            userRepository.save(anna)
            userRepository.save(bob)
        }
        
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
            medKitId = medkit.id
        )
        transaction(db) { drugRepository.save(drug) }

        // Verify both users have access
        val userCount = transaction(db) { medKitRepository.countUsersInMedKit(medkit.id) }
        assertEquals(2L, userCount)

        // Bob leaves (drugs stay)
        medKitDrugServices.removeUserFromMedKit(medkit.id, bob.id)

        // Medkit still exists with Anna only
        val updatedCount = transaction(db) { medKitRepository.countUsersInMedKit(medkit.id) }
        assertEquals(1L, updatedCount, "Only Anna should be in medkit")

        val users = transaction(db) { userRepository.findByMedKitsId(medkit.id) }
        assertTrue(users.any { it.id == anna.id })

        // Drug still exists
        val remainingDrug = transaction(db) { drugRepository.findById(drug.id) }
        assertNotNull(remainingDrug, "Drug should still exist")
        
        println("✅ Story 3 passed: Bob left medkit, cleanup successful")
    }

    /**
     * Story 4: Migrating drugs when deleting a medkit
     */
    @Test
    fun `Story 4 - User migrates drugs when deleting old medkit`() {
        // Create user and first medkit
        val user = User(id = UUID.randomUUID(), hashedKey = "user_${UUID.randomUUID()}")
        transaction(db) { userRepository.save(user) }
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
            medKitId = oldMedkit.id
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
            medKitId = oldMedkit.id
        )
        transaction(db) {
            drugRepository.save(drug1)
            drugRepository.save(drug2)
        }

        // Create new medkit for migration
        val newMedkit = medKitService.createNew(user.id)

        // Verify user has 2 medkits
        assertEquals(2, medKitService.findAllByUser(user.id).size)

        // Delete old medkit and move drugs
        medKitDrugServices.delete(oldMedkit.id, user.id, newMedkit.id)

        // Verify migration
        val drugsInNew = transaction(db) { drugRepository.findAllByMedKitId(newMedkit.id) }
        assertEquals(2, drugsInNew.size, "All drugs should be in new medkit")
        val drugNames = drugsInNew.map { drug -> drug.name }
        assertTrue(drugNames.contains("Drug A"))
        assertTrue(drugNames.contains("Drug B"))

        // Old medkit should be gone
        val oldMedkitCheck = transaction(db) { medKitRepository.findById(oldMedkit.id) }
        assertNull(oldMedkitCheck, "Old medkit should be deleted")
        
        // User should have only 1 medkit now
        assertEquals(1, medKitService.findAllByUser(user.id).size)
        
        println("✅ Story 4 passed: Drugs successfully migrated to new medkit")
    }

    /**
     * Story 5: Edge case - consuming all drug quantity
     */
    @Test
    fun `Story 5 - User consumes all available drug quantity`() {
        val user = User(id = UUID.randomUUID(), hashedKey = "user_${UUID.randomUUID()}")
        transaction(db) { userRepository.save(user) }
        
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
            medKitId = medkit.id
        )
        transaction(db) { drugRepository.save(drug) }

        // Consume all in steps
        drugService.consumeDrug(drug.id, 10.0, user.id)
        drugService.consumeDrug(drug.id, 10.0, user.id)
        drugService.consumeDrug(drug.id, 10.0, user.id)

        // Drug quantity should be exactly zero
        val updatedDrug = transaction(db) { drugRepository.findById(drug.id) }
        assertNotNull(updatedDrug)
        assertEquals(0.0, updatedDrug.quantity, "All drug should be consumed")
        
        println("✅ Story 5 passed: All drug quantity consumed correctly")
    }

    /**
     * Story 6: Complex workflow with treatment plans
     */
    @Test
    fun `Story 6 - User creates treatment plan and records intakes`() {
        val user = User(id = UUID.randomUUID(), hashedKey = "user_${UUID.randomUUID()}")
        transaction(db) { userRepository.save(user) }
        
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
            medKitId = medkit.id
        )
        transaction(db) { drugRepository.save(drug) }

        // Create treatment plan for 30 tablets
        val plan = usingService.createTreatmentPlan(
            userId = user.id,
            createDTO = UsingCreateDTO(
                drugId = drug.id,
                plannedAmount = 30.0
            )
        )
        assertNotNull(plan)

        // Verify plan was created
        val createdPlan = transaction(db) { usingRepository.findByUserIdAndDrugId(user.id, drug.id) }
        assertNotNull(createdPlan, "Plan should be created")
        assertEquals(30.0, createdPlan.plannedAmount, "Planned amount should be 30")

        // Record some intakes
        usingService.recordIntake(user.id, drug.id, 5.0)
        usingService.recordIntake(user.id, drug.id, 5.0)

        // Verify drug quantity decreased
        val updatedDrug = transaction(db) { drugRepository.findById(drug.id) }
        assertNotNull(updatedDrug)
        assertEquals(90.0, updatedDrug.quantity, "Drug quantity should be 90 after 10 consumed")
        
        println("✅ Story 6 passed: Treatment plan and intakes work correctly")
    }

    /**
     * Story 7: Multiple users share a medkit and create separate treatment plans for the same drug
     */
    @Test
    fun `Story 7 - Multiple users create treatment plans on shared drug`() {
        // Setup: Anna and Bob share a medkit with 100 tablets of Vitamin C
        val anna = User(id = UUID.randomUUID(), hashedKey = "anna_${UUID.randomUUID()}")
        val bob = User(id = UUID.randomUUID(), hashedKey = "bob_${UUID.randomUUID()}")
        transaction(db) {
            userRepository.save(anna)
            userRepository.save(bob)
        }

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
            medKitId = medkit.id
        )
        transaction(db) { drugRepository.save(vitaminC) }

        // Anna creates a treatment plan for 40 tablets
        usingService.createTreatmentPlan(anna.id, UsingCreateDTO(vitaminC.id, 40.0))

        // Bob creates a treatment plan for 50 tablets (should succeed: 100 - 40 = 60 available)
        usingService.createTreatmentPlan(bob.id, UsingCreateDTO(vitaminC.id, 50.0))

        // Total planned = 90, should match sumPlannedAmount
        val totalPlanned = transaction(db) { drugRepository.sumPlannedAmount(vitaminC.id) }
        assertEquals(90.0, totalPlanned, "Total planned should be 90")

        // Verify each user has their own plan
        val annaUsing = transaction(db) { usingRepository.findByUserIdAndDrugId(anna.id, vitaminC.id) }
        val bobUsing = transaction(db) { usingRepository.findByUserIdAndDrugId(bob.id, vitaminC.id) }
        assertNotNull(annaUsing)
        assertNotNull(bobUsing)
        assertEquals(40.0, annaUsing.plannedAmount)
        assertEquals(50.0, bobUsing.plannedAmount)

        println("✅ Story 7 passed: Multiple users created treatment plans on shared drug")
    }

    /**
     * Story 8: Drug quantity reduction cascades to treatment plans
     */
    @Test
    fun `Story 8 - Reducing drug quantity adjusts treatment plans proportionally`() {
        val user = User(id = UUID.randomUUID(), hashedKey = "user_${UUID.randomUUID()}")
        transaction(db) { userRepository.save(user) }
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
            medKitId = medkit.id
        )
        transaction(db) { drugRepository.save(drug) }

        // Create plan for 80 tablets
        usingService.createTreatmentPlan(user.id, UsingCreateDTO(drug.id, 80.0))

        // Consume 50 tablets (drug goes to 50, but plan is 80 > 50)
        // handleQuantityReduction should scale the plan down
        drugService.consumeDrug(drug.id, 50.0, user.id)

        val updatedDrug = transaction(db) { drugRepository.findById(drug.id) }
        assertNotNull(updatedDrug)
        assertEquals(50.0, updatedDrug.quantity)

        // Plan should be reduced proportionally: 80 * (50/80) = 50
        val updatedPlan = transaction(db) { usingRepository.findByUserIdAndDrugId(user.id, drug.id) }
        assertNotNull(updatedPlan)
        assertTrue(updatedPlan.plannedAmount <= 50.0, "Plan should be reduced to fit available quantity")

        println("✅ Story 8 passed: Drug quantity reduction cascaded to treatment plans")
    }

    /**
     * Story 9: Cannot create treatment plan exceeding available quantity
     */
    @Test
    fun `Story 9 - Cannot over-plan drug quantity`() {
        val user = User(id = UUID.randomUUID(), hashedKey = "user_${UUID.randomUUID()}")
        transaction(db) { userRepository.save(user) }
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
            medKitId = medkit.id
        )
        transaction(db) { drugRepository.save(drug) }

        // Try to create a plan for 60 tablets when only 50 available
        assertFailsWith<org.springframework.web.server.ResponseStatusException> {
            usingService.createTreatmentPlan(user.id, UsingCreateDTO(drug.id, 60.0))
        }

        // Create a plan for 30
        usingService.createTreatmentPlan(user.id, UsingCreateDTO(drug.id, 30.0))

        // Another user tries to plan 25 (only 20 available: 50 - 30 = 20)
        val user2 = User(id = UUID.randomUUID(), hashedKey = "user2_${UUID.randomUUID()}")
        transaction(db) { userRepository.save(user2) }
        val shareKey = medKitService.generateMedKitShareKey(medkit.id, user.id)
        medKitService.joinMedKitByKey(shareKey, user2.id)

        assertFailsWith<org.springframework.web.server.ResponseStatusException> {
            usingService.createTreatmentPlan(user2.id, UsingCreateDTO(drug.id, 25.0))
        }

        // But 20 should work
        usingService.createTreatmentPlan(user2.id, UsingCreateDTO(drug.id, 20.0))

        assertEquals(50.0, transaction(db) { drugRepository.sumPlannedAmount(drug.id) })

        println("✅ Story 9 passed: Cannot over-plan drug quantity")
    }

    /**
     * Story 10: Complete family medkit lifecycle
     */
    @Test
    fun `Story 10 - Complete family medkit lifecycle`() {
        // Mom creates a family medkit
        val mom = User(id = UUID.randomUUID(), hashedKey = "mom_${UUID.randomUUID()}")
        val dad = User(id = UUID.randomUUID(), hashedKey = "dad_${UUID.randomUUID()}")
        val child = User(id = UUID.randomUUID(), hashedKey = "child_${UUID.randomUUID()}")
        transaction(db) {
            userRepository.save(mom)
            userRepository.save(dad)
            userRepository.save(child)
        }

        val familyKit = medKitService.createNew(mom.id)
        val dadKey = medKitService.generateMedKitShareKey(familyKit.id, mom.id)
        medKitService.joinMedKitByKey(dadKey, dad.id)
        val childKey = medKitService.generateMedKitShareKey(familyKit.id, mom.id)
        medKitService.joinMedKitByKey(childKey, child.id)

        // Add family medications
        val aspirin = Drug(
            id = UUID.randomUUID(), name = "Children's Aspirin",
            quantity = 200.0, quantityUnit = "tablets", formType = "chewable",
            category = "painkiller", manufacturer = null, country = null,
            description = null, medKitId = familyKit.id
        )
        val vitamins = Drug(
            id = UUID.randomUUID(), name = "Multivitamins",
            quantity = 90.0, quantityUnit = "tablets", formType = "tablet",
            category = "supplement", manufacturer = null, country = null,
            description = null, medKitId = familyKit.id
        )
        transaction(db) {
            drugRepository.save(aspirin)
            drugRepository.save(vitamins)
        }

        // Everyone gets treatment plans for vitamins: 30 each
        usingService.createTreatmentPlan(mom.id, UsingCreateDTO(vitamins.id, 30.0))
        usingService.createTreatmentPlan(dad.id, UsingCreateDTO(vitamins.id, 30.0))
        usingService.createTreatmentPlan(child.id, UsingCreateDTO(vitamins.id, 30.0))

        // Total planned = 90 (full supply)
        assertEquals(90.0, transaction(db) { drugRepository.sumPlannedAmount(vitamins.id) })

        // Everyone takes their daily vitamin
        usingService.recordIntake(mom.id, vitamins.id, 1.0)
        usingService.recordIntake(dad.id, vitamins.id, 1.0)
        usingService.recordIntake(child.id, vitamins.id, 1.0)

        // Check vitamins after 1 day
        val updatedVitamins = transaction(db) { drugRepository.findById(vitamins.id) }
        assertNotNull(updatedVitamins)
        assertEquals(87.0, updatedVitamins.quantity, "Should be 90 - 3 = 87")

        // 3 users in the medkit
        val userCount = transaction(db) { medKitRepository.countUsersInMedKit(familyKit.id) }
        assertEquals(3L, userCount)

        // Child leaves the medkit
        medKitDrugServices.removeUserFromMedKit(familyKit.id, child.id)

        // Medkit still has mom and dad
        val updatedCount = transaction(db) { medKitRepository.countUsersInMedKit(familyKit.id) }
        assertEquals(2L, updatedCount)

        println("✅ Story 10 passed: Complete family medkit lifecycle")
    }

    /**
     * Story 11: Moving drugs between medkits preserves treatment plans
     */
    @Test
    fun `Story 11 - Moving drug between medkits`() {
        val user = User(id = UUID.randomUUID(), hashedKey = "user_${UUID.randomUUID()}")
        transaction(db) { userRepository.save(user) }

        val homeKit = medKitService.createNew(user.id)
        val travelKit = medKitService.createNew(user.id)

        val painkiller = Drug(
            id = UUID.randomUUID(), name = "Ibuprofen",
            quantity = 60.0, quantityUnit = "tablets", formType = "tablet",
            category = "painkiller", manufacturer = null, country = null,
            description = null, medKitId = homeKit.id
        )
        transaction(db) { drugRepository.save(painkiller) }

        // Create treatment plan
        usingService.createTreatmentPlan(user.id, UsingCreateDTO(painkiller.id, 20.0))

        // Move drug to travel kit
        drugService.moveDrug(painkiller.id, travelKit.id, user.id)

        // Drug is in travel kit
        val movedDrug = transaction(db) { drugRepository.findById(painkiller.id) }
        assertNotNull(movedDrug)
        assertEquals(travelKit.id, movedDrug.medKitId)

        // Home kit is empty
        val homeKitDrugs = transaction(db) { drugRepository.findAllByMedKitId(homeKit.id) }
        assertTrue(homeKitDrugs.isEmpty())

        // Travel kit has the drug
        val travelKitDrugs = transaction(db) { drugRepository.findAllByMedKitId(travelKit.id) }
        assertEquals(1, travelKitDrugs.size)

        // Treatment plan still exists
        val plan = transaction(db) { usingRepository.findByUserIdAndDrugId(user.id, painkiller.id) }
        assertNotNull(plan, "Treatment plan should survive drug move")
        assertEquals(20.0, plan.plannedAmount)

        println("✅ Story 11 passed: Drug moved between medkits with treatment plan intact")
    }

    /**
     * Story 12: Update treatment plan correctly checks available quantity
     */
    @Test
    fun `Story 12 - Updating treatment plan correctly checks available quantity`() {
        val anna = User(id = UUID.randomUUID(), hashedKey = "anna_${UUID.randomUUID()}")
        val bob = User(id = UUID.randomUUID(), hashedKey = "bob_${UUID.randomUUID()}")
        transaction(db) {
            userRepository.save(anna)
            userRepository.save(bob)
        }

        val medkit = medKitService.createNew(anna.id)
        val shareKey = medKitService.generateMedKitShareKey(medkit.id, anna.id)
        medKitService.joinMedKitByKey(shareKey, bob.id)

        val drug = Drug(
            id = UUID.randomUUID(), name = "Medicine X",
            quantity = 100.0, quantityUnit = "ml", formType = "liquid",
            category = null, manufacturer = null, country = null,
            description = null, medKitId = medkit.id
        )
        transaction(db) { drugRepository.save(drug) }

        // Anna plans 40, Bob plans 30 (total 70, available 30)
        usingService.createTreatmentPlan(anna.id, UsingCreateDTO(drug.id, 40.0))
        usingService.createTreatmentPlan(bob.id, UsingCreateDTO(drug.id, 30.0))

        // Anna should be able to increase her plan to 70 (available for her = 100 - 30 (bob) = 70)
        val updated = usingService.updateTreatmentPlan(
            anna.id, drug.id,
            org.kert0n.medappserver.controller.UsingUpdateDTO(70.0)
        )
        assertEquals(70.0, updated.plannedAmount)

        // Total planned should now be 100 (70 + 30)
        assertEquals(100.0, transaction(db) { drugRepository.sumPlannedAmount(drug.id) })

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
     */
    @Test
    fun `Story 13 - Deleting drug removes its treatment plans`() {
        val user = User(id = UUID.randomUUID(), hashedKey = "user_${UUID.randomUUID()}")
        transaction(db) { userRepository.save(user) }

        val medkit = medKitService.createNew(user.id)
        val drug = Drug(
            id = UUID.randomUUID(), name = "Expired Drug",
            quantity = 50.0, quantityUnit = "tablets", formType = null,
            category = null, manufacturer = null, country = null,
            description = null, medKitId = medkit.id
        )
        transaction(db) { drugRepository.save(drug) }

        // Create treatment plan
        usingService.createTreatmentPlan(user.id, UsingCreateDTO(drug.id, 25.0))

        // Verify plan exists
        val plan = transaction(db) { usingRepository.findByUserIdAndDrugId(user.id, drug.id) }
        assertNotNull(plan)

        // Delete the drug
        drugService.delete(drug.id, user.id)

        // Drug should be gone
        val deletedDrug = transaction(db) { drugRepository.findById(drug.id) }
        assertNull(deletedDrug)

        // Treatment plan should also be gone (cascade)
        val deletedPlan = transaction(db) { usingRepository.findByUserIdAndDrugId(user.id, drug.id) }
        assertNull(deletedPlan)

        println("✅ Story 13 passed: Deleting drug removed its treatment plans")
    }
}
