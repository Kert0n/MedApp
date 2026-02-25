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
import org.kert0n.medappserver.services.orchestrators.MedKitDrugServices
import org.kert0n.medappserver.testutil.DatabaseTestHelper
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.repository.findByIdOrNull
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull


@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MedKitDrugServiceTest {

    @Autowired
    private lateinit var userRepository: UserRepository
    @Autowired
    private lateinit var drugRepository: DrugRepository
    @Autowired
    private lateinit var usingRepository: UsingRepository
    @Autowired
    private lateinit var medKitDrugServices: MedKitDrugServices
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
    fun `MedKitDrugServices - Delete without transfer`() {
        val alice = dbHelper.freshUser("alice")
        val kit = medKitService.createNew(alice.id)
        dbHelper.freshDrug(kit, 10.0)
        dbHelper.flushAndClear()

        // Covers line 62: if (transferToMedKitId != null) bypassing the true block
        medKitDrugServices.delete(kit.id, alice.id, null)
        dbHelper.flushAndClear()

        // Assert it was deleted
        assertThrows<ResponseStatusException> {
            medKitService.findByIdForUser(kit.id, alice.id)
        }
    }


}