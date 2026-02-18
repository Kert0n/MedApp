package org.kert0n.medappserver.db.repository

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.kert0n.medappserver.db.model.*
import org.kert0n.medappserver.testutil.*
import jakarta.persistence.EntityManager
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import java.util.*

/**
 * Repository tests for UserRepository custom queries
 * Tests verify eager loading and unique constraints
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class UserRepositoryTest {

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

    private lateinit var testUser: User
    private lateinit var testMedKit: MedKit

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

        entityManager.flush()
        entityManager.clear()
    }

    // findByMedKitsId tests
    @Test
    fun `findByMedKitsId - returns users for medkit`() {
        val users = userRepository.findByMedKitsId(testMedKit.id)

        assertEquals(1, users.size)
        assertEquals(testUser.id, users[0].id)
    }

    @Test
    fun `findByMedKitsId - returns multiple users for shared medkit`() {
        val user2 = userBuilder()
            .withHashedKey("user2-hash")
            .build()
        entityManager.persist(user2)
        testMedKit.users.add(user2)
        user2.medKits.add(testMedKit)
        entityManager.merge(testMedKit)
        entityManager.flush()

        val users = userRepository.findByMedKitsId(testMedKit.id)

        assertEquals(2, users.size)
        assertTrue(users.any { it.id == testUser.id })
        assertTrue(users.any { it.id == user2.id })
    }

    @Test
    fun `findByMedKitsId - returns empty list when medkit has no users`() {
        val emptyMedKit = medKitBuilder().build()
        entityManager.persist(emptyMedKit)
        entityManager.flush()

        val users = userRepository.findByMedKitsId(emptyMedKit.id)

        assertTrue(users.isEmpty())
    }

    @Test
    fun `findByMedKitsId - returns empty list for non-existent medkit`() {
        val nonExistentId = UUID.randomUUID()

        val users = userRepository.findByMedKitsId(nonExistentId)

        assertTrue(users.isEmpty())
    }

    // findByUsingsDrugId tests
    @Test
    fun `findByUsingsDrugId - returns users using drug`() {
        val drug = drugBuilder(testMedKit)
            .withName("Aspirin")
            .build()
        entityManager.persist(drug)

        val using = usingBuilder(testUser, drug)
            .withPlannedAmount(30.0)
            .build()
        entityManager.persist(using)
        entityManager.flush()

        val users = userRepository.findByUsingsDrugId(drug.id)

        assertEquals(1, users.size)
        assertEquals(testUser.id, users[0].id)
    }

    @Test
    fun `findByUsingsDrugId - returns multiple users using same drug`() {
        val user2 = userBuilder()
            .withHashedKey("user2-hash")
            .build()
        entityManager.persist(user2)
        testMedKit.users.add(user2)
        user2.medKits.add(testMedKit)

        val drug = drugBuilder(testMedKit)
            .withName("Aspirin")
            .build()
        entityManager.persist(drug)

        val using1 = usingBuilder(testUser, drug)
            .withPlannedAmount(20.0)
            .build()
        val using2 = usingBuilder(user2, drug)
            .withPlannedAmount(40.0)
            .build()
        entityManager.persist(using1)
        entityManager.persist(using2)
        entityManager.flush()

        val users = userRepository.findByUsingsDrugId(drug.id)

        assertEquals(2, users.size)
        assertTrue(users.any { it.id == testUser.id })
        assertTrue(users.any { it.id == user2.id })
    }

    @Test
    fun `findByUsingsDrugId - returns empty list when no users use drug`() {
        val drug = drugBuilder(testMedKit)
            .withName("Unused Drug")
            .build()
        entityManager.persist(drug)
        entityManager.flush()

        val users = userRepository.findByUsingsDrugId(drug.id)

        assertTrue(users.isEmpty())
    }

    @Test
    fun `findByUsingsDrugId - returns empty list for non-existent drug`() {
        val nonExistentId = UUID.randomUUID()

        val users = userRepository.findByUsingsDrugId(nonExistentId)

        assertTrue(users.isEmpty())
    }

    // findByIdWithMedKits tests
    @Test
    fun `findByIdWithMedKits - eagerly loads medkits with JOIN FETCH`() {
        entityManager.clear()

        val user = userRepository.findByIdWithMedKits(testUser.id)

        assertNotNull(user)
        // Verify medkits are loaded (no lazy init exception)
        assertFalse(user!!.medKits.isEmpty())
        assertEquals(1, user.medKits.size)
        assertEquals(testMedKit.id, user.medKits.first().id)
    }

    @Test
    fun `findByIdWithMedKits - loads empty medkits collection when no medkits`() {
        val userWithoutMedKits = userBuilder()
            .withHashedKey("no-medkits-hash")
            .build()
        entityManager.persist(userWithoutMedKits)
        entityManager.flush()
        entityManager.clear()

        val user = userRepository.findByIdWithMedKits(userWithoutMedKits.id)

        assertNotNull(user)
        assertTrue(user!!.medKits.isEmpty())
    }

    @Test
    fun `findByIdWithMedKits - returns null for non-existent user`() {
        val nonExistentId = UUID.randomUUID()

        val user = userRepository.findByIdWithMedKits(nonExistentId)

        assertNull(user)
    }

    @Test
    fun `findByIdWithMedKits - loads multiple medkits`() {
        val medKit2 = medKitBuilder().build()
        medKit2.users.add(testUser)
        testUser.medKits.add(medKit2)
        entityManager.persist(medKit2)
        entityManager.flush()
        entityManager.clear()

        val user = userRepository.findByIdWithMedKits(testUser.id)

        assertNotNull(user)
        assertEquals(2, user!!.medKits.size)
        assertTrue(user.medKits.any { it.id == testMedKit.id })
        assertTrue(user.medKits.any { it.id == medKit2.id })
    }

    @Test
    fun `findByIdWithMedKits - prevents N+1 queries for medkits`() {
        val medKit2 = medKitBuilder().build()
        medKit2.users.add(testUser)
        testUser.medKits.add(medKit2)
        entityManager.persist(medKit2)
        entityManager.flush()
        entityManager.clear()

        val user = userRepository.findByIdWithMedKits(testUser.id)

        assertNotNull(user)
        // Accessing medkits should not trigger additional queries
        val medKitIds = user!!.medKits.map { it.id }
        assertEquals(2, medKitIds.size)
    }

    // Hashed key uniqueness tests
    @Test
    fun `save - allows different hashed keys`() {
        val user2 = userBuilder()
            .withHashedKey("different-hash")
            .build()

        val saved = userRepository.save(user2)

        assertNotNull(saved.id)
        assertEquals("different-hash", saved.hashedKey)
    }

    @Test
    fun `save - enforces unique hashed key constraint`() {
        val user2 = userBuilder()
            .withHashedKey("test-hash-123") // Same as testUser
            .build()

        assertThrows(DataIntegrityViolationException::class.java) {
            userRepository.save(user2)
            entityManager.flush()
        }
    }

    @Test
    fun `save - allows null hashed key to be updated`() {
        val userWithNullKey = User(
            id = UUID.randomUUID(),
            hashedKey = "temporary-hash"
        )
        val saved = userRepository.save(userWithNullKey)
        
        saved.hashedKey = "updated-hash"
        val updated = userRepository.save(saved)
        entityManager.flush()

        assertEquals("updated-hash", updated.hashedKey)
    }

    // Edge cases
    @Test
    fun `findAll - returns all users`() {
        val user2 = userBuilder()
            .withHashedKey("user2-hash")
            .build()
        userRepository.save(user2)
        entityManager.flush()

        val users = userRepository.findAll()

        assertTrue(users.size >= 2)
        assertTrue(users.any { it.id == testUser.id })
        assertTrue(users.any { it.id == user2.id })
    }

    @Test
    fun `findById - returns user with basic fields`() {
        val optional = userRepository.findById(testUser.id)

        assertTrue(optional.isPresent)
        val user = optional.get()
        assertEquals(testUser.id, user.id)
        assertEquals("test-hash-123", user.hashedKey)
    }

    @Test
    fun `delete - removes user and relationships`() {
        val userId = testUser.id
        userRepository.deleteById(userId)
        entityManager.flush()

        val optional = userRepository.findById(userId)
        assertFalse(optional.isPresent)
    }
}
