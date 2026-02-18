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
 * Repository tests for DrugRepository custom queries
 * Tests verify query efficiency and JOIN FETCH behavior
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DrugRepositoryTest {

    @Autowired
    private lateinit var drugRepository: DrugRepository

    @Autowired
    private lateinit var medKitRepository: MedKitRepository

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var usingRepository: UsingRepository

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

    // findAllByMedKitId tests
    @Test
    fun `findAllByMedKitId - returns all drugs for medkit`() {
        val drug2 = drugBuilder(testMedKit)
            .withName("Ibuprofen")
            .build()
        entityManager.persist(drug2)
        entityManager.flush()
        entityManager.clear()

        val drugs = drugRepository.findAllByMedKitId(testMedKit.id)

        assertEquals(2, drugs.size)
        assertTrue(drugs.any { it.name == "Aspirin" })
        assertTrue(drugs.any { it.name == "Ibuprofen" })
    }

    @Test
    fun `findAllByMedKitId - empty list when no drugs`() {
        val emptyMedKit = medKitBuilder().build()
        entityManager.persist(emptyMedKit)
        entityManager.flush()

        val drugs = drugRepository.findAllByMedKitId(emptyMedKit.id)

        assertTrue(drugs.isEmpty())
    }

    @Test
    fun `findAllByMedKitId - only returns drugs from specific medkit`() {
        val otherMedKit = medKitBuilder().build()
        entityManager.persist(otherMedKit)

        val otherDrug = drugBuilder(otherMedKit)
            .withName("Other Drug")
            .build()
        entityManager.persist(otherDrug)
        entityManager.flush()

        val drugs = drugRepository.findAllByMedKitId(testMedKit.id)

        assertEquals(1, drugs.size)
        assertEquals("Aspirin", drugs[0].name)
    }

    @Test
    fun `findAllByMedKitId - returns empty list for non-existent medkit`() {
        val nonExistentId = UUID.randomUUID()

        val drugs = drugRepository.findAllByMedKitId(nonExistentId)

        assertTrue(drugs.isEmpty())
    }

    // findByUsingsUserId tests (JOIN FETCH verification)
    @Test
    fun `findByUsingsUserId - returns drugs with usings for user`() {
        val using = usingBuilder(testUser, testDrug)
            .withPlannedAmount(30.0)
            .build()
        testUser.usings.add(using)
        testDrug.usings.add(using)
        entityManager.persist(using)
        entityManager.flush()
        entityManager.clear()

        val drugs = drugRepository.findByUsingsUserId(testUser.id)

        assertEquals(1, drugs.size)
        assertEquals("Aspirin", drugs[0].name)
    }

    @Test
    fun `findByUsingsUserId - eagerly loads usings with JOIN FETCH`() {
        val using = usingBuilder(testUser, testDrug)
            .withPlannedAmount(30.0)
            .build()
        testUser.usings.add(using)
        testDrug.usings.add(using)
        entityManager.persist(using)
        entityManager.flush()
        entityManager.clear()

        val drugs = drugRepository.findByUsingsUserId(testUser.id)

        assertFalse(drugs.isEmpty())
        val drug = drugs[0]
        // Verify usings are loaded (no lazy init exception)
        assertFalse(drug.usings.isEmpty())
        assertEquals(30.0, drug.usings.first().plannedAmount)
    }

    @Test
    fun `findByUsingsUserId - eagerly loads user in usings with JOIN FETCH`() {
        val using = usingBuilder(testUser, testDrug)
            .withPlannedAmount(30.0)
            .build()
        testUser.usings.add(using)
        testDrug.usings.add(using)
        entityManager.persist(using)
        entityManager.flush()
        entityManager.clear()

        val drugs = drugRepository.findByUsingsUserId(testUser.id)

        assertFalse(drugs.isEmpty())
        val drug = drugs[0]
        // Verify user is loaded in usings (no lazy init exception)
        val loadedUser = drug.usings.first().user
        assertEquals(testUser.id, loadedUser.id)
    }

    @Test
    fun `findByUsingsUserId - returns empty list when user has no usings`() {
        val userWithoutUsings = userBuilder()
            .withHashedKey("another-hash")
            .build()
        entityManager.persist(userWithoutUsings)
        entityManager.flush()

        val drugs = drugRepository.findByUsingsUserId(userWithoutUsings.id)

        assertTrue(drugs.isEmpty())
    }

    @Test
    fun `findByUsingsUserId - handles multiple drugs for same user`() {
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

        val drugs = drugRepository.findByUsingsUserId(testUser.id)

        assertEquals(2, drugs.size)
        assertTrue(drugs.any { it.name == "Aspirin" })
        assertTrue(drugs.any { it.name == "Ibuprofen" })
    }

    // findByIdAndMedKitUsersId tests
    @Test
    fun `findByIdAndMedKitUsersId - finds drug when user has access via medkit`() {
        val drug = drugRepository.findByIdAndMedKitUsersId(testDrug.id, testUser.id)

        assertNotNull(drug)
        assertEquals(testDrug.id, drug?.id)
        assertEquals("Aspirin", drug?.name)
    }

    @Test
    fun `findByIdAndMedKitUsersId - returns null when user not in medkit`() {
        val otherUser = userBuilder()
            .withHashedKey("other-hash")
            .build()
        entityManager.persist(otherUser)
        entityManager.flush()

        val drug = drugRepository.findByIdAndMedKitUsersId(testDrug.id, otherUser.id)

        assertNull(drug)
    }

    @Test
    fun `findByIdAndMedKitUsersId - returns null for non-existent drug`() {
        val nonExistentId = UUID.randomUUID()

        val drug = drugRepository.findByIdAndMedKitUsersId(nonExistentId, testUser.id)

        assertNull(drug)
    }

    @Test
    fun `findByIdAndMedKitUsersId - returns null for non-existent user`() {
        val nonExistentUserId = UUID.randomUUID()

        val drug = drugRepository.findByIdAndMedKitUsersId(testDrug.id, nonExistentUserId)

        assertNull(drug)
    }

    @Test
    fun `findByIdAndMedKitUsersId - works with shared medkit`() {
        val user2 = userBuilder()
            .withHashedKey("user2-hash")
            .build()
        entityManager.persist(user2)
        testMedKit.users.add(user2)
        user2.medKits.add(testMedKit)
        entityManager.merge(testMedKit)
        entityManager.flush()

        val drug = drugRepository.findByIdAndMedKitUsersId(testDrug.id, user2.id)

        assertNotNull(drug)
        assertEquals(testDrug.id, drug?.id)
    }

    // findById override with EntityGraph tests
    @Test
    fun `findById - eagerly loads usings via EntityGraph`() {
        val using = usingBuilder(testUser, testDrug)
            .withPlannedAmount(30.0)
            .build()
        entityManager.persist(using)
        entityManager.flush()
        entityManager.clear()

        val optional = drugRepository.findById(testDrug.id)

        assertTrue(optional.isPresent)
        val drug = optional.get()
        // Verify usings are loaded (no lazy init exception)
        assertFalse(drug.usings.isEmpty())
    }

    @Test
    fun `findById - returns empty optional for non-existent id`() {
        val nonExistentId = UUID.randomUUID()

        val optional = drugRepository.findById(nonExistentId)

        assertFalse(optional.isPresent)
    }

    @Test
    fun `findById - loads empty usings collection when no usings exist`() {
        val optional = drugRepository.findById(testDrug.id)

        assertTrue(optional.isPresent)
        val drug = optional.get()
        assertTrue(drug.usings.isEmpty())
    }
}
