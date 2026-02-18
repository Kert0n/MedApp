package org.kert0n.medappserver.controller

import com.fasterxml.jackson.databind.ObjectMapper
import io.jsonwebtoken.Jwts
import org.junit.jupiter.api.Test
import org.kert0n.medappserver.db.model.User
import org.kert0n.medappserver.services.UserService
import org.kert0n.medappserver.services.security.TokenService
import org.kert0n.medappserver.testutil.UserBuilder
import org.mockito.kotlin.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Import
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import java.util.*

@WebMvcTest(AuthController::class)
@Import(TestSecurityConfig::class)
@TestPropertySource(properties = [
    "registration.secret=test-secret",
    "authentication.term=3600000"
])
class AuthControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @MockBean
    private lateinit var userService: UserService

    @MockBean
    private lateinit var tokenService: TokenService

    private val testUserId = UUID.randomUUID()
    private val testSecret = "test-secret"

    // =============== POST /auth/register Tests ===============

    @Test
    fun `register - happy path - returns login and key`() {
        doNothing().whenever(userService).registerNewUser(any(), any())

        val result = mockMvc.perform(
            post("/auth/register")
                .param("secret", testSecret)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.login").exists())
            .andExpect(jsonPath("$.key").exists())
            .andReturn()

        val response = objectMapper.readTree(result.response.contentAsString)
        val login = response.get("login").asText()
        val key = response.get("key").asText()

        // Verify UUID format for login
        UUID.fromString(login)

        // Verify key is base64 encoded and not empty
        assert(key.isNotEmpty()) { "Key should not be empty" }

        verify(userService).registerNewUser(any(), any())
    }

    @Test
    fun `register - unauthorized - wrong secret`() {
        mockMvc.perform(
            post("/auth/register")
                .param("secret", "wrong-secret")
        )
            .andExpect(status().isUnauthorized)

        verifyNoInteractions(userService)
    }

    @Test
    fun `register - multiple registrations - each gets unique credentials`() {
        doNothing().whenever(userService).registerNewUser(any(), any())

        val result1 = mockMvc.perform(
            post("/auth/register")
                .param("secret", testSecret)
        )
            .andExpect(status().isOk)
            .andReturn()

        val result2 = mockMvc.perform(
            post("/auth/register")
                .param("secret", testSecret)
        )
            .andExpect(status().isOk)
            .andReturn()

        val response1 = objectMapper.readTree(result1.response.contentAsString)
        val response2 = objectMapper.readTree(result2.response.contentAsString)

        val login1 = response1.get("login").asText()
        val login2 = response2.get("login").asText()
        val key1 = response1.get("key").asText()
        val key2 = response2.get("key").asText()

        assert(login1 != login2) { "Logins should be unique" }
        assert(key1 != key2) { "Keys should be unique" }
    }

    @Test
    fun `register - key is properly encoded - base64 format`() {
        doNothing().whenever(userService).registerNewUser(any(), any())

        val result = mockMvc.perform(
            post("/auth/register")
                .param("secret", testSecret)
        )
            .andExpect(status().isOk)
            .andReturn()

        val response = objectMapper.readTree(result.response.contentAsString)
        val key = response.get("key").asText()

        // Verify it's a valid base64 string (basic check)
        assert(key.matches(Regex("[A-Za-z0-9+/=]+"))) { "Key should be base64 encoded" }
    }

    // =============== GET /auth/login Tests ===============

    @Test
    fun `login - happy path - returns JWT token`() {
        val user = UserBuilder().withId(testUserId).build()
        val token = "test.jwt.token"
        whenever(tokenService.generateToken(user, 3600000)).thenReturn(token)

        mockMvc.perform(
            get("/auth/login")
                .with(user(user))
        )
            .andExpect(status().isOk)
            .andExpect(content().string(token))

        verify(tokenService).generateToken(user, 3600000)
    }

    @Test
    fun `login - unauthorized - no authentication`() {
        mockMvc.perform(get("/auth/login"))
            .andExpect(status().isUnauthorized)

        verifyNoInteractions(tokenService)
    }

    @Test
    fun `login - token has correct term - uses configured value`() {
        val user = UserBuilder().withId(testUserId).build()
        val token = "test.jwt.token"
        whenever(tokenService.generateToken(user, 3600000)).thenReturn(token)

        mockMvc.perform(
            get("/auth/login")
                .with(user(user))
        )
            .andExpect(status().isOk)

        verify(tokenService).generateToken(user, 3600000) // 1 hour in milliseconds
    }

    @Test
    fun `login - repeated requests - generates new tokens each time`() {
        val user = UserBuilder().withId(testUserId).build()
        whenever(tokenService.generateToken(user, 3600000))
            .thenReturn("token1", "token2")

        val result1 = mockMvc.perform(
            get("/auth/login")
                .with(user(user))
        )
            .andExpect(status().isOk)
            .andReturn()

        val result2 = mockMvc.perform(
            get("/auth/login")
                .with(user(user))
        )
            .andExpect(status().isOk)
            .andReturn()

        val token1 = result1.response.contentAsString
        val token2 = result2.response.contentAsString

        assert(token1 == "token1")
        assert(token2 == "token2")
        verify(tokenService, times(2)).generateToken(user, 3600000)
    }

    @Test
    fun `login - token is valid JWT format - contains required parts`() {
        val user = UserBuilder().withId(testUserId).build()
        // Simulate a real JWT token structure
        val token = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c"
        whenever(tokenService.generateToken(user, 3600000)).thenReturn(token)

        val result = mockMvc.perform(
            get("/auth/login")
                .with(user(user))
        )
            .andExpect(status().isOk)
            .andReturn()

        val returnedToken = result.response.contentAsString

        // Verify it's in JWT format (3 parts separated by dots)
        val parts = returnedToken.split(".")
        assert(parts.size == 3) { "JWT should have 3 parts: header, payload, signature" }
    }

    @Test
    fun `login - different users - get different tokens`() {
        val user1 = UserBuilder().withId(UUID.randomUUID()).build()
        val user2 = UserBuilder().withId(UUID.randomUUID()).build()
        whenever(tokenService.generateToken(user1, 3600000)).thenReturn("token-for-user1")
        whenever(tokenService.generateToken(user2, 3600000)).thenReturn("token-for-user2")

        val result1 = mockMvc.perform(
            get("/auth/login")
                .with(user(user1))
        )
            .andExpect(status().isOk)
            .andReturn()

        val result2 = mockMvc.perform(
            get("/auth/login")
                .with(user(user2))
        )
            .andExpect(status().isOk)
            .andReturn()

        assert(result1.response.contentAsString == "token-for-user1")
        assert(result2.response.contentAsString == "token-for-user2")
    }

    @Test
    fun `register - no PII stored - only login and key generated`() {
        doNothing().whenever(userService).registerNewUser(any(), any())

        val result = mockMvc.perform(
            post("/auth/register")
                .param("secret", testSecret)
        )
            .andExpect(status().isOk)
            .andReturn()

        val response = objectMapper.readTree(result.response.contentAsString)

        // Verify only login and key are present
        assert(response.has("login"))
        assert(response.has("key"))
        assert(!response.has("email")) { "Should not have email" }
        assert(!response.has("name")) { "Should not have name" }
        assert(!response.has("phone")) { "Should not have phone" }
        assert(response.size() == 2) { "Should only have login and key fields" }
    }
}
