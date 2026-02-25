package org.kert0n.medappserver.services

import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.kert0n.medappserver.controller.DrugUpdateDTO
import org.kert0n.medappserver.controller.UsingCreateDTO
import org.kert0n.medappserver.db.model.Drug
import org.kert0n.medappserver.db.model.MedKit
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
class MedKitServiceTest {

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
}