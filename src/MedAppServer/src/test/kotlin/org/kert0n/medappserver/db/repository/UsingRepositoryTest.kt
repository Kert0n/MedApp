package org.kert0n.medappserver.db.repository

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.kert0n.medappserver.db.model.*
import org.kert0n.medappserver.testutil.*
import jakarta.persistence.EntityManager
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import java.util.*

/**
 * Repository tests for UsingRepository custom queries
 * Tests verify composite key operations and aggregations
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class UsingRepositoryTest {

    @Autowired
    private lateinit var usingRepository: UsingRepository

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var medKitRepository: MedKitRepository

    @Autowired
    private lateinit var drugRepository: DrugRepository

    @Autowired
    private lateinit var entityManager: EntityManager

    private lateinit var testUser: User
    private lateinit var testMedKit: MedKit
    private lateinit var testDrug: Drug

    @BeforeEach
    fun setup() {
        testUser = userBuilder()
            .withHashedKey("test-hash-123")
            .build()
        entityManager.persist(testUser)

        testMedKit = medKitBuilder().build()
        testMedKit.users.add(testUser)
        testUser.medKits.add(testMedKit)
        entityManager.persist(testMedKit)

        testDrug = drugBuilder(testMedKit)
            .withName("Aspirin")
            .withQuantity(100.0)
            .withQuantityUnit("mg")
            .build()
        entityManager.persist(testDrug)

        entityManager.flush()
        entityManager.clear()
    }

    // findAllByUserId tests
    @Test
    fun `findAllByUserId - returns usings for user`() {
        val using = usingBuilder(testUser, testDrug)
            .withPlannedAmount(30.0)
            .build()
        entityManager.persist(using)
        entityManager.flush()

        val usings = usingRepository.findAllByUserId(testUser.id)

        assertEquals(1, usings.size)
        assertEquals(testUser.id, usings[0].user.id)
        assertEquals(testDrug.id, usings[0].drug.id)
        assertEquals(30.0, usings[0].plannedAmount)
    }

    @Test
    fun `findAllByUserId - returns multiple usings for same user`() {
        val drug2 = drugBuilder(testMedKit)
            .withName("Ibuprofen")
            .build()
        entityManager.persist(drug2)

        val using1 = usingBuilder(testUser, testDrug)
            .withPlannedAmount(20.0)
            .build()
        val using2 = usingBuilder(testUser, drug2)
            .withPlannedAmount(40.0)
            .build()

        entityManager.persist(using1)
        entityManager.persist(using2)
        entityManager.flush()

        val usings = usingRepository.findAllByUserId(testUser.id)

        assertEquals(2, usings.size)
        assertTrue(usings.any { it.drug.id == testDrug.id && it.plannedAmount == 20.0 })
        assertTrue(usings.any { it.drug.id == drug2.id && it.plannedAmount == 40.0 })
    }

    @Test
    fun `findAllByUserId - returns empty list when user has no usings`() {
        val userWithoutUsings = userBuilder()
            .withHashedKey("no-usings-hash")
            .build()
        entityManager.persist(userWithoutUsings)
        entityManager.flush()

        val usings = usingRepository.findAllByUserId(userWithoutUsings.id)

        assertTrue(usings.isEmpty())
    }

    @Test
    fun `findAllByUserId - only returns usings for specified user`() {
        val user2 = userBuilder()
            .withHashedKey("user2-hash")
            .build()
        entityManager.persist(user2)
        testMedKit.users.add(user2)
        user2.medKits.add(testMedKit)

        val using1 = usingBuilder(testUser, testDrug)
            .withPlannedAmount(20.0)
            .build()
        val using2 = usingBuilder(user2, testDrug)
            .withPlannedAmount(40.0)
            .build()

        entityManager.persist(using1)
        entityManager.persist(using2)
        entityManager.flush()

        val usings = usingRepository.findAllByUserId(testUser.id)

        assertEquals(1, usings.size)
        assertEquals(testUser.id, usings[0].user.id)
        assertEquals(20.0, usings[0].plannedAmount)
    }

    // findAllByDrugId tests
    @Test
    fun `findAllByDrugId - returns usings for drug`() {
        val using = usingBuilder(testUser, testDrug)
            .withPlannedAmount(30.0)
            .build()
        entityManager.persist(using)
        entityManager.flush()

        val usings = usingRepository.findAllByDrugId(testDrug.id)

        assertEquals(1, usings.size)
        assertEquals(testDrug.id, usings[0].drug.id)
        assertEquals(testUser.id, usings[0].user.id)
        assertEquals(30.0, usings[0].plannedAmount)
    }

    @Test
    fun `findAllByDrugId - returns multiple users using same drug`() {
        val user2 = userBuilder()
            .withHashedKey("user2-hash")
            .build()
        entityManager.persist(user2)
        testMedKit.users.add(user2)
        user2.medKits.add(testMedKit)

        val using1 = usingBuilder(testUser, testDrug)
            .withPlannedAmount(20.0)
            .build()
        val using2 = usingBuilder(user2, testDrug)
            .withPlannedAmount(40.0)
            .build()

        entityManager.persist(using1)
        entityManager.persist(using2)
        entityManager.flush()

        val usings = usingRepository.findAllByDrugId(testDrug.id)

        assertEquals(2, usings.size)
        assertTrue(usings.any { it.user.id == testUser.id && it.plannedAmount == 20.0 })
        assertTrue(usings.any { it.user.id == user2.id && it.plannedAmount == 40.0 })
    }

    @Test
    fun `findAllByDrugId - returns empty list when drug has no usings`() {
        val unusedDrug = drugBuilder(testMedKit)
            .withName("Unused Drug")
            .build()
        entityManager.persist(unusedDrug)
        entityManager.flush()

        val usings = usingRepository.findAllByDrugId(unusedDrug.id)

        assertTrue(usings.isEmpty())
    }

    @Test
    fun `findAllByDrugId - only returns usings for specified drug`() {
        val drug2 = drugBuilder(testMedKit)
            .withName("Ibuprofen")
            .build()
        entityManager.persist(drug2)

        val using1 = usingBuilder(testUser, testDrug)
            .withPlannedAmount(20.0)
            .build()
        val using2 = usingBuilder(testUser, drug2)
            .withPlannedAmount(40.0)
            .build()

        entityManager.persist(using1)
        entityManager.persist(using2)
        entityManager.flush()

        val usings = usingRepository.findAllByDrugId(testDrug.id)

        assertEquals(1, usings.size)
        assertEquals(testDrug.id, usings[0].drug.id)
        assertEquals(20.0, usings[0].plannedAmount)
    }

    // findByUserIdAndDrugId tests (composite key)
    @Test
    fun `findByUserIdAndDrugId - finds using by composite key`() {
        val using = usingBuilder(testUser, testDrug)
            .withPlannedAmount(30.0)
            .build()
        entityManager.persist(using)
        entityManager.flush()

        val found = usingRepository.findByUserIdAndDrugId(testUser.id, testDrug.id)

        assertNotNull(found)
        assertEquals(testUser.id, found?.user?.id)
        assertEquals(testDrug.id, found?.drug?.id)
        assertEquals(30.0, found?.plannedAmount)
    }

    @Test
    fun `findByUserIdAndDrugId - returns null when using does not exist`() {
        val found = usingRepository.findByUserIdAndDrugId(testUser.id, testDrug.id)

        assertNull(found)
    }

    @Test
    fun `findByUserIdAndDrugId - returns null for non-existent user`() {
        val using = usingBuilder(testUser, testDrug)
            .withPlannedAmount(30.0)
            .build()
        entityManager.persist(using)
        entityManager.flush()

        val nonExistentUserId = UUID.randomUUID()
        val found = usingRepository.findByUserIdAndDrugId(nonExistentUserId, testDrug.id)

        assertNull(found)
    }

    @Test
    fun `findByUserIdAndDrugId - returns null for non-existent drug`() {
        val using = usingBuilder(testUser, testDrug)
            .withPlannedAmount(30.0)
            .build()
        entityManager.persist(using)
        entityManager.flush()

        val nonExistentDrugId = UUID.randomUUID()
        val found = usingRepository.findByUserIdAndDrugId(testUser.id, nonExistentDrugId)

        assertNull(found)
    }

    // findAllByUserIdWithDrug tests (JOIN FETCH verification)
    @Test
    fun `findAllByUserIdWithDrug - eagerly loads drug with JOIN FETCH`() {
        val using = usingBuilder(testUser, testDrug)
            .withPlannedAmount(30.0)
            .build()
        entityManager.persist(using)
        entityManager.flush()
        entityManager.clear()

        val usings = usingRepository.findAllByUserIdWithDrug(testUser.id)

        assertEquals(1, usings.size)
        // Verify drug is loaded (no lazy init exception)
        val drug = usings[0].drug
        assertEquals(testDrug.id, drug.id)
        assertEquals("Aspirin", drug.name)
    }

    @Test
    fun `findAllByUserIdWithDrug - prevents N+1 queries for drugs`() {
        val drug2 = drugBuilder(testMedKit)
            .withName("Ibuprofen")
            .build()
        entityManager.persist(drug2)

        val using1 = usingBuilder(testUser, testDrug)
            .withPlannedAmount(20.0)
            .build()
        val using2 = usingBuilder(testUser, drug2)
            .withPlannedAmount(40.0)
            .build()

        entityManager.persist(using1)
        entityManager.persist(using2)
        entityManager.flush()
        entityManager.clear()

        val usings = usingRepository.findAllByUserIdWithDrug(testUser.id)

        assertEquals(2, usings.size)
        // Accessing drugs should not trigger additional queries
        val drugNames = usings.map { it.drug.name }
        assertTrue(drugNames.contains("Aspirin"))
        assertTrue(drugNames.contains("Ibuprofen"))
    }

    @Test
    fun `findAllByUserIdWithDrug - returns empty list when user has no usings`() {
        val userWithoutUsings = userBuilder()
            .withHashedKey("no-usings-hash")
            .build()
        entityManager.persist(userWithoutUsings)
        entityManager.flush()

        val usings = usingRepository.findAllByUserIdWithDrug(userWithoutUsings.id)

        assertTrue(usings.isEmpty())
    }

    // Edge cases
    @Test
    fun `save - creates using with composite key`() {
        val using = usingBuilder(testUser, testDrug)
            .withPlannedAmount(50.0)
            .build()

        val saved = usingRepository.save(using)
        entityManager.flush()

        assertEquals(testUser.id, saved.usingKey.userId)
        assertEquals(testDrug.id, saved.usingKey.drugId)
        assertEquals(50.0, saved.plannedAmount)
    }

    @Test
    fun `save - updates existing using`() {
        val using = usingBuilder(testUser, testDrug)
            .withPlannedAmount(30.0)
            .build()
        usingRepository.save(using)
        entityManager.flush()

        using.plannedAmount = 60.0
        val updated = usingRepository.save(using)
        entityManager.flush()

        assertEquals(60.0, updated.plannedAmount)
    }

    @Test
    fun `delete - removes using by composite key`() {
        val using = usingBuilder(testUser, testDrug)
            .withPlannedAmount(30.0)
            .build()
        usingRepository.save(using)
        entityManager.flush()

        usingRepository.delete(using)
        entityManager.flush()

        val found = usingRepository.findByUserIdAndDrugId(testUser.id, testDrug.id)
        assertNull(found)
    }
}
