package org.kert0n.medappserver.integration

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.kert0n.medappserver.services.MedKitDrugServices
import org.kert0n.medappserver.controller.DrugCreateDTO
import org.kert0n.medappserver.controller.DrugUpdateDTO
import org.kert0n.medappserver.controller.UsingCreateDTO
import org.kert0n.medappserver.controller.UsingUpdateDTO
import org.kert0n.medappserver.db.model.User
import org.kert0n.medappserver.db.repository.UserRepository
import org.kert0n.medappserver.db.table.*
import org.kert0n.medappserver.services.DrugService
import org.kert0n.medappserver.services.MedKitService
import org.kert0n.medappserver.services.UsingService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.web.server.ResponseStatusException
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@SpringBootTest
@ActiveProfiles("test")
class ServiceIntegrationTests() {

    @Autowired
    private lateinit var medKitDrugServices: MedKitDrugServices
    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var drugService: DrugService

    @Autowired
    private lateinit var medKitService: MedKitService

    @Autowired
    private lateinit var usingService: UsingService

    @Autowired
    private lateinit var db: Database

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

    private fun createUser(): User {
        return transaction(db) {
            userRepository.save(User(id = UUID.randomUUID(), hashedKey = "key_${UUID.randomUUID()}"))
        }
    }

    // === DrugService Tests ===

    @Test
    fun `DrugService - create drug in user medkit`() {
        val user = createUser()
        val medKit = medKitService.createNew(user.id)

        val drug = drugService.create(
            DrugCreateDTO(
                name = "Aspirin",
                quantity = 100.0,
                quantityUnit = "mg",
                medKitId = medKit.id
            ),
            medKit.id,
            user.id
        )

        assertNotNull(drug.id)
        assertEquals("Aspirin", drug.name)
        assertEquals(100.0, drug.quantity)
        assertEquals(medKit.id, drug.medKitId)
    }

    @Test
    fun `DrugService - create drug fails for unauthorized user`() {
        val user1 = createUser()
        val user2 = createUser()
        val medKit = medKitService.createNew(user1.id)

        assertFailsWith<ResponseStatusException> {
            medKitDrugServices.createDrugInMedkit(
                DrugCreateDTO(
                    name = "Aspirin",
                    quantity = 100.0,
                    quantityUnit = "mg",
                    medKitId = medKit.id
                ),
                user2.id
            )
        }
    }

    @Test
    fun `DrugService - update drug fields`() {
        val user = createUser()
        val medKit = medKitService.createNew(user.id)
        val drug = drugService.create(
            DrugCreateDTO(name = "Old Name", quantity = 50.0, quantityUnit = "mg", medKitId = medKit.id),
            medKit.id,
            user.id
        )

        val updated = drugService.update(
            drug.id,
            DrugUpdateDTO(name = "New Name", quantity = 75.0),
            user.id
        )

        assertEquals("New Name", updated.name)
        assertEquals(75.0, updated.quantity)
    }

    @Test
    fun `DrugService - consume drug reduces quantity`() {
        val user = createUser()
        val medKit = medKitService.createNew(user.id)
        val drug = drugService.create(
            DrugCreateDTO(name = "Drug", quantity = 100.0, quantityUnit = "mg", medKitId = medKit.id),
            medKit.id,
            user.id
        )

        val consumed = drugService.consumeDrug(drug.id, 30.0, user.id)
        assertEquals(70.0, consumed.quantity)
    }

    @Test
    fun `DrugService - consume drug fails when insufficient quantity`() {
        val user = createUser()
        val medKit = medKitService.createNew(user.id)
        val drug = drugService.create(
            DrugCreateDTO(name = "Drug", quantity = 10.0, quantityUnit = "mg", medKitId = medKit.id),
            medKit.id,
            user.id
        )

        assertFailsWith<ResponseStatusException> {
            drugService.consumeDrug(drug.id, 20.0, user.id)
        }
    }

    @Test
    fun `DrugService - move drug to another medkit`() {
        val user = createUser()
        val medKit1 = medKitService.createNew(user.id)
        val medKit2 = medKitService.createNew(user.id)
        val drug = drugService.create(
            DrugCreateDTO(name = "Drug", quantity = 50.0, quantityUnit = "mg", medKitId = medKit1.id),
            medKit1.id,
            user.id
        )

        val moved = drugService.moveDrug(drug.id, medKit2.id, user.id)
        assertEquals(medKit2.id, moved.medKitId)
    }

    @Test
    fun `DrugService - delete drug`() {
        val user = createUser()
        val medKit = medKitService.createNew(user.id)
        val drug = drugService.create(
            DrugCreateDTO(name = "Drug", quantity = 50.0, quantityUnit = "mg", medKitId = medKit.id),
            medKit.id,
            user.id
        )

        drugService.delete(drug.id, user.id)

        assertFailsWith<ResponseStatusException> {
            drugService.findById(drug.id)
        }
    }

    @Test
    fun `DrugService - getPlannedQuantity returns correct sum`() {
        val user = createUser()
        val medKit = medKitService.createNew(user.id)
        val drug = drugService.create(
            DrugCreateDTO(name = "Drug", quantity = 100.0, quantityUnit = "mg", medKitId = medKit.id),
            medKit.id,
            user.id
        )

        // No treatment plans yet
        assertEquals(0.0, drugService.getPlannedQuantity(drug.id))

        // Create treatment plan
        usingService.createTreatmentPlan(user.id, UsingCreateDTO(drug.id, 30.0))

        assertEquals(30.0, drugService.getPlannedQuantity(drug.id))
    }

    @Test
    fun `DrugService - toDrugDTO includes planned quantity`() {
        val user = createUser()
        val medKit = medKitService.createNew(user.id)
        val drug = drugService.create(
            DrugCreateDTO(name = "Drug", quantity = 100.0, quantityUnit = "mg", medKitId = medKit.id),
            medKit.id,
            user.id
        )
        usingService.createTreatmentPlan(user.id, UsingCreateDTO(drug.id, 25.0))

        val dto = drugService.toDrugDTO(drug)
        assertEquals(25.0, dto.plannedQuantity)
        assertEquals(100.0, dto.quantity)
    }

    // === MedKitService Tests ===

    @Test
    fun `MedKitService - createNew creates medkit with user`() {
        val user = createUser()
        val medKit = medKitService.createNew(user.id)

        assertNotNull(medKit.id)
        val userMedKits = medKitService.findAllByUser(user.id)
        assertTrue(userMedKits.any { it.id == medKit.id })
    }

    @Test
    fun `MedKitService - addUserToMedKit adds second user`() {
        val user1 = createUser()
        val user2 = createUser()
        val medKit = medKitService.createNew(user1.id)

        medKitService.addUserToMedKit(medKit.id, user2.id)

        val medKits1 = medKitService.findAllByUser(user1.id)
        val medKits2 = medKitService.findAllByUser(user2.id)
        assertEquals(1, medKits1.size)
        assertEquals(1, medKits2.size)
        assertEquals(medKits1[0].id, medKits2[0].id)
    }

    @Test
    fun `MedKitService - joinMedKitByKey adds user and invalidates key`() {
        val owner = createUser()
        val joiner = createUser()
        val medKit = medKitService.createNew(owner.id)

        val shareKey = medKitService.generateMedKitShareKey(medKit.id, owner.id)
        medKitService.joinMedKitByKey(shareKey, joiner.id)

        val joinerMedKits = medKitService.findAllByUser(joiner.id)
        assertEquals(1, joinerMedKits.size)
        assertEquals(medKit.id, joinerMedKits.first().id)

        assertFailsWith<ResponseStatusException> {
            medKitService.joinMedKitByKey(shareKey, joiner.id)
        }
    }

    @Test
    fun `MedKitService - joinMedKitByKey fails for missing key`() {
        val user = createUser()

        assertFailsWith<ResponseStatusException> {
            medKitService.joinMedKitByKey("missing-key", user.id)
        }
    }

    @Test
    fun `MedKitService - findByIdForUser throws when user has no access`() {
        val user1 = createUser()
        val user2 = createUser()
        val medKit = medKitService.createNew(user1.id)

        assertFailsWith<ResponseStatusException> {
            medKitService.findByIdForUser(medKit.id, user2.id)
        }
    }

    @Test
    fun `MedKitService - removeUserFromMedKit keeps medkit when other users remain`() {
        val user1 = createUser()
        val user2 = createUser()
        val medKit = medKitService.createNew(user1.id)
        medKitService.addUserToMedKit(medKit.id, user2.id)

        medKitDrugServices.removeUserFromMedKit(medKit.id, user2.id)

        // medKit should still exist with user1
        val found = medKitService.findByIdForUser(medKit.id, user1.id)
        assertNotNull(found)

        // user2 should not have access
        assertFailsWith<ResponseStatusException> {
            medKitService.findByIdForUser(medKit.id, user2.id)
        }
    }

    @Test
    fun `MedKitService - toMedKitDTO returns correct DTO`() {
        val user = createUser()
        val medKit = medKitService.createNew(user.id)
        drugService.create(
            DrugCreateDTO(name = "Drug A", quantity = 50.0, quantityUnit = "mg", medKitId = medKit.id),
            medKit.id,
            user.id
        )
        drugService.create(
            DrugCreateDTO(name = "Drug B", quantity = 30.0, quantityUnit = "tablets", medKitId = medKit.id),
            medKit.id,
            user.id
        )

        val dto = medKitDrugServices.toMedKitDTO(medKit)
        assertEquals(medKit.id, dto.id)
        assertEquals(2, dto.drugs.size)
    }

    // === UsingService Tests ===

    @Test
    fun `UsingService - create treatment plan`() {
        val user = createUser()
        val medKit = medKitService.createNew(user.id)
        val drug = drugService.create(
            DrugCreateDTO(name = "Drug", quantity = 100.0, quantityUnit = "mg", medKitId = medKit.id),
            medKit.id,
            user.id
        )

        val using = usingService.createTreatmentPlan(user.id, UsingCreateDTO(drug.id, 30.0))
        assertEquals(30.0, using.plannedAmount)
        assertEquals(user.id, using.userId)
        assertEquals(drug.id, using.drugId)
    }

    @Test
    fun `UsingService - create treatment plan fails for duplicate`() {
        val user = createUser()
        val medKit = medKitService.createNew(user.id)
        val drug = drugService.create(
            DrugCreateDTO(name = "Drug", quantity = 100.0, quantityUnit = "mg", medKitId = medKit.id),
            medKit.id,
            user.id
        )

        usingService.createTreatmentPlan(user.id, UsingCreateDTO(drug.id, 30.0))

        assertFailsWith<ResponseStatusException> {
            usingService.createTreatmentPlan(user.id, UsingCreateDTO(drug.id, 20.0))
        }
    }

    @Test
    fun `UsingService - update treatment plan`() {
        val user = createUser()
        val medKit = medKitService.createNew(user.id)
        val drug = drugService.create(
            DrugCreateDTO(name = "Drug", quantity = 100.0, quantityUnit = "mg", medKitId = medKit.id),
            medKit.id,
            user.id
        )
        usingService.createTreatmentPlan(user.id, UsingCreateDTO(drug.id, 30.0))

        val updated = usingService.updateTreatmentPlan(user.id, drug.id, UsingUpdateDTO(50.0))
        assertEquals(50.0, updated.plannedAmount)
    }

    @Test
    fun `UsingService - record intake reduces both drug and plan`() {
        val user = createUser()
        val medKit = medKitService.createNew(user.id)
        val drug = drugService.create(
            DrugCreateDTO(name = "Drug", quantity = 100.0, quantityUnit = "mg", medKitId = medKit.id),
            medKit.id,
            user.id
        )
        usingService.createTreatmentPlan(user.id, UsingCreateDTO(drug.id, 30.0))

        val updatedUsing = usingService.recordIntake(user.id, drug.id, 10.0)
        assertNotNull(updatedUsing, "recordIntake should return a non-null Using when planned amount remains")
        assertEquals(20.0, updatedUsing.plannedAmount)

        val updatedDrug = drugService.findById(drug.id)
        assertEquals(90.0, updatedDrug.quantity)
    }

    @Test
    fun `UsingService - delete treatment plan`() {
        val user = createUser()
        val medKit = medKitService.createNew(user.id)
        val drug = drugService.create(
            DrugCreateDTO(name = "Drug", quantity = 100.0, quantityUnit = "mg", medKitId = medKit.id),
            medKit.id,
            user.id
        )
        usingService.createTreatmentPlan(user.id, UsingCreateDTO(drug.id, 30.0))

        usingService.deleteTreatmentPlan(user.id, drug.id)

        assertFailsWith<ResponseStatusException> {
            usingService.findByUserAndDrug(user.id, drug.id)
        }
    }

    @Test
    fun `UsingService - toUsingDTO returns correct DTO`() {
        val user = createUser()
        val medKit = medKitService.createNew(user.id)
        val drug = drugService.create(
            DrugCreateDTO(name = "Drug", quantity = 100.0, quantityUnit = "mg", medKitId = medKit.id),
            medKit.id,
            user.id
        )
        val using = usingService.createTreatmentPlan(user.id, UsingCreateDTO(drug.id, 30.0))

        val dto = usingService.toUsingDTO(using)
        assertEquals(user.id, dto.userId)
        assertEquals(drug.id, dto.drugId)
        assertEquals(30.0, dto.plannedAmount)
    }
}
