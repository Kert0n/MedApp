package org.kert0n.medappserver.controller

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.kert0n.medappserver.db.model.*
import org.kert0n.medappserver.services.MedKitService
import org.kert0n.medappserver.testutil.MedKitBuilder
import org.kert0n.medappserver.testutil.UserBuilder
import org.mockito.kotlin.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.web.server.ResponseStatusException
import java.util.*

@WebMvcTest(MedKitController::class)
@Import(TestSecurityConfig::class)
class MedKitControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @MockBean
    private lateinit var medKitService: MedKitService

    private val testUserId = UUID.randomUUID()
    private val testMedKitId = UUID.randomUUID()

    // =============== POST /med-kit Tests ===============

    @Test
    fun `createNew - happy path - returns 201 with medkit ID`() {
        val user = UserBuilder().withId(testUserId).build()
        val medKit = MedKitBuilder().withId(testMedKitId).withUsers(mutableSetOf(user)).build()
        whenever(medKitService.createNew(testUserId)).thenReturn(medKit)

        mockMvc.perform(
            post("/med-kit")
                .with(user(testUserId.toString()))
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.id").value(testMedKitId.toString()))

        verify(medKitService).createNew(testUserId)
    }

    @Test
    fun `createNew - unauthorized - returns 401`() {
        mockMvc.perform(post("/med-kit"))
            .andExpect(status().isUnauthorized)

        verifyNoInteractions(medKitService)
    }

    @Test
    fun `createNew - multiple medkits - each gets unique ID`() {
        val medKit1 = MedKitBuilder().withId(UUID.randomUUID()).build()
        val medKit2 = MedKitBuilder().withId(UUID.randomUUID()).build()
        whenever(medKitService.createNew(testUserId)).thenReturn(medKit1, medKit2)

        val result1 = mockMvc.perform(post("/med-kit").with(user(testUserId.toString())))
            .andExpect(status().isCreated)
            .andReturn()

        val result2 = mockMvc.perform(post("/med-kit").with(user(testUserId.toString())))
            .andExpect(status().isCreated)
            .andReturn()

        val id1 = objectMapper.readTree(result1.response.contentAsString).get("id").asText()
        val id2 = objectMapper.readTree(result2.response.contentAsString).get("id").asText()
        assert(id1 != id2) { "Created medkit IDs should be different" }
    }

    // =============== GET /med-kit/{id} Tests ===============

    @Test
    fun `getMedKit - happy path - returns medkit DTO`() {
        val user = UserBuilder().withId(testUserId).build()
        val medKit = MedKitBuilder().withId(testMedKitId).withUsers(mutableSetOf(user)).build()
        whenever(medKitService.findByIdForUser(testMedKitId, testUserId)).thenReturn(medKit)
        whenever(medKitService.toMedKitDTO(medKit)).thenReturn(
            MedKitDTO(id = testMedKitId, users = setOf(), drugs = setOf())
        )

        mockMvc.perform(
            get("/med-kit/{id}", testMedKitId)
                .with(user(testUserId.toString()))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(testMedKitId.toString()))

        verify(medKitService).findByIdForUser(testMedKitId, testUserId)
        verify(medKitService).toMedKitDTO(medKit)
    }

    @Test
    fun `getMedKit - not found - returns 404`() {
        whenever(medKitService.findByIdForUser(any(), any()))
            .thenThrow(ResponseStatusException(HttpStatus.NOT_FOUND, "MedKit not found"))

        mockMvc.perform(
            get("/med-kit/{id}", testMedKitId)
                .with(user(testUserId.toString()))
        )
            .andExpect(status().isNotFound)
    }

    @Test
    fun `getMedKit - forbidden - user has no access`() {
        whenever(medKitService.findByIdForUser(any(), any()))
            .thenThrow(ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied"))

        mockMvc.perform(
            get("/med-kit/{id}", testMedKitId)
                .with(user(testUserId.toString()))
        )
            .andExpect(status().isForbidden)
    }

    @Test
    fun `getMedKit - shared medkit - returns medkit for participant`() {
        val user1 = UserBuilder().withId(testUserId).build()
        val user2 = UserBuilder().withId(UUID.randomUUID()).build()
        val medKit = MedKitBuilder()
            .withId(testMedKitId)
            .withUsers(mutableSetOf(user1, user2))
            .build()
        whenever(medKitService.findByIdForUser(testMedKitId, testUserId)).thenReturn(medKit)
        whenever(medKitService.toMedKitDTO(medKit)).thenReturn(
            MedKitDTO(id = testMedKitId, users = setOf(), drugs = setOf())
        )

        mockMvc.perform(
            get("/med-kit/{id}", testMedKitId)
                .with(user(testUserId.toString()))
        )
            .andExpect(status().isOk)
    }

    // =============== GET /med-kit Tests ===============

    @Test
    fun `getAllMedKits - happy path - returns list of medkit summaries`() {
        val user = UserBuilder().withId(testUserId).build()
        val medKit1 = MedKitBuilder().withId(UUID.randomUUID()).withUsers(mutableSetOf(user)).build()
        val medKit2 = MedKitBuilder().withId(UUID.randomUUID()).withUsers(mutableSetOf(user)).build()
        whenever(medKitService.findAllByUser(testUserId)).thenReturn(listOf(medKit1, medKit2))

        mockMvc.perform(
            get("/med-kit")
                .with(user(testUserId.toString()))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$").isArray)
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].id").exists())
            .andExpect(jsonPath("$[0].userCount").value(1))
            .andExpect(jsonPath("$[0].drugCount").value(0))

        verify(medKitService).findAllByUser(testUserId)
    }

    @Test
    fun `getAllMedKits - user has no medkits - returns empty array`() {
        whenever(medKitService.findAllByUser(testUserId)).thenReturn(emptyList())

        mockMvc.perform(
            get("/med-kit")
                .with(user(testUserId.toString()))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$").isArray)
            .andExpect(jsonPath("$").isEmpty)
    }

    @Test
    fun `getAllMedKits - only personal medkits - returns all personal`() {
        val user = UserBuilder().withId(testUserId).build()
        val medKit1 = MedKitBuilder().withId(UUID.randomUUID()).withUsers(mutableSetOf(user)).build()
        val medKit2 = MedKitBuilder().withId(UUID.randomUUID()).withUsers(mutableSetOf(user)).build()
        whenever(medKitService.findAllByUser(testUserId)).thenReturn(listOf(medKit1, medKit2))

        mockMvc.perform(
            get("/med-kit")
                .with(user(testUserId.toString()))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(2))
    }

    @Test
    fun `getAllMedKits - mixed personal and shared - returns all accessible`() {
        val user1 = UserBuilder().withId(testUserId).build()
        val user2 = UserBuilder().withId(UUID.randomUUID()).build()
        val personalMedKit = MedKitBuilder().withId(UUID.randomUUID()).withUsers(mutableSetOf(user1)).build()
        val sharedMedKit = MedKitBuilder().withId(UUID.randomUUID()).withUsers(mutableSetOf(user1, user2)).build()
        whenever(medKitService.findAllByUser(testUserId)).thenReturn(listOf(personalMedKit, sharedMedKit))

        mockMvc.perform(
            get("/med-kit")
                .with(user(testUserId.toString()))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[1].userCount").value(2))
    }

    @Test
    fun `getAllMedKits - unauthorized - returns 401`() {
        mockMvc.perform(get("/med-kit"))
            .andExpect(status().isUnauthorized)

        verifyNoInteractions(medKitService)
    }

    // =============== POST /med-kit/{id}/share Tests ===============

    @Test
    fun `addUserToMedKit - happy path - adds user and returns medkit`() {
        val newUserId = UUID.randomUUID()
        val request = AddUserRequest(userId = newUserId)
        val user1 = UserBuilder().withId(testUserId).build()
        val medKit = MedKitBuilder().withId(testMedKitId).withUsers(mutableSetOf(user1)).build()
        val updatedMedKit = MedKitBuilder().withId(testMedKitId).build()

        whenever(medKitService.findByIdForUser(testMedKitId, testUserId)).thenReturn(medKit)
        whenever(medKitService.addUserToMedKit(testMedKitId, newUserId)).thenReturn(updatedMedKit)
        whenever(medKitService.toMedKitDTO(updatedMedKit)).thenReturn(
            MedKitDTO(id = testMedKitId, users = setOf(), drugs = setOf())
        )

        mockMvc.perform(
            post("/med-kit/{id}/share", testMedKitId)
                .with(user(testUserId.toString()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(testMedKitId.toString()))

        verify(medKitService).findByIdForUser(testMedKitId, testUserId)
        verify(medKitService).addUserToMedKit(testMedKitId, newUserId)
    }

    @Test
    fun `addUserToMedKit - not found - medkit does not exist`() {
        val request = AddUserRequest(userId = UUID.randomUUID())
        whenever(medKitService.findByIdForUser(any(), any()))
            .thenThrow(ResponseStatusException(HttpStatus.NOT_FOUND, "MedKit not found"))

        mockMvc.perform(
            post("/med-kit/{id}/share", testMedKitId)
                .with(user(testUserId.toString()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isNotFound)
    }

    @Test
    fun `addUserToMedKit - not found - user to add does not exist`() {
        val request = AddUserRequest(userId = UUID.randomUUID())
        val medKit = MedKitBuilder().withId(testMedKitId).build()
        whenever(medKitService.findByIdForUser(testMedKitId, testUserId)).thenReturn(medKit)
        whenever(medKitService.addUserToMedKit(any(), any()))
            .thenThrow(ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"))

        mockMvc.perform(
            post("/med-kit/{id}/share", testMedKitId)
                .with(user(testUserId.toString()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isNotFound)
    }

    @Test
    fun `addUserToMedKit - forbidden - requester has no access to medkit`() {
        val request = AddUserRequest(userId = UUID.randomUUID())
        whenever(medKitService.findByIdForUser(any(), any()))
            .thenThrow(ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied"))

        mockMvc.perform(
            post("/med-kit/{id}/share", testMedKitId)
                .with(user(testUserId.toString()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isForbidden)
    }

    @Test
    fun `addUserToMedKit - idempotent - adding same user twice`() {
        val newUserId = UUID.randomUUID()
        val request = AddUserRequest(userId = newUserId)
        val medKit = MedKitBuilder().withId(testMedKitId).build()
        whenever(medKitService.findByIdForUser(testMedKitId, testUserId)).thenReturn(medKit)
        whenever(medKitService.addUserToMedKit(testMedKitId, newUserId)).thenReturn(medKit)
        whenever(medKitService.toMedKitDTO(medKit)).thenReturn(
            MedKitDTO(id = testMedKitId, users = setOf(), drugs = setOf())
        )

        mockMvc.perform(
            post("/med-kit/{id}/share", testMedKitId)
                .with(user(testUserId.toString()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isOk)
    }

    @Test
    fun `addUserToMedKit - validation error - missing userId in body`() {
        val invalidRequest = mapOf<String, Any>()

        mockMvc.perform(
            post("/med-kit/{id}/share", testMedKitId)
                .with(user(testUserId.toString()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidRequest))
        )
            .andExpect(status().isBadRequest)

        verify(medKitService, never()).addUserToMedKit(any(), any())
    }

    // =============== DELETE /med-kit/{id}/leave Tests ===============

    @Test
    fun `leaveMedKit - happy path - returns 204`() {
        doNothing().whenever(medKitService).removeUserFromMedKit(testMedKitId, testUserId)

        mockMvc.perform(
            delete("/med-kit/{id}/leave", testMedKitId)
                .with(user(testUserId.toString()))
        )
            .andExpect(status().isNoContent)

        verify(medKitService).removeUserFromMedKit(testMedKitId, testUserId)
    }

    @Test
    fun `leaveMedKit - not found - medkit does not exist`() {
        doThrow(ResponseStatusException(HttpStatus.NOT_FOUND, "MedKit not found"))
            .whenever(medKitService).removeUserFromMedKit(any(), any())

        mockMvc.perform(
            delete("/med-kit/{id}/leave", testMedKitId)
                .with(user(testUserId.toString()))
        )
            .andExpect(status().isNotFound)
    }

    @Test
    fun `leaveMedKit - last user - medkit is deleted`() {
        doNothing().whenever(medKitService).removeUserFromMedKit(testMedKitId, testUserId)

        mockMvc.perform(
            delete("/med-kit/{id}/leave", testMedKitId)
                .with(user(testUserId.toString()))
        )
            .andExpect(status().isNoContent)
    }

    @Test
    fun `leaveMedKit - with user treatment plans - deletes user plans`() {
        doNothing().whenever(medKitService).removeUserFromMedKit(testMedKitId, testUserId)

        mockMvc.perform(
            delete("/med-kit/{id}/leave", testMedKitId)
                .with(user(testUserId.toString()))
        )
            .andExpect(status().isNoContent)
    }

    // =============== DELETE /med-kit/{id} Tests ===============

    @Test
    fun `deleteMedKit - without transfer - returns 204`() {
        doNothing().whenever(medKitService).delete(testMedKitId, testUserId, null)

        mockMvc.perform(
            delete("/med-kit/{id}", testMedKitId)
                .with(user(testUserId.toString()))
        )
            .andExpect(status().isNoContent)

        verify(medKitService).delete(testMedKitId, testUserId, null)
    }

    @Test
    fun `deleteMedKit - with transfer - moves drugs and deletes`() {
        val targetMedKitId = UUID.randomUUID()
        doNothing().whenever(medKitService).delete(testMedKitId, testUserId, targetMedKitId)

        mockMvc.perform(
            delete("/med-kit/{id}", testMedKitId)
                .with(user(testUserId.toString()))
                .param("transferToMedKitId", targetMedKitId.toString())
        )
            .andExpect(status().isNoContent)

        verify(medKitService).delete(testMedKitId, testUserId, targetMedKitId)
    }

    @Test
    fun `deleteMedKit - not found - medkit does not exist`() {
        doThrow(ResponseStatusException(HttpStatus.NOT_FOUND, "MedKit not found"))
            .whenever(medKitService).delete(any(), any(), any())

        mockMvc.perform(
            delete("/med-kit/{id}", testMedKitId)
                .with(user(testUserId.toString()))
        )
            .andExpect(status().isNotFound)
    }

    @Test
    fun `deleteMedKit - not found - target medkit for transfer does not exist`() {
        val targetMedKitId = UUID.randomUUID()
        doThrow(ResponseStatusException(HttpStatus.NOT_FOUND, "Target MedKit not found"))
            .whenever(medKitService).delete(any(), any(), eq(targetMedKitId))

        mockMvc.perform(
            delete("/med-kit/{id}", testMedKitId)
                .with(user(testUserId.toString()))
                .param("transferToMedKitId", targetMedKitId.toString())
        )
            .andExpect(status().isNotFound)
    }

    @Test
    fun `deleteMedKit - forbidden - user has no access to source medkit`() {
        doThrow(ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied"))
            .whenever(medKitService).delete(any(), any(), any())

        mockMvc.perform(
            delete("/med-kit/{id}", testMedKitId)
                .with(user(testUserId.toString()))
        )
            .andExpect(status().isForbidden)
    }

    @Test
    fun `deleteMedKit - forbidden - user has no access to target medkit`() {
        val targetMedKitId = UUID.randomUUID()
        doThrow(ResponseStatusException(HttpStatus.FORBIDDEN, "No access to target medkit"))
            .whenever(medKitService).delete(any(), any(), eq(targetMedKitId))

        mockMvc.perform(
            delete("/med-kit/{id}", testMedKitId)
                .with(user(testUserId.toString()))
                .param("transferToMedKitId", targetMedKitId.toString())
        )
            .andExpect(status().isForbidden)
    }

    @Test
    fun `deleteMedKit - shared medkit - only removes user not entire medkit`() {
        // For shared medkit, delete should behave like leave
        doNothing().whenever(medKitService).delete(testMedKitId, testUserId, null)

        mockMvc.perform(
            delete("/med-kit/{id}", testMedKitId)
                .with(user(testUserId.toString()))
        )
            .andExpect(status().isNoContent)
    }
}
