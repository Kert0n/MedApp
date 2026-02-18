package org.kert0n.medappserver.services

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.kert0n.medappserver.db.model.*
import org.kert0n.medappserver.db.repository.DrugRepository
import org.kert0n.medappserver.db.repository.MedKitRepository
import org.kert0n.medappserver.db.repository.UsingRepository
import org.mockito.kotlin.*
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.util.*

class DrugServiceTest {

    private lateinit var drugRepository: DrugRepository
    private lateinit var medKitRepository: MedKitRepository
    private lateinit var usingRepository: UsingRepository
    private lateinit var drugService: DrugService

    private lateinit var testUser: User
    private lateinit var testMedKit: MedKit
    private lateinit var testDrug: Drug

    @BeforeEach
    fun setup() {
        drugRepository = mock()
        medKitRepository = mock()
        usingRepository = mock()
        drugService = DrugService(drugRepository, medKitRepository, usingRepository)

        testUser = User(id = UUID.randomUUID(), hashedKey = "test-hash")
        testMedKit = MedKit().apply {
            users.add(testUser)
        }
        testUser.medKits.add(testMedKit)
        
        testDrug = Drug(
            name = "Test Drug",
            quantity = 100.0,
            quantityUnit = "mg",
            formType = "tablet",
            category = null,
            manufacturer = null,
            country = null,
            description = null,
            medKit = testMedKit
        )
    }

    @Test
    fun `findByIdForUser should return drug when user has access`() {
        whenever(drugRepository.findByIdAndMedKitUserId(testDrug.id, testUser.id))
            .thenReturn(testDrug)

        val result = drugService.findByIdForUser(testDrug.id, testUser.id)

        assertEquals(testDrug, result)
    }

    @Test
    fun `findByIdForUser should throw exception when user has no access`() {
        val unauthorizedUserId = UUID.randomUUID()
        whenever(drugRepository.findByIdAndMedKitUserId(testDrug.id, unauthorizedUserId))
            .thenReturn(null)

        val exception = assertThrows<ResponseStatusException> {
            drugService.findByIdForUser(testDrug.id, unauthorizedUserId)
        }
        assertEquals(HttpStatus.NOT_FOUND, exception.statusCode)
    }

    @Test
    fun `create should successfully create drug`() {
        val createDTO = DrugCreateDTO(
            name = "New Drug",
            quantity = 50.0,
            quantityUnit = "ml",
            medKitId = testMedKit.id
        )

        whenever(medKitRepository.findById(testMedKit.id)).thenReturn(Optional.of(testMedKit))
        whenever(drugRepository.save(any<Drug>())).thenAnswer { it.arguments[0] }

        val result = drugService.create(createDTO, testUser.id)

        assertEquals(createDTO.name, result.name)
        assertEquals(createDTO.quantity, result.quantity)
    }

    @Test
    fun `create should throw exception when medkit not found`() {
        val createDTO = DrugCreateDTO(
            name = "New Drug",
            quantity = 50.0,
            quantityUnit = "ml",
            medKitId = UUID.randomUUID()
        )

        whenever(medKitRepository.findById(any())).thenReturn(Optional.empty())

        assertThrows<ResponseStatusException> {
            drugService.create(createDTO, testUser.id)
        }
    }

    @Test
    fun `consumeDrug should reduce quantity`() {
        val consumeAmount = 20.0
        val initialQuantity = testDrug.quantity

        whenever(drugRepository.findByIdAndMedKitUserId(testDrug.id, testUser.id))
            .thenReturn(testDrug)
        whenever(drugRepository.save(any<Drug>())).thenAnswer { it.arguments[0] }

        drugService.consumeDrug(testDrug.id, consumeAmount, testUser.id)

        assertEquals(initialQuantity - consumeAmount, testDrug.quantity)
    }

    @Test
    fun `consumeDrug should throw exception when quantity insufficient`() {
        val excessiveAmount = testDrug.quantity + 10.0

        whenever(drugRepository.findByIdAndMedKitUserId(testDrug.id, testUser.id))
            .thenReturn(testDrug)

        assertThrows<ResponseStatusException> {
            drugService.consumeDrug(testDrug.id, excessiveAmount, testUser.id)
        }
    }

    @Test
    fun `getPlannedQuantity should return sum of all usings`() {
        val user1 = User(id = UUID.randomUUID(), hashedKey = "user1")
        val user2 = User(id = UUID.randomUUID(), hashedKey = "user2")

        testDrug.usings.add(Using(
            user = user1,
            drug = testDrug,
            plannedAmount = 30.0
        ))
        testDrug.usings.add(Using(
            user = user2,
            drug = testDrug,
            plannedAmount = 20.0
        ))

        val result = drugService.getPlannedQuantity(testDrug)

        assertEquals(50.0, result)
    }

    @Test
    fun `getPlannedQuantity should return zero when no usings`() {
        val result = drugService.getPlannedQuantity(testDrug)

        assertEquals(0.0, result)
    }
}
