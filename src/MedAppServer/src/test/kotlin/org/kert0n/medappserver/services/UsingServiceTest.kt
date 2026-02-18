package org.kert0n.medappserver.services

import jakarta.persistence.EntityManager
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
 * Comprehensive UsingService tests with H2 database
 * Following the test matrix: minimum 5 tests per method
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class UsingServiceTest {

    @Autowired
    private lateinit var usingService: UsingService

    @Autowired
    private lateinit var userService: UserService

    @Autowired
    private lateinit var medKitService: MedKitService

    @Autowired
    private lateinit var drugService: DrugService

    @Autowired
    private lateinit var usingRepository: UsingRepository

    @Autowired
    private lateinit var drugRepository: DrugRepository
    
    @Autowired
    private lateinit var entityManager: EntityManager

    private lateinit var testUser: User
    private lateinit var testUser2: User
    private lateinit var testMedKit: MedKit
    private lateinit var testDrug: Drug

    @BeforeEach
    fun setup() {
        testUser = userService.registerNewUser(UUID.randomUUID(), "test-password")
        testUser2 = userService.registerNewUser(UUID.randomUUID(), "test-password2")
        testMedKit = medKitService.createNew(testUser.id)
        testDrug = drugService.create(
            drugCreateDTOBuilder()
                .withMedKitId(testMedKit.id)
                .withQuantity(100.0)
                .build(),
            testUser.id
        )
    }

    // ========== createTreatmentPlan Tests ==========

    @Test
    fun `createTreatmentPlan - valid data - creates treatment plan`() {
        val createDTO = UsingCreateDTO(
            drugId = testDrug.id,
            plannedAmount = 30.0
        )

        val using = usingService.createTreatmentPlan(testUser.id, createDTO)

        assertEquals(testUser.id, using.user.id)
        assertEquals(testDrug.id, using.drug.id)
        assertEquals(30.0, using.plannedAmount)
    }

    @Test
    fun `createTreatmentPlan - exceeds drug quantity - throws exception`() {
        val createDTO = UsingCreateDTO(
            drugId = testDrug.id,
            plannedAmount = 150.0  // More than available 100.0
        )

        val exception = assertThrows<ResponseStatusException> {
            usingService.createTreatmentPlan(testUser.id, createDTO)
        }

        assertEquals(HttpStatus.BAD_REQUEST, exception.statusCode)
    }

    @Test
    fun `createTreatmentPlan - drug not accessible - throws exception`() {
        val createDTO = UsingCreateDTO(
            drugId = testDrug.id,
            plannedAmount = 30.0
        )

        val exception = assertThrows<ResponseStatusException> {
            usingService.createTreatmentPlan(testUser2.id, createDTO)
        }

        assertEquals(HttpStatus.FORBIDDEN, exception.statusCode)
    }

    @Test
    fun `createTreatmentPlan - negative amount - throws exception`() {
        val createDTO = UsingCreateDTO(
            drugId = testDrug.id,
            plannedAmount = -10.0
        )

        val exception = assertThrows<ResponseStatusException> {
            usingService.createTreatmentPlan(testUser.id, createDTO)
        }

        assertEquals(HttpStatus.BAD_REQUEST, exception.statusCode)
    }

    @Test
    fun `createTreatmentPlan - already exists - throws exception`() {
        val createDTO = UsingCreateDTO(
            drugId = testDrug.id,
            plannedAmount = 30.0
        )
        
        usingService.createTreatmentPlan(testUser.id, createDTO)

        val exception = assertThrows<ResponseStatusException> {
            usingService.createTreatmentPlan(testUser.id, createDTO)
        }

        assertEquals(HttpStatus.CONFLICT, exception.statusCode)
    }

    // ========== updateTreatmentPlan Tests ==========

    @Test
    fun `updateTreatmentPlan - valid update - updates successfully`() {
        val createDTO = UsingCreateDTO(testDrug.id, 30.0)
        usingService.createTreatmentPlan(testUser.id, createDTO)

        val updateDTO = UsingUpdateDTO(plannedAmount = 40.0)
        val updated = usingService.updateTreatmentPlan(testUser.id, testDrug.id, updateDTO)

        assertEquals(40.0, updated.plannedAmount)
    }

    @Test
    fun `updateTreatmentPlan - exceeds quantity - throws exception`() {
        val createDTO = UsingCreateDTO(testDrug.id, 30.0)
        usingService.createTreatmentPlan(testUser.id, createDTO)

        val updateDTO = UsingUpdateDTO(plannedAmount = 150.0)

        val exception = assertThrows<ResponseStatusException> {
            usingService.updateTreatmentPlan(testUser.id, testDrug.id, updateDTO)
        }

        assertEquals(HttpStatus.BAD_REQUEST, exception.statusCode)
    }

    @Test
    fun `updateTreatmentPlan - non-existent plan - throws exception`() {
        val updateDTO = UsingUpdateDTO(plannedAmount = 40.0)

        val exception = assertThrows<ResponseStatusException> {
            usingService.updateTreatmentPlan(testUser.id, testDrug.id, updateDTO)
        }

        assertEquals(HttpStatus.NOT_FOUND, exception.statusCode)
    }

    @Test
    fun `updateTreatmentPlan - unauthorized user - throws exception`() {
        val createDTO = UsingCreateDTO(testDrug.id, 30.0)
        usingService.createTreatmentPlan(testUser.id, createDTO)

        val updateDTO = UsingUpdateDTO(plannedAmount = 40.0)

        val exception = assertThrows<ResponseStatusException> {
            usingService.updateTreatmentPlan(testUser2.id, testDrug.id, updateDTO)
        }

        assertEquals(HttpStatus.NOT_FOUND, exception.statusCode)
    }

    @Test
    fun `updateTreatmentPlan - negative amount - throws exception`() {
        val createDTO = UsingCreateDTO(testDrug.id, 30.0)
        usingService.createTreatmentPlan(testUser.id, createDTO)

        val updateDTO = UsingUpdateDTO(plannedAmount = -10.0)

        val exception = assertThrows<ResponseStatusException> {
            usingService.updateTreatmentPlan(testUser.id, testDrug.id, updateDTO)
        }

        assertEquals(HttpStatus.BAD_REQUEST, exception.statusCode)
    }

    // ========== recordIntake Tests ==========

    @Test
    fun `recordIntake - valid intake - reduces planned amount and drug quantity`() {
        val createDTO = UsingCreateDTO(testDrug.id, 30.0)
        usingService.createTreatmentPlan(testUser.id, createDTO)

        val updated = usingService.recordIntake(testUser.id, testDrug.id, 10.0)

        assertEquals(20.0, updated.plannedAmount)
        
        val drug = drugRepository.findById(testDrug.id).orElseThrow()
        assertEquals(90.0, drug.quantity)
    }

    @Test
    fun `recordIntake - exact planned amount - sets to zero`() {
        val createDTO = UsingCreateDTO(testDrug.id, 30.0)
        usingService.createTreatmentPlan(testUser.id, createDTO)

        val updated = usingService.recordIntake(testUser.id, testDrug.id, 30.0)

        assertEquals(0.0, updated.plannedAmount)
    }

    @Test
    fun `recordIntake - exceeds planned amount - throws exception`() {
        val createDTO = UsingCreateDTO(testDrug.id, 30.0)
        usingService.createTreatmentPlan(testUser.id, createDTO)

        val exception = assertThrows<ResponseStatusException> {
            usingService.recordIntake(testUser.id, testDrug.id, 40.0)
        }

        assertEquals(HttpStatus.BAD_REQUEST, exception.statusCode)
    }

    @Test
    fun `recordIntake - negative quantity - throws exception`() {
        val createDTO = UsingCreateDTO(testDrug.id, 30.0)
        usingService.createTreatmentPlan(testUser.id, createDTO)

        val exception = assertThrows<ResponseStatusException> {
            usingService.recordIntake(testUser.id, testDrug.id, -5.0)
        }

        assertEquals(HttpStatus.BAD_REQUEST, exception.statusCode)
    }

    @Test
    fun `recordIntake - no treatment plan - throws exception`() {
        val exception = assertThrows<ResponseStatusException> {
            usingService.recordIntake(testUser.id, testDrug.id, 10.0)
        }

        assertEquals(HttpStatus.NOT_FOUND, exception.statusCode)
    }

    // ========== deleteTreatmentPlan Tests ==========

    @Test
    fun `deleteTreatmentPlan - existing plan - deletes successfully`() {
        val createDTO = UsingCreateDTO(testDrug.id, 30.0)
        usingService.createTreatmentPlan(testUser.id, createDTO)

        usingService.deleteTreatmentPlan(testUser.id, testDrug.id)

        val using = usingRepository.findById(UsingKey(testUser.id, testDrug.id))
        assertFalse(using.isPresent)
    }

    @Test
    fun `deleteTreatmentPlan - non-existent plan - throws exception`() {
        val exception = assertThrows<ResponseStatusException> {
            usingService.deleteTreatmentPlan(testUser.id, testDrug.id)
        }

        assertEquals(HttpStatus.NOT_FOUND, exception.statusCode)
    }

    @Test
    fun `deleteTreatmentPlan - unauthorized user - throws exception`() {
        val createDTO = UsingCreateDTO(testDrug.id, 30.0)
        usingService.createTreatmentPlan(testUser.id, createDTO)

        val exception = assertThrows<ResponseStatusException> {
            usingService.deleteTreatmentPlan(testUser2.id, testDrug.id)
        }

        assertEquals(HttpStatus.NOT_FOUND, exception.statusCode)
    }

    @Test
    fun `deleteTreatmentPlan - returns drug quantity - does not change quantity`() {
        val createDTO = UsingCreateDTO(testDrug.id, 30.0)
        usingService.createTreatmentPlan(testUser.id, createDTO)
        
        val quantityBefore = drugRepository.findById(testDrug.id).orElseThrow().quantity

        usingService.deleteTreatmentPlan(testUser.id, testDrug.id)

        val quantityAfter = drugRepository.findById(testDrug.id).orElseThrow().quantity
        assertEquals(quantityBefore, quantityAfter)
    }

    // ========== Query Methods Tests ==========

    @Test
    fun `findAllByUser - returns all user's treatment plans`() {
        val drug2 = drugService.create(
            drugCreateDTOBuilder()
                .withMedKitId(testMedKit.id)
                .withName("Drug2")
                .build(),
            testUser.id
        )

        usingService.createTreatmentPlan(testUser.id, UsingCreateDTO(testDrug.id, 30.0))
        usingService.createTreatmentPlan(testUser.id, UsingCreateDTO(drug2.id, 20.0))

        val usings = usingService.findAllByUser(testUser.id)

        assertEquals(2, usings.size)
    }

    @Test
    fun `findAllByDrug - returns all plans for drug`() {
        medKitService.addUserToMedKit(testMedKit.id, testUser2.id)
        
        usingService.createTreatmentPlan(testUser.id, UsingCreateDTO(testDrug.id, 30.0))
        usingService.createTreatmentPlan(testUser2.id, UsingCreateDTO(testDrug.id, 20.0))

        val usings = usingService.findAllByDrug(testDrug.id)

        assertEquals(2, usings.size)
    }

    @Test
    fun `findByUserAndDrug - returns specific plan`() {
        usingService.createTreatmentPlan(testUser.id, UsingCreateDTO(testDrug.id, 30.0))

        val using = usingService.findByUserAndDrug(testUser.id, testDrug.id)

        assertNotNull(using)
        assertEquals(30.0, using!!.plannedAmount)
    }

    @Test
    fun `findByUserAndDrug - non-existent - returns null`() {
        val using = usingService.findByUserAndDrug(testUser.id, testDrug.id)

        assertNull(using)
    }
}
