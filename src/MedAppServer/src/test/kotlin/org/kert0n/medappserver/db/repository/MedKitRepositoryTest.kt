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
 * Repository tests for MedKitRepository custom queries
 * Tests verify eager loading with JOIN FETCH for relationships
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MedKitRepositoryTest {

    @Autowired
    private lateinit var medKitRepository: MedKitRepository

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var drugRepository: DrugRepository

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

    // findByUsersId tests
    @Test
    fun `findByUsersId - returns medkits for user`() {
        val medKits = medKitRepository.findByUsersId(testUser.id)

        assertEquals(1, medKits.size)
        assertEquals(testMedKit.id, medKits[0].id)
    }

    @Test
    fun `findByUsersId - returns multiple medkits for user`() {
        // Create new medkit and associate with existing user
        val medKit2 = medKitBuilder().build()
        // Fetch user from DB to get managed entity
        val managedUser = entityManager.find(User::class.java, testUser.id)
        managedUser.addMedKit(medKit2)  // Use helper method for proper bidirectional sync
        entityManager.persist(medKit2)
        entityManager.flush()

        val medKits = medKitRepository.findByUsersId(testUser.id)

        assertEquals(2, medKits.size)
        assertTrue(medKits.any { it.id == testMedKit.id })
        assertTrue(medKits.any { it.id == medKit2.id })
    }

    @Test
    fun `findByUsersId - returns empty list when user has no medkits`() {
        val userWithoutMedKits = userBuilder()
            .withHashedKey("no-medkits-hash")
            .build()
        entityManager.persist(userWithoutMedKits)
        entityManager.flush()

        val medKits = medKitRepository.findByUsersId(userWithoutMedKits.id)

        assertTrue(medKits.isEmpty())
    }

    @Test
    fun `findByUsersId - shared medkit appears for multiple users`() {
        val user2 = userBuilder()
            .withHashedKey("user2-hash")
            .build()
        entityManager.persist(user2)
        testMedKit.users.add(user2)
        user2.medKits.add(testMedKit)
        entityManager.merge(testMedKit)
        entityManager.flush()

        val medKits1 = medKitRepository.findByUsersId(testUser.id)
        val medKits2 = medKitRepository.findByUsersId(user2.id)

        assertEquals(1, medKits1.size)
        assertEquals(1, medKits2.size)
        assertEquals(testMedKit.id, medKits1[0].id)
        assertEquals(testMedKit.id, medKits2[0].id)
    }

    // findByIdWithDrugs tests
    @Test
    fun `findByIdWithDrugs - eagerly loads drugs with JOIN FETCH`() {
        val drug = drugBuilder(testMedKit)
            .withName("Aspirin")
            .build()
        entityManager.persist(drug)
        entityManager.flush()
        entityManager.clear()

        val medKit = medKitRepository.findByIdWithDrugs(testMedKit.id)

        assertNotNull(medKit)
        // Verify drugs are loaded (no lazy init exception)
        assertFalse(medKit!!.drugs.isEmpty())
        assertEquals(1, medKit.drugs.size)
        assertEquals("Aspirin", medKit.drugs.first().name)
    }

    @Test
    fun `findByIdWithDrugs - loads empty drugs collection when no drugs`() {
        val medKit = medKitRepository.findByIdWithDrugs(testMedKit.id)

        assertNotNull(medKit)
        assertTrue(medKit!!.drugs.isEmpty())
    }

    @Test
    fun `findByIdWithDrugs - returns null for non-existent medkit`() {
        val nonExistentId = UUID.randomUUID()

        val medKit = medKitRepository.findByIdWithDrugs(nonExistentId)

        assertNull(medKit)
    }

    @Test
    fun `findByIdWithDrugs - loads multiple drugs`() {
        val drug1 = drugBuilder(testMedKit)
            .withName("Aspirin")
            .build()
        val drug2 = drugBuilder(testMedKit)
            .withName("Ibuprofen")
            .build()
        entityManager.persist(drug1)
        entityManager.persist(drug2)
        entityManager.flush()
        entityManager.clear()

        val medKit = medKitRepository.findByIdWithDrugs(testMedKit.id)

        assertNotNull(medKit)
        assertEquals(2, medKit!!.drugs.size)
        assertTrue(medKit.drugs.any { it.name == "Aspirin" })
        assertTrue(medKit.drugs.any { it.name == "Ibuprofen" })
    }

    // findByIdWithUsers tests
    @Test
    fun `findByIdWithUsers - eagerly loads users with JOIN FETCH`() {
        entityManager.clear()

        val medKit = medKitRepository.findByIdWithUsers(testMedKit.id)

        assertNotNull(medKit)
        // Verify users are loaded (no lazy init exception)
        assertFalse(medKit!!.users.isEmpty())
        assertEquals(1, medKit.users.size)
        assertEquals(testUser.id, medKit.users.first().id)
    }

    @Test
    fun `findByIdWithUsers - loads empty users collection when no users`() {
        val medKitWithoutUsers = medKitBuilder().build()
        entityManager.persist(medKitWithoutUsers)
        entityManager.flush()
        entityManager.clear()

        val medKit = medKitRepository.findByIdWithUsers(medKitWithoutUsers.id)

        assertNotNull(medKit)
        assertTrue(medKit!!.users.isEmpty())
    }

    @Test
    fun `findByIdWithUsers - returns null for non-existent medkit`() {
        val nonExistentId = UUID.randomUUID()

        val medKit = medKitRepository.findByIdWithUsers(nonExistentId)

        assertNull(medKit)
    }

    @Test
    fun `findByIdWithUsers - loads multiple users`() {
        val user2 = userBuilder()
            .withHashedKey("user2-hash")
            .build()
        entityManager.persist(user2)
        testMedKit.users.add(user2)
        user2.medKits.add(testMedKit)
        entityManager.merge(testMedKit)
        entityManager.flush()
        entityManager.clear()

        val medKit = medKitRepository.findByIdWithUsers(testMedKit.id)

        assertNotNull(medKit)
        assertEquals(2, medKit!!.users.size)
        assertTrue(medKit.users.any { it.id == testUser.id })
        assertTrue(medKit.users.any { it.id == user2.id })
    }

    @Test
    fun `findByIdWithUsers - handles users without hashed keys loaded`() {
        entityManager.clear()

        val medKit = medKitRepository.findByIdWithUsers(testMedKit.id)

        assertNotNull(medKit)
        val user = medKit!!.users.first()
        // Verify user details are accessible
        assertNotNull(user.hashedKey)
        assertEquals("test-hash-123", user.hashedKey)
    }

    // Edge cases for relationship management
    @Test
    fun `findByUsersId - handles user removed from medkit`() {
        val user2 = userBuilder()
            .withHashedKey("user2-hash")
            .build()
        entityManager.persist(user2)
        testMedKit.users.add(user2)
        user2.medKits.add(testMedKit)
        entityManager.merge(testMedKit)
        entityManager.flush()

        // Remove user2 from medkit
        testMedKit.users.remove(user2)
        user2.medKits.remove(testMedKit)
        entityManager.merge(testMedKit)
        entityManager.flush()

        val medKitsForUser2 = medKitRepository.findByUsersId(user2.id)

        assertTrue(medKitsForUser2.isEmpty())
    }

    @Test
    fun `findByIdWithDrugs and findByIdWithUsers - both work independently`() {
        val drug = drugBuilder(testMedKit)
            .withName("Aspirin")
            .build()
        entityManager.persist(drug)
        entityManager.flush()
        entityManager.clear()

        val medKitWithDrugs = medKitRepository.findByIdWithDrugs(testMedKit.id)
        val medKitWithUsers = medKitRepository.findByIdWithUsers(testMedKit.id)

        assertNotNull(medKitWithDrugs)
        assertNotNull(medKitWithUsers)
        assertFalse(medKitWithDrugs!!.drugs.isEmpty())
        assertFalse(medKitWithUsers!!.users.isEmpty())
    }
}
