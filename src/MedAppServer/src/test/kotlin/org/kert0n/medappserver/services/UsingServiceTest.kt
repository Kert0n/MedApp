package org.kert0n.medappserver.services

import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
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
class UsingServiceTest {

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
    fun `UsingService - recordIntake exception branches`() {
        val alice = dbHelper.freshUser("alice")
        val kit = medKitService.createNew(alice.id)
        val drug = dbHelper.freshDrug(kit, 20.0)
        dbHelper.flushAndClear()

        usingService.createTreatmentPlan(alice.id, UsingCreateDTO(drug.id, 10.0))
        dbHelper.flushAndClear()

        // 1. Branch: Consumed > Planned
        val ex1 = assertThrows<ResponseStatusException> {
            usingService.recordIntake(alice.id, drug.id, 15.0)
        }
        assertTrue(ex1.reason!!.contains("exceeds planned amount"))

        // 2. Branch: Consumed > Drug Quantity
        // NOTE: Because of our strict invariant, Drug Quantity >= Planned Amount.
        // To hit this specific safety-net branch in testing, we have to artificially corrupt the DB state
        // to simulate a race condition or manual DB edit where stock vanished without plans scaling.
        val directDrug = drugService.findByIdForUserForUpdate(drug.id, alice.id)
        directDrug.quantity = 2.0
        // We don't update the plan, forcing corruption
        drugRepository.saveAndFlush(directDrug)

        val ex2 = assertThrows<ResponseStatusException> {
            usingService.recordIntake(alice.id, drug.id, 5.0) // 5 <= plan (10), but 5 > actual drug (2)
        }
        assertTrue(ex2.reason!!.contains("Insufficient drug quantity"))
    }
    @Test
    fun `UsingService - recordIntake exactly consuming planned amount deletes plan`() {
        val alice = dbHelper.freshUser("alice")
        val kit = medKitService.createNew(alice.id)
        val drug = dbHelper.freshDrug(kit, 20.0)
        dbHelper.flushAndClear()

        // Create a plan of 10.0
        usingService.createTreatmentPlan(alice.id, UsingCreateDTO(drug.id, 10.0))
        dbHelper.flushAndClear()

        // ACTION: Consume EXACTLY the planned amount (10.0)
        // plannedAmount becomes 0.0, hitting the `if (using.plannedAmount == 0.0)` true branch.
        val result = usingService.recordIntake(alice.id, drug.id, 10.0)
        dbHelper.flushAndClear()

        // VERIFICATION:
        // 1. The method must return null as dictated by the branch.
        assertNull(result, "recordIntake should return null when plan hits 0.0")

        // 2. The plan must be physically deleted from the database.
        assertNull(dbHelper.userPlan(alice.id, drug.id), "Plan should be completely removed from DB")

        // 3. The drug itself should still exist with the remaining quantity (20 - 10 = 10)
        assertEquals(10.0, dbHelper.drugQuantity(drug.id)!!, 0.001, "Drug should retain remaining unconsumed stock")
    }
    @Test
    fun `UsingService & DrugService - Orphan delegation methods`() {
        val alice = dbHelper.freshUser("alice")
        val kit = medKitService.createNew(alice.id)
        val drug = dbHelper.freshDrug(kit, 20.0)
        dbHelper.flushAndClear()

        usingService.createTreatmentPlan(alice.id, UsingCreateDTO(drug.id, 10.0))
        dbHelper.flushAndClear()

        // Hits UsingService.findAllByUser (0% -> 100%)
        assertEquals(1, usingService.findAllByUser(alice.id).size)

        // Hits DrugService.findAllByUser (0% -> 100%)
        assertEquals(1, drugService.findAllByUser(alice.id).size)

        // Hits UsingService.deleteAllByUserIdInMedkit (0% -> 100%)
        usingService.deleteAllByUserIdInMedkit(alice.id, kit.id)
        dbHelper.flushAndClear()

        assertEquals(0, usingService.findAllByUser(alice.id).size)
    }
}