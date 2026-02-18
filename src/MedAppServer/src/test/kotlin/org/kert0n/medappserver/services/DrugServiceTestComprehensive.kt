package org.kert0n.medappserver.services

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.kert0n.medappserver.db.model.*
import org.kert0n.medappserver.db.repository.*
import org.kert0n.medappserver.testutil.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpStatus
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.util.*

/**
 * Comprehensive DrugService tests with H2 database
 * Following the test matrix: minimum 5 tests per method
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DrugServiceTestComprehensive {

    @Autowired
    private lateinit var drugService: DrugService

    @Autowired
    private lateinit var medKitService: MedKitService

    @Autowired
    private lateinit var userService: UserService

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var medKitRepository: MedKitRepository

    @Autowired
    private lateinit var drugRepository: DrugRepository

    @Autowired
    private lateinit var usingRepository: UsingRepository

    private lateinit var testUser: User
    private lateinit var testMedKit: MedKit
    private lateinit var testDrug: Drug

    @BeforeEach
    fun setup() {
        // Create test user
        testUser = userService.registerNewUser(UUID.randomUUID(), "test-password")
        
        // Create test medkit
        testMedKit = medKitService.createNew(testUser.id)
        
        // Create test drug
        val drugDTO = drugCreateDTOBuilder()
            .withName("Test Aspirin")
            .withQuantity(100.0)
            .withQuantityUnit("mg")
            .withMedKitId(testMedKit.id)
            .build()
        testDrug = drugService.create(drugDTO, testUser.id)
    }

    // findById tests (5+)
    @Test
    fun `findById - happy path - returns drug`() {
        val found = drugService.findById(testDrug.id)
        assertNotNull(found)
        assertEquals(testDrug.id, found.id)
        assertEquals("Test Aspirin", found.name)
    }

    @Test
    fun `findById - not found - throws exception`() {
        val nonExistentId = UUID.randomUUID()
        val exception = assertThrows<ResponseStatusException> {
            drugService.findById(nonExistentId)
        }
        assertEquals(HttpStatus.NOT_FOUND, exception.statusCode)
    }

    @Test
    fun `findById - with usings - loads associations`() {
        // Create a treatment plan
        val using = usingBuilder(testUser, testDrug)
            .withPlannedAmount(20.0)
            .build()
        testDrug.usings.add(using)
        drugRepository.save(testDrug)

        val found = drugService.findById(testDrug.id)
        assertFalse(found.usings.isEmpty())
    }

    @Test
    fun `findById - different instances - same entity`() {
        val found1 = drugService.findById(testDrug.id)
        val found2 = drugService.findById(testDrug.id)
        assertEquals(found1.id, found2.id)
    }

    @Test
    fun `findById - after modification - returns updated`() {
        testDrug.quantity = 200.0
        drugRepository.save(testDrug)
        
        val found = drugService.findById(testDrug.id)
        assertEquals(200.0, found.quantity)
    }

    // findByIdForUser tests (5+)
    @Test
    fun `findByIdForUser - authorized user - returns drug`() {
        val found = drugService.findByIdForUser(testDrug.id, testUser.id)
        assertNotNull(found)
        assertEquals(testDrug.id, found.id)
    }

    @Test
    fun `findByIdForUser - unauthorized user - throws exception`() {
        val otherUser = userService.registerNewUser(UUID.randomUUID(), "other-password")
        
        val exception = assertThrows<ResponseStatusException> {
            drugService.findByIdForUser(testDrug.id, otherUser.id)
        }
        assertEquals(HttpStatus.NOT_FOUND, exception.statusCode)
    }

    @Test
    fun `findByIdForUser - non-existent drug - throws exception`() {
        val exception = assertThrows<ResponseStatusException> {
            drugService.findByIdForUser(UUID.randomUUID(), testUser.id)
        }
        assertEquals(HttpStatus.NOT_FOUND, exception.statusCode)
    }

    @Test
    fun `findByIdForUser - non-existent user - throws exception`() {
        val exception = assertThrows<ResponseStatusException> {
            drugService.findByIdForUser(testDrug.id, UUID.randomUUID())
        }
        assertEquals(HttpStatus.NOT_FOUND, exception.statusCode)
    }

    @Test
    fun `findByIdForUser - shared medkit - allows access`() {
        val otherUser = userService.registerNewUser(UUID.randomUUID(), "other-password")
        medKitService.addUserToMedKit(testMedKit.id, otherUser.id)
        
        val found = drugService.findByIdForUser(testDrug.id, otherUser.id)
        assertNotNull(found)
    }

    // create tests (5+)
    @Test
    fun `create - valid DTO - creates drug`() {
        val dto = drugCreateDTOBuilder()
            .withName("New Drug")
            .withQuantity(50.0)
            .withMedKitId(testMedKit.id)
            .build()
        
        val created = drugService.create(dto, testUser.id)
        
        assertNotNull(created.id)
        assertEquals("New Drug", created.name)
        assertEquals(50.0, created.quantity)
    }

    @Test
    fun `create - non-existent medkit - throws exception`() {
        val dto = drugCreateDTOBuilder()
            .withMedKitId(UUID.randomUUID())
            .build()
        
        assertThrows<ResponseStatusException> {
            drugService.create(dto, testUser.id)
        }
    }

    @Test
    fun `create - unauthorized medkit - throws exception`() {
        val otherUser = userService.registerNewUser(UUID.randomUUID(), "other-password")
        val dto = drugCreateDTOBuilder()
            .withMedKitId(testMedKit.id)
            .build()
        
        val exception = assertThrows<ResponseStatusException> {
            drugService.create(dto, otherUser.id)
        }
        assertEquals(HttpStatus.FORBIDDEN, exception.statusCode)
    }

    @Test
    fun `create - with all optional fields - creates successfully`() {
        val dto = drugCreateDTOBuilder()
            .withName("Complete Drug")
            .withQuantity(75.0)
            .withQuantityUnit("ml")
            .withMedKitId(testMedKit.id)
            .withFormType("syrup")
            .withCategory("cough")
            .withManufacturer("PharmaCorp")
            .withCountry("USA")
            .withDescription("Test description")
            .build()
        
        val created = drugService.create(dto, testUser.id)
        
        assertEquals("PharmaCorp", created.manufacturer)
        assertEquals("syrup", created.formType)
    }

    @Test
    fun `create - minimum required fields - creates successfully`() {
        val dto = drugCreateDTOBuilder()
            .withName("Minimal Drug")
            .withQuantity(10.0)
            .withQuantityUnit("g")
            .withMedKitId(testMedKit.id)
            .withFormType(null)
            .withCategory(null)
            .withManufacturer(null)
            .build()
        
        val created = drugService.create(dto, testUser.id)
        
        assertNotNull(created.id)
        assertNull(created.manufacturer)
    }

    // update tests (5+)
    @Test
    fun `update - valid changes - updates drug`() {
        val updateDTO = drugUpdateDTOBuilder()
            .withName("Updated Name")
            .withQuantity(150.0)
            .build()
        
        val updated = drugService.update(testDrug.id, updateDTO, testUser.id)
        
        assertEquals("Updated Name", updated.name)
        assertEquals(150.0, updated.quantity)
    }

    @Test
    fun `update - partial update - only changes specified fields`() {
        val originalQuantity = testDrug.quantity
        val updateDTO = drugUpdateDTOBuilder()
            .withName("Only Name Changed")
            .build()
        
        val updated = drugService.update(testDrug.id, updateDTO, testUser.id)
        
        assertEquals("Only Name Changed", updated.name)
        assertEquals(originalQuantity, updated.quantity)
    }

    @Test
    fun `update - unauthorized user - throws exception`() {
        val otherUser = userService.registerNewUser(UUID.randomUUID(), "other-password")
        val updateDTO = drugUpdateDTOBuilder()
            .withName("Should Fail")
            .build()
        
        assertThrows<ResponseStatusException> {
            drugService.update(testDrug.id, updateDTO, otherUser.id)
        }
    }

    @Test
    fun `update - non-existent drug - throws exception`() {
        val updateDTO = drugUpdateDTOBuilder()
            .withName("New Name")
            .build()
        
        assertThrows<ResponseStatusException> {
            drugService.update(UUID.randomUUID(), updateDTO, testUser.id)
        }
    }

    @Test
    fun `update - empty update - returns unchanged`() {
        val originalName = testDrug.name
        val updateDTO = DrugUpdateDTO()
        
        val result = drugService.update(testDrug.id, updateDTO, testUser.id)
        
        assertEquals(originalName, result.name)
    }

    // consumeDrug tests (5+)
    @Test
    fun `consumeDrug - valid amount - reduces quantity`() {
        val initialQuantity = testDrug.quantity
        val consumeAmount = 20.0
        
        val result = drugService.consumeDrug(testDrug.id, consumeAmount, testUser.id)
        
        assertEquals(initialQuantity - consumeAmount, result.quantity)
    }

    @Test
    fun `consumeDrug - exceeds available - throws exception`() {
        val excessAmount = testDrug.quantity + 10.0
        
        val exception = assertThrows<ResponseStatusException> {
            drugService.consumeDrug(testDrug.id, excessAmount, testUser.id)
        }
        assertEquals(HttpStatus.BAD_REQUEST, exception.statusCode)
    }

    @Test
    fun `consumeDrug - negative amount - throws exception`() {
        assertThrows<ResponseStatusException> {
            drugService.consumeDrug(testDrug.id, -10.0, testUser.id)
        }
    }

    @Test
    fun `consumeDrug - exact quantity - sets to zero`() {
        val exactAmount = testDrug.quantity
        
        val result = drugService.consumeDrug(testDrug.id, exactAmount, testUser.id)
        
        assertEquals(0.0, result.quantity)
    }

    @Test
    fun `consumeDrug - unauthorized user - throws exception`() {
        val otherUser = userService.registerNewUser(UUID.randomUUID(), "other-password")
        
        assertThrows<ResponseStatusException> {
            drugService.consumeDrug(testDrug.id, 10.0, otherUser.id)
        }
    }

    // getPlannedQuantity tests (5+)
    @Test
    fun `getPlannedQuantity - no usings - returns zero`() {
        val planned = drugService.getPlannedQuantity(testDrug)
        assertEquals(0.0, planned)
    }

    @Test
    fun `getPlannedQuantity - single using - returns amount`() {
        val using = usingBuilder(testUser, testDrug)
            .withPlannedAmount(25.0)
            .build()
        testDrug.usings.add(using)
        
        val planned = drugService.getPlannedQuantity(testDrug)
        assertEquals(25.0, planned)
    }

    @Test
    fun `getPlannedQuantity - multiple usings - returns sum`() {
        // This test is complex due to composite keys and bidirectional relationships
        // For now, we test the calculation logic directly with mock data
        val using1 = Using(
            usingKey = UsingKey(userId = testUser.id, drugId = testDrug.id),
            user = testUser,
            drug = testDrug,
            plannedAmount = 30.0
        )
        val using2 = Using(
            usingKey = UsingKey(userId = UUID.randomUUID(), drugId = testDrug.id),
            user = testUser, // Reuse testUser for simplicity
            drug = testDrug,
            plannedAmount = 20.0
        )
        
        // Manually populate the usings collection to test the calculation
        testDrug.usings.add(using1)
        testDrug.usings.add(using2)
        
        val planned = drugService.getPlannedQuantity(testDrug)
        assertEquals(50.0, planned, "Should sum planned amounts from multiple usings")
    }

    @Test
    fun `getPlannedQuantity - fractional amounts - calculates correctly`() {
        val using = usingBuilder(testUser, testDrug)
            .withPlannedAmount(12.5)
            .build()
        testDrug.usings.add(using)
        
        val planned = drugService.getPlannedQuantity(testDrug)
        assertEquals(12.5, planned)
    }

    @Test
    fun `getPlannedQuantity - empty usings after clear - returns zero`() {
        val using = usingBuilder(testUser, testDrug).withPlannedAmount(30.0).build()
        testDrug.usings.add(using)
        testDrug.usings.clear()
        
        val planned = drugService.getPlannedQuantity(testDrug)
        assertEquals(0.0, planned)
    }
}
