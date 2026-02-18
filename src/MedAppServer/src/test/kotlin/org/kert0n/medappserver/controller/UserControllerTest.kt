package org.kert0n.medappserver.controller

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.kert0n.medappserver.db.model.*
import org.kert0n.medappserver.services.MedKitService
import org.kert0n.medappserver.services.UserService
import org.kert0n.medappserver.testutil.MedKitBuilder
import org.kert0n.medappserver.testutil.UserBuilder
import org.mockito.kotlin.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Import
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import java.util.*

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

    private val testUserId = UUID.randomUUID()

    // =============== GET /user Tests ===============

    @Test
    fun `getAllDataForUser - happy path - returns user with medkits`() {
        val user = UserBuilder().withId(testUserId).build()
        val medKit1 = MedKitBuilder().withId(UUID.randomUUID()).withUsers(mutableSetOf(user)).build()
        val medKit2 = MedKitBuilder().withId(UUID.randomUUID()).withUsers(mutableSetOf(user)).build()

        whenever(userService.findById(testUserId)).thenReturn(user)
        whenever(medKitService.findAllByUser(testUserId)).thenReturn(listOf(medKit1, medKit2))
        whenever(medKitService.toMedKitDTO(any())).thenReturn(
            MedKitDTO(id = UUID.randomUUID(), users = setOf(), drugs = setOf())
        )

        mockMvc.perform(
            get("/user")
                .with(user(testUserId.toString()))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(testUserId.toString()))
            .andExpect(jsonPath("$.medKits").isArray)
            .andExpect(jsonPath("$.medKits.length()").value(2))

        verify(userService).findById(testUserId)
        verify(medKitService).findAllByUser(testUserId)
    }

    @Test
    fun `getAllDataForUser - user has no medkits - returns user with empty medkits`() {
        val user = UserBuilder().withId(testUserId).build()
        whenever(userService.findById(testUserId)).thenReturn(user)
        whenever(medKitService.findAllByUser(testUserId)).thenReturn(emptyList())

        mockMvc.perform(
            get("/user")
                .with(user(testUserId.toString()))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(testUserId.toString()))
            .andExpect(jsonPath("$.medKits").isArray)
            .andExpect(jsonPath("$.medKits").isEmpty)
    }

    @Test
    fun `getAllDataForUser - user has multiple medkits - returns all`() {
        val user = UserBuilder().withId(testUserId).build()
        val medKits = (1..5).map {
            MedKitBuilder().withId(UUID.randomUUID()).withUsers(mutableSetOf(user)).build()
        }
        whenever(userService.findById(testUserId)).thenReturn(user)
        whenever(medKitService.findAllByUser(testUserId)).thenReturn(medKits)
        whenever(medKitService.toMedKitDTO(any())).thenReturn(
            MedKitDTO(id = UUID.randomUUID(), users = setOf(), drugs = setOf())
        )

        mockMvc.perform(
            get("/user")
                .with(user(testUserId.toString()))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.medKits.length()").value(5))
    }

    @Test
    fun `getAllDataForUser - shared and personal medkits - returns all accessible`() {
        val user = UserBuilder().withId(testUserId).build()
        val otherUser = UserBuilder().withId(UUID.randomUUID()).build()
        val personalMedKit = MedKitBuilder().withId(UUID.randomUUID()).withUsers(mutableSetOf(user)).build()
        val sharedMedKit = MedKitBuilder().withId(UUID.randomUUID()).withUsers(mutableSetOf(user, otherUser)).build()

        whenever(userService.findById(testUserId)).thenReturn(user)
        whenever(medKitService.findAllByUser(testUserId)).thenReturn(listOf(personalMedKit, sharedMedKit))
        whenever(medKitService.toMedKitDTO(any())).thenReturn(
            MedKitDTO(id = UUID.randomUUID(), users = setOf(), drugs = setOf())
        )

        mockMvc.perform(
            get("/user")
                .with(user(testUserId.toString()))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.medKits.length()").value(2))
    }

    @Test
    fun `getAllDataForUser - unauthorized - returns 401`() {
        mockMvc.perform(get("/user"))
            .andExpect(status().isUnauthorized)

        verifyNoInteractions(userService)
        verifyNoInteractions(medKitService)
    }

    @Test
    fun `getAllDataForUser - no PII exposed - only ID and medkits`() {
        val user = UserBuilder().withId(testUserId).build()
        whenever(userService.findById(testUserId)).thenReturn(user)
        whenever(medKitService.findAllByUser(testUserId)).thenReturn(emptyList())

        val result = mockMvc.perform(
            get("/user")
                .with(user(testUserId.toString()))
        )
            .andExpect(status().isOk)
            .andReturn()

        val jsonResponse = objectMapper.readTree(result.response.contentAsString)
        // Ensure only id and medKits fields are present, no personal info
        assert(jsonResponse.has("id"))
        assert(jsonResponse.has("medKits"))
        assert(!jsonResponse.has("hashedKey")) { "Should not expose hashedKey" }
        assert(!jsonResponse.has("email")) { "Should not expose email" }
        assert(!jsonResponse.has("name")) { "Should not expose name" }
    }

    @Test
    fun `getAllDataForUser - returns distinct medkits only`() {
        val user = UserBuilder().withId(testUserId).build()
        val medKit = MedKitBuilder().withId(UUID.randomUUID()).withUsers(mutableSetOf(user)).build()
        whenever(userService.findById(testUserId)).thenReturn(user)
        whenever(medKitService.findAllByUser(testUserId)).thenReturn(listOf(medKit))
        whenever(medKitService.toMedKitDTO(medKit)).thenReturn(
            MedKitDTO(id = medKit.id, users = setOf(), drugs = setOf())
        )

        mockMvc.perform(
            get("/user")
                .with(user(testUserId.toString()))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.medKits.length()").value(1))

        verify(medKitService, times(1)).toMedKitDTO(medKit)
    }
}
