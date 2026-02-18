package org.kert0n.medappserver.controller

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.kert0n.medappserver.db.model.*
import org.kert0n.medappserver.services.MedKitService
import org.kert0n.medappserver.services.UserService
import org.kert0n.medappserver.testutil.*
import org.mockito.kotlin.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

/**
 * Comprehensive tests for UserController
 * Tests GET /user endpoint with security, data validation, and error handling
 */
@WebMvcTest(UserController::class)
@Import(TestSecurityConfig::class)
class UserControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @MockBean
    private lateinit var userService: UserService

    @MockBean
    private lateinit var medKitService: MedKitService

    // Test 1: Authenticated user - returns complete user data
    @Test
    @WithMockUser(username = "550e8400-e29b-41d4-a716-446655440000")
    fun `GET user - authenticated user - returns complete user data`() {
        // Arrange
        val userId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
        val user = userBuilder()
            .withId(userId)
            .withHashedKey("hashed_password_123")
            .build()

        val medKit1 = medKitBuilder().withId(UUID.randomUUID()).build()
        val medKit2 = medKitBuilder().withId(UUID.randomUUID()).build()
        
        val drug1 = drugBuilder(medKit1)
            .withId(UUID.randomUUID())
            .withName("Aspirin")
            .withQuantity(100.0)
            .withQuantityUnit("mg")
            .build()
        
        val drug2 = drugBuilder(medKit2)
            .withId(UUID.randomUUID())
            .withName("Ibuprofen")
            .withQuantity(200.0)
            .withQuantityUnit("mg")
            .build()

        val medKitDTO1 = MedKitDTO(
            id = medKit1.id,
            drugs = setOf(
                DrugDTO(
                    id = drug1.id,
                    name = "Aspirin",
                    quantity = 100.0,
                    plannedQuantity = 0.0,
                    quantityUnit = "mg",
                    formType = "tablet",
                    category = "painkiller",
                    manufacturer = "Test Pharma",
                    country = "TestLand",
                    description = "Test description",
                    medKitId = medKit1.id
                )
            )
        )
        
        val medKitDTO2 = MedKitDTO(
            id = medKit2.id,
            drugs = setOf(
                DrugDTO(
                    id = drug2.id,
                    name = "Ibuprofen",
                    quantity = 200.0,
                    plannedQuantity = 0.0,
                    quantityUnit = "mg",
                    formType = "tablet",
                    category = "painkiller",
                    manufacturer = "Test Pharma",
                    country = "TestLand",
                    description = "Test description",
                    medKitId = medKit2.id
                )
            )
        )

        whenever(userService.findById(userId)).thenReturn(user)
        whenever(medKitService.findAllByUser(userId)).thenReturn(listOf(medKit1, medKit2))
        whenever(medKitService.toMedKitDTO(medKit1)).thenReturn(medKitDTO1)
        whenever(medKitService.toMedKitDTO(medKit2)).thenReturn(medKitDTO2)

        // Act & Assert
        mockMvc.perform(get("/user")
            .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.id").value(userId.toString()))
            .andExpect(jsonPath("$.medKits").isArray)
            .andExpect(jsonPath("$.medKits.length()").value(2))

        verify(userService).findById(userId)
        verify(medKitService).findAllByUser(userId)
        verify(medKitService, times(2)).toMedKitDTO(any())
    }

    // Test 2: No authentication - returns 401
    @Test
    fun `GET user - unauthenticated - returns 401 Unauthorized`() {
        // Act & Assert
        mockMvc.perform(get("/user")
            .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isUnauthorized)

        verifyNoInteractions(userService)
        verifyNoInteractions(medKitService)
    }

    // Test 3: Invalid token (invalid UUID format) - should still work if authentication passes
    @Test
    @WithMockUser(username = "invalid-uuid-format")
    fun `GET user - invalid UUID in authentication - returns 500 or 400`() {
        // Act & Assert - Spring will handle the UUID parsing failure
        mockMvc.perform(get("/user")
            .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().is4xxClientError)
    }

    // Test 4: User data complete with multiple medkits
    @Test
    @WithMockUser(username = "550e8400-e29b-41d4-a716-446655440001")
    fun `GET user - returns complete data structure with multiple medkits`() {
        // Arrange
        val userId = UUID.fromString("550e8400-e29b-41d4-a716-446655440001")
        val user = userBuilder().withId(userId).build()
        
        val medKit = medKitBuilder().withId(UUID.randomUUID()).build()
        val medKitDTO = MedKitDTO(id = medKit.id, drugs = emptySet())

        whenever(userService.findById(userId)).thenReturn(user)
        whenever(medKitService.findAllByUser(userId)).thenReturn(listOf(medKit))
        whenever(medKitService.toMedKitDTO(medKit)).thenReturn(medKitDTO)

        // Act & Assert
        mockMvc.perform(get("/user"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").exists())
            .andExpect(jsonPath("$.medKits").exists())
            .andExpect(jsonPath("$.medKits").isArray)
    }

    // Test 5: No PII (Personally Identifiable Information) exposed
    @Test
    @WithMockUser(username = "550e8400-e29b-41d4-a716-446655440002")
    fun `GET user - response does not contain PII or sensitive data`() {
        // Arrange
        val userId = UUID.fromString("550e8400-e29b-41d4-a716-446655440002")
        val user = userBuilder()
            .withId(userId)
            .withHashedKey("hashed_password_should_not_be_exposed")
            .build()

        whenever(userService.findById(userId)).thenReturn(user)
        whenever(medKitService.findAllByUser(userId)).thenReturn(emptyList())

        // Act & Assert
        val result = mockMvc.perform(get("/user"))
            .andExpect(status().isOk)
            .andReturn()

        val responseBody = result.response.contentAsString
        
        // Verify sensitive fields are NOT in the response
        assert(!responseBody.contains("hashedKey"))
        assert(!responseBody.contains("password"))
        assert(!responseBody.contains("hashed_password_should_not_be_exposed"))
    }

    // Test 6: Security key/password not revealed in response
    @Test
    @WithMockUser(username = "550e8400-e29b-41d4-a716-446655440003")
    fun `GET user - hashedKey is not exposed in JSON response`() {
        // Arrange
        val userId = UUID.fromString("550e8400-e29b-41d4-a716-446655440003")
        val user = userBuilder()
            .withId(userId)
            .withHashedKey("super_secret_hashed_key_12345")
            .build()

        whenever(userService.findById(userId)).thenReturn(user)
        whenever(medKitService.findAllByUser(userId)).thenReturn(emptyList())

        // Act & Assert
        mockMvc.perform(get("/user"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.hashedKey").doesNotExist())
            .andExpect(jsonPath("$.password").doesNotExist())
    }

    // Test 7: Medkits count included and accurate
    @Test
    @WithMockUser(username = "550e8400-e29b-41d4-a716-446655440004")
    fun `GET user - medkits count is accurate`() {
        // Arrange
        val userId = UUID.fromString("550e8400-e29b-41d4-a716-446655440004")
        val user = userBuilder().withId(userId).build()

        val medKit1 = medKitBuilder().withId(UUID.randomUUID()).build()
        val medKit2 = medKitBuilder().withId(UUID.randomUUID()).build()
        val medKit3 = medKitBuilder().withId(UUID.randomUUID()).build()

        val medKitDTO1 = MedKitDTO(id = medKit1.id, drugs = emptySet())
        val medKitDTO2 = MedKitDTO(id = medKit2.id, drugs = emptySet())
        val medKitDTO3 = MedKitDTO(id = medKit3.id, drugs = emptySet())

        whenever(userService.findById(userId)).thenReturn(user)
        whenever(medKitService.findAllByUser(userId)).thenReturn(listOf(medKit1, medKit2, medKit3))
        whenever(medKitService.toMedKitDTO(medKit1)).thenReturn(medKitDTO1)
        whenever(medKitService.toMedKitDTO(medKit2)).thenReturn(medKitDTO2)
        whenever(medKitService.toMedKitDTO(medKit3)).thenReturn(medKitDTO3)

        // Act & Assert
        mockMvc.perform(get("/user"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.medKits.length()").value(3))
    }

    // Test 8: Drugs count included in medkits
    @Test
    @WithMockUser(username = "550e8400-e29b-41d4-a716-446655440005")
    fun `GET user - drugs count is included in each medkit`() {
        // Arrange
        val userId = UUID.fromString("550e8400-e29b-41d4-a716-446655440005")
        val user = userBuilder().withId(userId).build()

        val medKit = medKitBuilder().withId(UUID.randomUUID()).build()
        val drug1 = drugBuilder(medKit).withId(UUID.randomUUID()).withName("Drug1").build()
        val drug2 = drugBuilder(medKit).withId(UUID.randomUUID()).withName("Drug2").build()

        val drugDTO1 = DrugDTO(
            id = drug1.id,
            name = "Drug1",
            quantity = 100.0,
            plannedQuantity = 0.0,
            quantityUnit = "mg",
            formType = null,
            category = null,
            manufacturer = null,
            country = null,
            description = null,
            medKitId = medKit.id
        )
        
        val drugDTO2 = DrugDTO(
            id = drug2.id,
            name = "Drug2",
            quantity = 200.0,
            plannedQuantity = 0.0,
            quantityUnit = "mg",
            formType = null,
            category = null,
            manufacturer = null,
            country = null,
            description = null,
            medKitId = medKit.id
        )

        val medKitDTO = MedKitDTO(
            id = medKit.id,
            drugs = setOf(drugDTO1, drugDTO2)
        )

        whenever(userService.findById(userId)).thenReturn(user)
        whenever(medKitService.findAllByUser(userId)).thenReturn(listOf(medKit))
        whenever(medKitService.toMedKitDTO(medKit)).thenReturn(medKitDTO)

        // Act & Assert
        mockMvc.perform(get("/user"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.medKits[0].drugs.length()").value(2))
    }

    // Test 9: Treatment plans count included (via plannedQuantity in drugs)
    @Test
    @WithMockUser(username = "550e8400-e29b-41d4-a716-446655440006")
    fun `GET user - treatment plans reflected in drug plannedQuantity`() {
        // Arrange
        val userId = UUID.fromString("550e8400-e29b-41d4-a716-446655440006")
        val user = userBuilder().withId(userId).build()

        val medKit = medKitBuilder().withId(UUID.randomUUID()).build()
        val drug = drugBuilder(medKit)
            .withId(UUID.randomUUID())
            .withName("Aspirin")
            .withQuantity(100.0)
            .build()

        val drugDTO = DrugDTO(
            id = drug.id,
            name = "Aspirin",
            quantity = 100.0,
            plannedQuantity = 30.0, // Treatment plan quantity
            quantityUnit = "mg",
            formType = "tablet",
            category = "painkiller",
            manufacturer = null,
            country = null,
            description = null,
            medKitId = medKit.id
        )

        val medKitDTO = MedKitDTO(id = medKit.id, drugs = setOf(drugDTO))

        whenever(userService.findById(userId)).thenReturn(user)
        whenever(medKitService.findAllByUser(userId)).thenReturn(listOf(medKit))
        whenever(medKitService.toMedKitDTO(medKit)).thenReturn(medKitDTO)

        // Act & Assert
        mockMvc.perform(get("/user"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.medKits[0].drugs[0].plannedQuantity").value(30.0))
    }

    // Test 10: Error handling - user not found
    @Test
    @WithMockUser(username = "550e8400-e29b-41d4-a716-446655440007")
    fun `GET user - user not found - returns 404`() {
        // Arrange
        val userId = UUID.fromString("550e8400-e29b-41d4-a716-446655440007")
        
        whenever(userService.findById(userId))
            .thenThrow(ResponseStatusException(HttpStatus.NOT_FOUND, "User with ID $userId not found"))

        // Act & Assert
        mockMvc.perform(get("/user"))
            .andExpect(status().isNotFound)
    }

    // Test 11: Empty medkits - user with no medkits
    @Test
    @WithMockUser(username = "550e8400-e29b-41d4-a716-446655440008")
    fun `GET user - user with no medkits - returns empty medkits array`() {
        // Arrange
        val userId = UUID.fromString("550e8400-e29b-41d4-a716-446655440008")
        val user = userBuilder().withId(userId).build()

        whenever(userService.findById(userId)).thenReturn(user)
        whenever(medKitService.findAllByUser(userId)).thenReturn(emptyList())

        // Act & Assert
        mockMvc.perform(get("/user"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(userId.toString()))
            .andExpect(jsonPath("$.medKits").isArray)
            .andExpect(jsonPath("$.medKits.length()").value(0))
    }

    // Test 12: Service layer exception handling
    @Test
    @WithMockUser(username = "550e8400-e29b-41d4-a716-446655440009")
    fun `GET user - medKitService throws exception - propagates error`() {
        // Arrange
        val userId = UUID.fromString("550e8400-e29b-41d4-a716-446655440009")
        val user = userBuilder().withId(userId).build()

        whenever(userService.findById(userId)).thenReturn(user)
        whenever(medKitService.findAllByUser(userId))
            .thenThrow(ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Database error"))

        // Act & Assert
        mockMvc.perform(get("/user"))
            .andExpect(status().isInternalServerError)
    }

    // Test 13: Correct endpoint path
    @Test
    @WithMockUser(username = "550e8400-e29b-41d4-a716-446655440010")
    fun `GET user - correct endpoint path is slash user`() {
        // Arrange
        val userId = UUID.fromString("550e8400-e29b-41d4-a716-446655440010")
        val user = userBuilder().withId(userId).build()

        whenever(userService.findById(userId)).thenReturn(user)
        whenever(medKitService.findAllByUser(userId)).thenReturn(emptyList())

        // Act & Assert - Testing /user endpoint specifically
        mockMvc.perform(get("/user"))
            .andExpect(status().isOk)
    }

    // Test 14: Response content type is JSON
    @Test
    @WithMockUser(username = "550e8400-e29b-41d4-a716-446655440011")
    fun `GET user - returns JSON content type`() {
        // Arrange
        val userId = UUID.fromString("550e8400-e29b-41d4-a716-446655440011")
        val user = userBuilder().withId(userId).build()

        whenever(userService.findById(userId)).thenReturn(user)
        whenever(medKitService.findAllByUser(userId)).thenReturn(emptyList())

        // Act & Assert
        mockMvc.perform(get("/user"))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
    }
}
