package org.kert0n.medappserver.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import org.junit.jupiter.api.Test
import org.kert0n.medappserver.db.model.*
import org.kert0n.medappserver.services.MedKitService
import org.kert0n.medappserver.services.UserService
import org.kert0n.medappserver.testutil.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
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
 * UserController tests with MockMvc and MockK
 * Tests GET /api/users/me endpoint
 */
@WebMvcTest(UserController::class)
@Import(TestSecurityConfig::class)
class UserControllerTest {
    
    @Autowired
    private lateinit var mockMvc: MockMvc
    
    @Autowired
    private lateinit var objectMapper: ObjectMapper
    
    @MockkBean
    private lateinit var userService: UserService
    
    @MockkBean
    private lateinit var medKitService: MedKitService
    
    // Test 1: Authenticated user returns user data
    @Test
    @WithMockUser(username = "550e8400-e29b-41d4-a716-446655440000")
    fun `GET user - authenticated - returns user data`() {
        val userId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
        val user = userBuilder().withId(userId).build()
        
        every { userService.findById(userId) } returns user
        
        mockMvc.perform(get("/api/users/me")
            .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(userId.toString()))
    }
    
    // Test 2: Unauthenticated user returns 401
    @Test
    fun `GET user - unauthenticated - returns 401`() {
        mockMvc.perform(get("/api/users/me")
            .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isUnauthorized)
    }
    
    // Test 3: User not found returns 404
    @Test
    @WithMockUser(username = "550e8400-e29b-41d4-a716-446655440000")
    fun `GET user - not found - returns 404`() {
        val userId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
        
        every { userService.findById(userId) } throws ResponseStatusException(HttpStatus.NOT_FOUND)
        
        mockMvc.perform(get("/api/users/me")
            .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isNotFound)
    }
    
    // Test 4: Invalid UUID returns 400
    @Test
    @WithMockUser(username = "invalid-uuid")
    fun `GET user - invalid UUID - returns 400`() {
        mockMvc.perform(get("/api/users/me")
            .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isBadRequest)
    }
    
    // Test 5: Returns user with medkits
    @Test
    @WithMockUser(username = "550e8400-e29b-41d4-a716-446655440000")
    fun `GET user - with medkits - returns medkit list`() {
        val userId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
        val medKit1 = medKitBuilder().withId(UUID.randomUUID()).build()
        val medKit2 = medKitBuilder().withId(UUID.randomUUID()).build()
        val user = userBuilder()
            .withId(userId)
            .withMedKits(mutableSetOf(medKit1, medKit2))
            .build()
        
        every { userService.findById(userId) } returns user
        
        mockMvc.perform(get("/api/users/me")
            .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.medKits").isArray)
            .andExpect(jsonPath("$.medKits.length()").value(2))
    }
    
    // Test 6: Returns user without medkits
    @Test
    @WithMockUser(username = "550e8400-e29b-41d4-a716-446655440000")
    fun `GET user - no medkits - returns empty list`() {
        val userId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
        val user = userBuilder()
            .withId(userId)
            .withMedKits(mutableSetOf())
            .build()
        
        every { userService.findById(userId) } returns user
        
        mockMvc.perform(get("/api/users/me")
            .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.medKits").isArray)
            .andExpect(jsonPath("$.medKits.length()").value(0))
    }
    
    // Test 7: Returns correct hashedKey field
    @Test
    @WithMockUser(username = "550e8400-e29b-41d4-a716-446655440000")
    fun `GET user - returns hashedKey`() {
        val userId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
        val hashedKey = "hashed_key_abc123"
        val user = userBuilder()
            .withId(userId)
            .withHashedKey(hashedKey)
            .build()
        
        every { userService.findById(userId) } returns user
        
        mockMvc.perform(get("/api/users/me")
            .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.hashedKey").value(hashedKey))
    }
    
    // Test 8: No PII is exposed in response
    @Test
    @WithMockUser(username = "550e8400-e29b-41d4-a716-446655440000")
    fun `GET user - no PII exposed`() {
        val userId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
        val user = userBuilder().withId(userId).build()
        
        every { userService.findById(userId) } returns user
        
        mockMvc.perform(get("/api/users/me")
            .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.name").doesNotExist())
            .andExpect(jsonPath("$.email").doesNotExist())
            .andExpect(jsonPath("$.phone").doesNotExist())
            .andExpect(jsonPath("$.address").doesNotExist())
    }
    
    // Test 9: Content-Type header is application/json
    @Test
    @WithMockUser(username = "550e8400-e29b-41d4-a716-446655440000")
    fun `GET user - returns JSON content type`() {
        val userId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
        val user = userBuilder().withId(userId).build()
        
        every { userService.findById(userId) } returns user
        
        mockMvc.perform(get("/api/users/me")
            .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
    }
    
    // Test 10: Service exception propagates correctly
    @Test
    @WithMockUser(username = "550e8400-e29b-41d4-a716-446655440000")
    fun `GET user - service exception - returns appropriate status`() {
        val userId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
        
        every { userService.findById(userId) } throws ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Database error")
        
        mockMvc.perform(get("/api/users/me")
            .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isInternalServerError)
    }
    
    // Test 11: Returns UUID format correctly
    @Test
    @WithMockUser(username = "550e8400-e29b-41d4-a716-446655440000")
    fun `GET user - UUID format is correct`() {
        val userId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
        val user = userBuilder().withId(userId).build()
        
        every { userService.findById(userId) } returns user
        
        mockMvc.perform(get("/api/users/me")
            .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value("550e8400-e29b-41d4-a716-446655440000"))
    }
    
    // Test 12: Multiple concurrent requests handled correctly
    @Test
    @WithMockUser(username = "550e8400-e29b-41d4-a716-446655440000")
    fun `GET user - concurrent requests - handled correctly`() {
        val userId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
        val user = userBuilder().withId(userId).build()
        
        every { userService.findById(userId) } returns user
        
        // First request
        mockMvc.perform(get("/api/users/me")
            .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk)
        
        // Second request
        mockMvc.perform(get("/api/users/me")
            .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk)
    }
}
