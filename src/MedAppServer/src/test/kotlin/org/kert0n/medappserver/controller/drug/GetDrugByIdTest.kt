package org.kert0n.medappserver.controller.drug

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.kert0n.medappserver.db.model.*
import org.kert0n.medappserver.db.repository.*
import org.kert0n.medappserver.testutil.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.transaction.annotation.Transactional
import java.util.*

/**
 * Tests for GET /drug/{id} endpoint
 * Minimum 6 tests covering: happy path, not found, forbidden, invalid input, edge cases
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class GetDrugByIdTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var medKitRepository: MedKitRepository

    @Autowired
    private lateinit var drugRepository: DrugRepository

    private lateinit var testUser: User
    private lateinit var testMedKit: MedKit
    private lateinit var testDrug: Drug
    private lateinit var otherUser: User

    @BeforeEach
    fun setup() {
        // Create test user
        testUser = userBuilder().build()
        userRepository.save(testUser)

        // Create other user (for access control tests)
        otherUser = userBuilder().build()
        userRepository.save(otherUser)

        // Create medkit for test user
        testMedKit = medKitBuilder().build()
        testUser.addMedKit(testMedKit)
        medKitRepository.save(testMedKit)
        userRepository.save(testUser)

        // Create test drug
        testDrug = drugBuilder(testMedKit)
            .withName("Aspirin")
            .withQuantity(50.0)
            .build()
        drugRepository.save(testDrug)
    }

    @Test
    fun `GET drug by ID - success - returns drug when user has access`() {
        // Act & Assert
        mockMvc.perform(
            get("/drug/{id}", testDrug.id)
                .with(user(testUser.hashedKey))
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.id").value(testDrug.id.toString()))
            .andExpect(jsonPath("$.name").value("Aspirin"))
            .andExpect(jsonPath("$.quantity").value(50.0))
            .andExpect(jsonPath("$.medKitId").value(testMedKit.id.toString()))
    }

    @Test
    fun `GET drug by ID - not found - returns 404 when drug doesn't exist`() {
        // Arrange
        val nonExistentId = UUID.randomUUID()

        // Act & Assert
        mockMvc.perform(
            get("/drug/{id}", nonExistentId)
                .with(user(testUser.hashedKey))
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isNotFound)
    }

    @Test
    fun `GET drug by ID - forbidden - returns 403 when user doesn't have access`() {
        // Act & Assert - otherUser trying to access testUser's drug
        mockMvc.perform(
            get("/drug/{id}", testDrug.id)
                .with(user(otherUser.hashedKey))
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isForbidden)
    }

    @Test
    fun `GET drug by ID - invalid ID format - returns 400`() {
        // Arrange
        val invalidId = "not-a-uuid"

        // Act & Assert
        mockMvc.perform(
            get("/drug/{id}", invalidId)
                .with(user(testUser.hashedKey))
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `GET drug by ID - drug with all fields populated - returns complete data`() {
        // Arrange
        val completeDrug = drugBuilder(testMedKit)
            .withName("Paracetamol 500mg")
            .withQuantity(250.5)
            .withQuantityUnit("tablets")
            .withFormType("tablet")
            .withCategory("painkiller")
            .withManufacturer("PharmaCorp")
            .withCountry("Germany")
            .withDescription("For headaches and fever")
            .build()
        drugRepository.save(completeDrug)

        // Act & Assert
        mockMvc.perform(
            get("/drug/{id}", completeDrug.id)
                .with(user(testUser.hashedKey))
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(completeDrug.id.toString()))
            .andExpect(jsonPath("$.name").value("Paracetamol 500mg"))
            .andExpect(jsonPath("$.quantity").value(250.5))
            .andExpect(jsonPath("$.quantityUnit").value("tablets"))
            .andExpect(jsonPath("$.formType").value("tablet"))
            .andExpect(jsonPath("$.category").value("painkiller"))
            .andExpect(jsonPath("$.manufacturer").value("PharmaCorp"))
            .andExpect(jsonPath("$.country").value("Germany"))
            .andExpect(jsonPath("$.description").value("For headaches and fever"))
    }

    @Test
    fun `GET drug by ID - drug with minimal fields - returns data with nulls`() {
        // Arrange
        val minimalDrug = drugBuilder(testMedKit)
            .withName("Generic Med")
            .withQuantity(10.0)
            .withFormType(null)
            .withCategory(null)
            .withManufacturer(null)
            .withCountry(null)
            .withDescription(null)
            .build()
        drugRepository.save(minimalDrug)

        // Act & Assert
        mockMvc.perform(
            get("/drug/{id}", minimalDrug.id)
                .with(user(testUser.hashedKey))
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(minimalDrug.id.toString()))
            .andExpect(jsonPath("$.name").value("Generic Med"))
            .andExpect(jsonPath("$.quantity").value(10.0))
            .andExpect(jsonPath("$.formType").doesNotExist())
            .andExpect(jsonPath("$.category").doesNotExist())
            .andExpect(jsonPath("$.manufacturer").doesNotExist())
    }
}
