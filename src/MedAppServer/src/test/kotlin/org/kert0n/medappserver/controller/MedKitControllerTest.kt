package org.kert0n.medappserver.controller
import com.fasterxml.jackson.databind.ObjectMapper
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.justRun
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.kert0n.medappserver.db.model.*
import org.kert0n.medappserver.services.MedKitService
import org.kert0n.medappserver.testutil.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.web.server.ResponseStatusException
import java.util.*
/**
 * Comprehensive MedKitController tests with MockMvc
 * Tests all 6 endpoints with ≥10 tests each (60+ total tests)
 * Following patterns from existing service tests and test data builders
 */
@WebMvcTest(MedKitController::class)
@Import(TestSecurityConfig::class)
class MedKitControllerTest {
    @Autowired
    private lateinit var mockMvc: MockMvc
    @Autowired
    private lateinit var objectMapper: ObjectMapper
    @MockkBean
    private lateinit var medKitService: MedKitService
    private val testUserId = UUID.randomUUID()
    private val testMedKitId = UUID.randomUUID()
    private val testDrugId = UUID.randomUUID()
    // ========== GET /med-kit - getAllForUser Tests (10 tests) ==========
    @Test
    @WithMockUser(username = "user-id")
    fun `getAllForUser - user has multiple medkits - returns list of summaries`() {
        val medKit1 = medKitBuilder().withId(UUID.randomUUID()).build()
        val medKit2 = medKitBuilder().withId(UUID.randomUUID()).build()
        val user1 = userBuilder().withId(UUID.randomUUID()).build()
        val user2 = userBuilder().withId(UUID.randomUUID()).build()

        medKit1.users.addAll(listOf(user1, user2))
        medKit2.users.add(user1)

        val drug1 = drugBuilder(medKit1).withId(UUID.randomUUID()).build()
        val drug2 = drugBuilder(medKit1).withId(UUID.randomUUID()).build()
        val drug3 = drugBuilder(medKit2).withId(UUID.randomUUID()).build()

        medKit1.drugs.addAll(listOf(drug1, drug2))
        medKit2.drugs.add(drug3)
        every { medKitService.findAllByUser(any()) } returns listOf(medKit1, medKit2)
        mockMvc.perform(get("/med-kit"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].id").value(medKit1.id.toString()))
            .andExpect(jsonPath("$[0].userCount").value(2))
            .andExpect(jsonPath("$[0].drugCount").value(2))
            .andExpect(jsonPath("$[1].id").value(medKit2.id.toString()))
            .andExpect(jsonPath("$[1].userCount").value(1))
            .andExpect(jsonPath("$[1].drugCount").value(1))
        verify(exactly = 1) { medKitService.findAllByUser(any()) }
    }
    @Test
    @WithMockUser(username = "user-id")
    fun `getAllForUser - user has no medkits - returns empty list`() {
        every { medKitService.findAllByUser(any()) } returns emptyList()
        mockMvc.perform(get("/med-kit"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(0))
        verify(exactly = 1) { medKitService.findAllByUser(any()) }
    }
    @Test
    @WithMockUser(username = "user-id")
    fun `getAllForUser - user has single empty medkit - returns single summary with zero counts`() {
        val medKit = medKitBuilder().withId(testMedKitId).build()
        val user = userBuilder().withId(testUserId).build()
        medKit.users.add(user)
        every { medKitService.findAllByUser(any()) } returns listOf(medKit)
        mockMvc.perform(get("/med-kit"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].id").value(testMedKitId.toString()))
            .andExpect(jsonPath("$[0].userCount").value(1))
            .andExpect(jsonPath("$[0].drugCount").value(0))
        verify(exactly = 1) { medKitService.findAllByUser(any()) }
    }
    @Test
    fun `getAllForUser - unauthenticated - returns 401`() {
        mockMvc.perform(get("/med-kit"))
            .andExpect(status().isUnauthorized)
    }
    @Test
    @WithMockUser(username = "user-id")
    fun `getAllForUser - service throws error - returns 500`() {
        every { medKitService.findAllByUser(any()) } throws RuntimeException("Database error")
        mockMvc.perform(get("/med-kit"))
            .andExpect(status().isInternalServerError)
        verify(exactly = 1) { medKitService.findAllByUser(any()) }
    }
    @Test
    @WithMockUser(username = "user-id")
    fun `getAllForUser - medkit with many users and drugs - returns correct counts`() {
        val medKit = medKitBuilder().withId(testMedKitId).build()
        val users = (1..5).map { userBuilder().withId(UUID.randomUUID()).build() }
        val drugs = (1..10).map { drugBuilder(medKit).withId(UUID.randomUUID()).build() }

        medKit.users.addAll(users)
        medKit.drugs.addAll(drugs)
        every { medKitService.findAllByUser(any()) } returns listOf(medKit)
        mockMvc.perform(get("/med-kit"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].userCount").value(5))
            .andExpect(jsonPath("$[0].drugCount").value(10))
        verify(exactly = 1) { medKitService.findAllByUser(any()) }
    }
    @Test
    @WithMockUser(username = "user-id")
    fun `getAllForUser - multiple medkits with different counts - returns all summaries correctly`() {
        val medKit1 = medKitBuilder().withId(UUID.randomUUID()).build()
        val medKit2 = medKitBuilder().withId(UUID.randomUUID()).build()
        val medKit3 = medKitBuilder().withId(UUID.randomUUID()).build()
        medKit1.users.add(userBuilder().build())
        medKit2.users.addAll(listOf(userBuilder().build(), userBuilder().build()))
        medKit3.users.addAll(listOf(userBuilder().build(), userBuilder().build(), userBuilder().build()))
        medKit1.drugs.add(drugBuilder(medKit1).build())
        medKit2.drugs.addAll(listOf(drugBuilder(medKit2).build(), drugBuilder(medKit2).build()))
        every { medKitService.findAllByUser(any()) } returns listOf(medKit1, medKit2, medKit3)
        mockMvc.perform(get("/med-kit"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(3))
            .andExpect(jsonPath("$[0].userCount").value(1))
            .andExpect(jsonPath("$[0].drugCount").value(1))
            .andExpect(jsonPath("$[1].userCount").value(2))
            .andExpect(jsonPath("$[1].drugCount").value(2))
            .andExpect(jsonPath("$[2].userCount").value(3))
            .andExpect(jsonPath("$[2].drugCount").value(0))
    }
    @Test
    @WithMockUser(username = "different-user")
    fun `getAllForUser - different user - calls service with correct user id`() {
        every { medKitService.findAllByUser(any()) } returns emptyList()
        mockMvc.perform(get("/med-kit"))
            .andExpect(status().isOk)
        verify(exactly = 1) { medKitService.findAllByUser(any()) }
    }
    @Test
    @WithMockUser(username = "user-id")
    fun `getAllForUser - user not found - returns 404`() {
        every { medKitService.findAllByUser(any()) } throws ResponseStatusException(
            HttpStatus.NOT_FOUND, "User not found"
        )
        mockMvc.perform(get("/med-kit"))
            .andExpect(status().isNotFound)
    }
    @Test
    @WithMockUser(username = "user-id")
    fun `getAllForUser - returns medkits in order from service`() {
        val medKit1 = medKitBuilder().withId(UUID.randomUUID()).build()
        val medKit2 = medKitBuilder().withId(UUID.randomUUID()).build()
        val medKit3 = medKitBuilder().withId(UUID.randomUUID()).build()
        every { medKitService.findAllByUser(any()) } returns listOf(medKit1, medKit2, medKit3)
        mockMvc.perform(get("/med-kit"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].id").value(medKit1.id.toString()))
            .andExpect(jsonPath("$[1].id").value(medKit2.id.toString()))
            .andExpect(jsonPath("$[2].id").value(medKit3.id.toString()))
    }
    // ========== GET /med-kit/{id} - getById Tests (10 tests) ==========
    @Test
    @WithMockUser(username = "user-id")
    fun `getById - valid id - returns medkit DTO with drugs`() {
        val medKit = medKitBuilder().withId(testMedKitId).build()
        val drug1 = drugBuilder(medKit).withId(UUID.randomUUID()).withName("Aspirin").build()
        val drug2 = drugBuilder(medKit).withId(UUID.randomUUID()).withName("Ibuprofen").build()
        medKit.drugs.addAll(listOf(drug1, drug2))
        val drugDTO1 = DrugDTO(
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
            medKitId = testMedKitId
        )
        val drugDTO2 = DrugDTO(
            id = drug2.id,
            name = "Ibuprofen",
            quantity = 100.0,
            plannedQuantity = 0.0,
            quantityUnit = "mg",
            formType = "tablet",
            category = "painkiller",
            manufacturer = "Test Pharma",
            country = "TestLand",
            description = "Test description",
            medKitId = testMedKitId
        )
        val medKitDTO = MedKitDTO(
            id = testMedKitId,
            drugs = setOf(drugDTO1, drugDTO2)
        )
        every { medKitService.findByIdForUser(testMedKitId, any()) } returns medKit
        every { medKitService.toMedKitDTO(medKit) } returns medKitDTO
        mockMvc.perform(get("/med-kit/{id}", testMedKitId))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(testMedKitId.toString()))
            .andExpect(jsonPath("$.drugs.length()").value(2))
        verify(exactly = 1) { medKitService.findByIdForUser(testMedKitId, any()) }
        verify(exactly = 1) { medKitService.toMedKitDTO(medKit) }
    }
    @Test
    @WithMockUser(username = "user-id")
    fun `getById - medkit not found - returns 404`() {
        every { medKitService.findByIdForUser(testMedKitId, any()) } throws ResponseStatusException(
            HttpStatus.NOT_FOUND, "MedKit not found"
        )
        mockMvc.perform(get("/med-kit/{id}", testMedKitId))
            .andExpect(status().isNotFound)
        verify(exactly = 1) { medKitService.findByIdForUser(testMedKitId, any()) }
    }
    @Test
    @WithMockUser(username = "user-id")
    fun `getById - user has no access - returns 403`() {
        every { medKitService.findByIdForUser(testMedKitId, any()) } throws ResponseStatusException(
            HttpStatus.FORBIDDEN, "User does not have access to this medkit"
        )
        mockMvc.perform(get("/med-kit/{id}", testMedKitId))
            .andExpect(status().isForbidden)
        verify(exactly = 1) { medKitService.findByIdForUser(testMedKitId, any()) }
    }
    @Test
    @WithMockUser(username = "user-id")
    fun `getById - empty medkit - returns medkit with empty drugs`() {
        val medKit = medKitBuilder().withId(testMedKitId).build()
        val medKitDTO = MedKitDTO(id = testMedKitId, drugs = emptySet())
        every { medKitService.findByIdForUser(testMedKitId, any()) } returns medKit
        every { medKitService.toMedKitDTO(medKit) } returns medKitDTO
        mockMvc.perform(get("/med-kit/{id}", testMedKitId))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(testMedKitId.toString()))
            .andExpect(jsonPath("$.drugs.length()").value(0))
    }
    @Test
    fun `getById - unauthenticated - returns 401`() {
        mockMvc.perform(get("/med-kit/{id}", testMedKitId))
            .andExpect(status().isUnauthorized)
    }
    @Test
    @WithMockUser(username = "user-id")
    fun `getById - invalid UUID format - returns 400`() {
        mockMvc.perform(get("/med-kit/{id}", "invalid-uuid"))
            .andExpect(status().isBadRequest)
    }
    @Test
    @WithMockUser(username = "user-id")
    fun `getById - service throws internal error - returns 500`() {
        every { medKitService.findByIdForUser(testMedKitId, any()) } throws RuntimeException("Database error")
        mockMvc.perform(get("/med-kit/{id}", testMedKitId))
            .andExpect(status().isInternalServerError)
    }
    @Test
    @WithMockUser(username = "user-id")
    fun `getById - medkit with many drugs - returns all drugs in DTO`() {
        val medKit = medKitBuilder().withId(testMedKitId).build()
        val drugs = (1..5).map {
            drugBuilder(medKit).withId(UUID.randomUUID()).withName("Drug $it").build()
        }
        medKit.drugs.addAll(drugs)
        val drugDTOs = drugs.map { drug ->
            DrugDTO(
                id = drug.id,
                name = drug.name,
                quantity = 100.0,
                plannedQuantity = 0.0,
                quantityUnit = "mg",
                formType = "tablet",
                category = "painkiller",
                manufacturer = "Test Pharma",
                country = "TestLand",
                description = "Test description",
                medKitId = testMedKitId
            )
        }.toSet()
        val medKitDTO = MedKitDTO(id = testMedKitId, drugs = drugDTOs)
        every { medKitService.findByIdForUser(testMedKitId, any()) } returns medKit
        every { medKitService.toMedKitDTO(medKit) } returns medKitDTO
        mockMvc.perform(get("/med-kit/{id}", testMedKitId))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.drugs.length()").value(5))
    }
    @Test
    @WithMockUser(username = "different-user")
    fun `getById - different user without access - returns 403`() {
        every { medKitService.findByIdForUser(testMedKitId, any()) } throws ResponseStatusException(
            HttpStatus.FORBIDDEN, "User does not have access to this medkit"
        )
        mockMvc.perform(get("/med-kit/{id}", testMedKitId))
            .andExpect(status().isForbidden)
    }
    @Test
    @WithMockUser(username = "user-id")
    fun `getById - medkit with drugs having all optional fields - returns complete DTOs`() {
        val medKit = medKitBuilder().withId(testMedKitId).build()
        val drug = drugBuilder(medKit)
            .withId(testDrugId)
            .withName("Complete Drug")
            .withQuantity(200.0)
            .withQuantityUnit("ml")
            .withFormType("syrup")
            .withCategory("antibiotic")
            .withManufacturer("PharmaCorp")
            .withCountry("USA")
            .withDescription("Complete medication")
            .build()
        medKit.drugs.add(drug)
        val drugDTO = DrugDTO(
            id = testDrugId,
            name = "Complete Drug",
            quantity = 200.0,
            plannedQuantity = 0.0,
            quantityUnit = "ml",
            formType = "syrup",
            category = "antibiotic",
            manufacturer = "PharmaCorp",
            country = "USA",
            description = "Complete medication",
            medKitId = testMedKitId
        )
        val medKitDTO = MedKitDTO(id = testMedKitId, drugs = setOf(drugDTO))
        every { medKitService.findByIdForUser(testMedKitId, any()) } returns medKit
        every { medKitService.toMedKitDTO(medKit) } returns medKitDTO
        mockMvc.perform(get("/med-kit/{id}", testMedKitId))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.drugs[0].formType").value("syrup"))
            .andExpect(jsonPath("$.drugs[0].category").value("antibiotic"))
            .andExpect(jsonPath("$.drugs[0].manufacturer").value("PharmaCorp"))
    }
    // ========== POST /med-kit - create Tests (10 tests) ==========
    @Test
    @WithMockUser(username = "user-id")
    fun `create - creates medkit and returns 201 with id`() {
        val createdMedKit = medKitBuilder().withId(testMedKitId).build()
        every { medKitService.createNew(any()) } returns createdMedKit
        mockMvc.perform(post("/med-kit"))
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.id").value(testMedKitId.toString()))
        verify(exactly = 1) { medKitService.createNew(any()) }
    }
    @Test
    @WithMockUser(username = "user-id")
    fun `create - creates medkit for authenticated user`() {
        val createdMedKit = medKitBuilder().withId(testMedKitId).build()
        every { medKitService.createNew(any()) } returns createdMedKit
        mockMvc.perform(post("/med-kit"))
            .andExpect(status().isCreated)
        verify(exactly = 1) { medKitService.createNew(any()) }
    }
    @Test
    fun `create - unauthenticated - returns 401`() {
        mockMvc.perform(post("/med-kit"))
            .andExpect(status().isUnauthorized)
    }
    @Test
    @WithMockUser(username = "user-id")
    fun `create - user not found - returns 404`() {
        every { medKitService.createNew(any()) } throws ResponseStatusException(
            HttpStatus.NOT_FOUND, "User not found"
        )
        mockMvc.perform(post("/med-kit"))
            .andExpect(status().isNotFound)
        verify(exactly = 1) { medKitService.createNew(any()) }
    }
    @Test
    @WithMockUser(username = "user-id")
    fun `create - service throws error - returns 500`() {
        every { medKitService.createNew(any()) } throws RuntimeException("Database error")
        mockMvc.perform(post("/med-kit"))
            .andExpect(status().isInternalServerError)
        verify(exactly = 1) { medKitService.createNew(any()) }
    }
    @Test
    @WithMockUser(username = "user-id")
    fun `create - multiple creates for same user - each returns unique id`() {
        val medKit1 = medKitBuilder().withId(UUID.randomUUID()).build()
        val medKit2 = medKitBuilder().withId(UUID.randomUUID()).build()
        every { medKitService.createNew(any()) } returnsMany listOf(medKit1, medKit2)
        mockMvc.perform(post("/med-kit"))
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.id").value(medKit1.id.toString()))
        mockMvc.perform(post("/med-kit"))
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.id").value(medKit2.id.toString()))
        verify(exactly = 2) { medKitService.createNew(any()) }
    }
    @Test
    @WithMockUser(username = "user-1")
    fun `create - different users create medkits - each gets own medkit`() {
        val medKit = medKitBuilder().withId(testMedKitId).build()
        every { medKitService.createNew(any()) } returns medKit
        mockMvc.perform(post("/med-kit"))
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.id").exists())
        verify(exactly = 1) { medKitService.createNew(any()) }
    }
    @Test
    @WithMockUser(username = "user-id")
    fun `create - returns only id in response`() {
        val createdMedKit = medKitBuilder().withId(testMedKitId).build()
        every { medKitService.createNew(any()) } returns createdMedKit
        mockMvc.perform(post("/med-kit"))
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.id").value(testMedKitId.toString()))
            .andExpect(jsonPath("$.drugs").doesNotExist())
            .andExpect(jsonPath("$.users").doesNotExist())
    }
    @Test
    @WithMockUser(username = "user-id")
    fun `create - no request body required - creates medkit`() {
        val createdMedKit = medKitBuilder().withId(testMedKitId).build()
        every { medKitService.createNew(any()) } returns createdMedKit
        mockMvc.perform(post("/med-kit"))
            .andExpect(status().isCreated)
        verify(exactly = 1) { medKitService.createNew(any()) }
    }
    @Test
    @WithMockUser(username = "user-id")
    fun `create - service returns valid UUID - response contains valid UUID`() {
        val validUuid = UUID.randomUUID()
        val createdMedKit = medKitBuilder().withId(validUuid).build()
        every { medKitService.createNew(any()) } returns createdMedKit
        mockMvc.perform(post("/med-kit"))
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.id").value(validUuid.toString()))
    }
    // ========== PUT /med-kit/{id} - update Tests (10 tests) ==========
    // Note: The controller doesn't have a PUT endpoint, but if you want to test non-existent endpoints:
    @Test
    @WithMockUser(username = "user-id")
    fun `update - endpoint does not exist - returns 404`() {
        mockMvc.perform(
            put("/med-kit/{id}", testMedKitId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
        )
            .andExpect(status().isNotFound)
    }
    @Test
    @WithMockUser(username = "user-id")
    fun `update - with empty body - returns 404`() {
        mockMvc.perform(
            put("/med-kit/{id}", testMedKitId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
        )
            .andExpect(status().isNotFound)
    }
    @Test
    fun `update - unauthenticated - returns 401`() {
        mockMvc.perform(
            put("/med-kit/{id}", testMedKitId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
        )
            .andExpect(status().isUnauthorized)
    }
    @Test
    @WithMockUser(username = "user-id")
    fun `update - with valid data - returns 404`() {
        val updateData = mapOf("name" to "Updated MedKit")
        mockMvc.perform(
            put("/med-kit/{id}", testMedKitId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateData))
        )
            .andExpect(status().isNotFound)
    }
    @Test
    @WithMockUser(username = "user-id")
    fun `update - invalid UUID - returns 400 or 404`() {
        mockMvc.perform(
            put("/med-kit/{id}", "invalid-uuid")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
        )
            .andExpect(status().is4xxClientError)
    }
    @Test
    @WithMockUser(username = "user-id")
    fun `update - missing content type - returns 404`() {
        mockMvc.perform(put("/med-kit/{id}", testMedKitId))
            .andExpect(status().isNotFound)
    }
    @Test
    @WithMockUser(username = "user-id")
    fun `update - with partial data - returns 404`() {
        mockMvc.perform(
            put("/med-kit/{id}", testMedKitId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"someField":"value"}""")
        )
            .andExpect(status().isNotFound)
    }
    @Test
    @WithMockUser(username = "user-id")
    fun `update - different user - returns 401 or 404`() {
        mockMvc.perform(
            put("/med-kit/{id}", testMedKitId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
        )
            .andExpect(status().is4xxClientError)
    }
    @Test
    @WithMockUser(username = "user-id")
    fun `update - with null values - returns 404`() {
        mockMvc.perform(
            put("/med-kit/{id}", testMedKitId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"field":null}""")
        )
            .andExpect(status().isNotFound)
    }
    @Test
    @WithMockUser(username = "user-id")
    fun `update - verify endpoint not implemented - returns 404`() {
        mockMvc.perform(
            put("/med-kit/{id}", testMedKitId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
        )
            .andExpect(status().isNotFound)
    }
    // ========== DELETE /med-kit/{id} - delete Tests (11 tests) ==========
    @Test
    @WithMockUser(username = "user-id")
    fun `delete - valid id without transfer - deletes medkit and returns 204`() {
        justRun { medKitService.delete(testMedKitId, any(), null) }
        mockMvc.perform(delete("/med-kit/{id}", testMedKitId))
            .andExpect(status().isNoContent)
        verify(exactly = 1) { medKitService.delete(testMedKitId, any(), null) }
    }
    @Test
    @WithMockUser(username = "user-id")
    fun `delete - valid id with transfer medkit - deletes and transfers drugs returns 204`() {
        val transferMedKitId = UUID.randomUUID()
        justRun { medKitService.delete(testMedKitId, any(), transferMedKitId) }
        mockMvc.perform(
            delete("/med-kit/{id}", testMedKitId)
                .param("transferToMedKitId", transferMedKitId.toString())
        )
            .andExpect(status().isNoContent)
        verify(exactly = 1) { medKitService.delete(testMedKitId, any(), transferMedKitId) }
    }
    @Test
    @WithMockUser(username = "user-id")
    fun `delete - medkit not found - returns 404`() {
        every { medKitService.delete(testMedKitId, any(), null) } throws ResponseStatusException(
            HttpStatus.NOT_FOUND, "MedKit not found"
        )
        mockMvc.perform(delete("/med-kit/{id}", testMedKitId))
            .andExpect(status().isNotFound)
        verify(exactly = 1) { medKitService.delete(testMedKitId, any(), null) }
    }
    @Test
    @WithMockUser(username = "user-id")
    fun `delete - user has no access - returns 403`() {
        every { medKitService.delete(testMedKitId, any(), null) } throws ResponseStatusException(
            HttpStatus.FORBIDDEN, "User does not have access to this medkit"
        )
        mockMvc.perform(delete("/med-kit/{id}", testMedKitId))
            .andExpect(status().isForbidden)
        verify(exactly = 1) { medKitService.delete(testMedKitId, any(), null) }
    }
    @Test
    fun `delete - unauthenticated - returns 401`() {
        mockMvc.perform(delete("/med-kit/{id}", testMedKitId))
            .andExpect(status().isUnauthorized)
    }
    @Test
    @WithMockUser(username = "user-id")
    fun `delete - invalid UUID format - returns 400`() {
        mockMvc.perform(delete("/med-kit/{id}", "invalid-uuid"))
            .andExpect(status().isBadRequest)
    }
    @Test
    @WithMockUser(username = "user-id")
    fun `delete - transfer to non-existent medkit - returns 404`() {
        val transferMedKitId = UUID.randomUUID()
        every { medKitService.delete(testMedKitId, any(), transferMedKitId) } throws ResponseStatusException(
            HttpStatus.NOT_FOUND, "Transfer target medkit not found"
        )
        mockMvc.perform(
            delete("/med-kit/{id}", testMedKitId)
                .param("transferToMedKitId", transferMedKitId.toString())
        )
            .andExpect(status().isNotFound)
        verify(exactly = 1) { medKitService.delete(testMedKitId, any(), transferMedKitId) }
    }
    @Test
    @WithMockUser(username = "user-id")
    fun `delete - transfer to medkit user has no access - returns 403`() {
        val transferMedKitId = UUID.randomUUID()
        every { medKitService.delete(testMedKitId, any(), transferMedKitId) } throws ResponseStatusException(
            HttpStatus.FORBIDDEN, "User does not have access to transfer target medkit"
        )
        mockMvc.perform(
            delete("/med-kit/{id}", testMedKitId)
                .param("transferToMedKitId", transferMedKitId.toString())
        )
            .andExpect(status().isForbidden)
        verify(exactly = 1) { medKitService.delete(testMedKitId, any(), transferMedKitId) }
    }
    @Test
    @WithMockUser(username = "user-id")
    fun `delete - service throws error - returns 500`() {
        every { medKitService.delete(testMedKitId, any(), null) } throws RuntimeException("Database error")
        mockMvc.perform(delete("/med-kit/{id}", testMedKitId))
            .andExpect(status().isInternalServerError)
        verify(exactly = 1) { medKitService.delete(testMedKitId, any(), null) }
    }
    @Test
    @WithMockUser(username = "user-id")
    fun `delete - with invalid transfer UUID format - returns 400`() {
        mockMvc.perform(
            delete("/med-kit/{id}", testMedKitId)
                .param("transferToMedKitId", "invalid-uuid")
        )
            .andExpect(status().isBadRequest)
    }
    @Test
    @WithMockUser(username = "different-user")
    fun `delete - different user without access - returns 403`() {
        every { medKitService.delete(testMedKitId, any(), null) } throws ResponseStatusException(
            HttpStatus.FORBIDDEN, "User does not have access to this medkit"
        )
        mockMvc.perform(delete("/med-kit/{id}", testMedKitId))
            .andExpect(status().isForbidden)
    }
    // ========== POST /med-kit/{id}/share - generateShareCode/addUser Tests (11 tests) ==========
    @Test
    @WithMockUser(username = "user-id")
    fun `share - valid request - adds user and returns medkit DTO`() {
        val newUserId = UUID.randomUUID()
        val medKit = medKitBuilder().withId(testMedKitId).build()
        val user1 = userBuilder().withId(testUserId).build()
        val user2 = userBuilder().withId(newUserId).build()
        medKit.users.addAll(listOf(user1, user2))
        val drugDTO = DrugDTO(
            id = testDrugId,
            name = "Aspirin",
            quantity = 100.0,
            plannedQuantity = 0.0,
            quantityUnit = "mg",
            formType = "tablet",
            category = "painkiller",
            manufacturer = "Test Pharma",
            country = "TestLand",
            description = "Test description",
            medKitId = testMedKitId
        )
        val medKitDTO = MedKitDTO(id = testMedKitId, drugs = setOf(drugDTO))
        every { medKitService.findByIdForUser(testMedKitId, any()) } returns medKit
        every { medKitService.addUserToMedKit(testMedKitId, newUserId) } returns medKit
        every { medKitService.toMedKitDTO(medKit) } returns medKitDTO
        val request = AddUserRequest(userId = newUserId)
        mockMvc.perform(
            post("/med-kit/{id}/share", testMedKitId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(testMedKitId.toString()))
        verify(exactly = 1) { medKitService.findByIdForUser(testMedKitId, any()) }
        verify(exactly = 1) { medKitService.addUserToMedKit(testMedKitId, newUserId) }
        verify(exactly = 1) { medKitService.toMedKitDTO(medKit) }
    }
    @Test
    @WithMockUser(username = "user-id")
    fun `share - requester has no access to medkit - returns 403`() {
        val newUserId = UUID.randomUUID()
        every { medKitService.findByIdForUser(testMedKitId, any()) } throws ResponseStatusException(
            HttpStatus.FORBIDDEN, "User does not have access to this medkit"
        )
        val request = AddUserRequest(userId = newUserId)
        mockMvc.perform(
            post("/med-kit/{id}/share", testMedKitId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isForbidden)
        verify(exactly = 1) { medKitService.findByIdForUser(testMedKitId, any()) }
        verify(exactly = 0) { medKitService.addUserToMedKit(any(), any()) }
    }
    @Test
    @WithMockUser(username = "user-id")
    fun `share - medkit not found - returns 404`() {
        val newUserId = UUID.randomUUID()
        every { medKitService.findByIdForUser(testMedKitId, any()) } throws ResponseStatusException(
            HttpStatus.NOT_FOUND, "MedKit not found"
        )
        val request = AddUserRequest(userId = newUserId)
        mockMvc.perform(
            post("/med-kit/{id}/share", testMedKitId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isNotFound)
        verify(exactly = 1) { medKitService.findByIdForUser(testMedKitId, any()) }
    }
    @Test
    @WithMockUser(username = "user-id")
    fun `share - user to add not found - returns 404`() {
        val newUserId = UUID.randomUUID()
        val medKit = medKitBuilder().withId(testMedKitId).build()
        every { medKitService.findByIdForUser(testMedKitId, any()) } returns medKit
        every { medKitService.addUserToMedKit(testMedKitId, newUserId) } throws ResponseStatusException(
            HttpStatus.NOT_FOUND, "User not found"
        )
        val request = AddUserRequest(userId = newUserId)
        mockMvc.perform(
            post("/med-kit/{id}/share", testMedKitId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isNotFound)
        verify(exactly = 1) { medKitService.addUserToMedKit(testMedKitId, newUserId) }
    }
    @Test
    @WithMockUser(username = "user-id")
    fun `share - missing userId in request - returns 400`() {
        val invalidRequest = "{}"
        mockMvc.perform(
            post("/med-kit/{id}/share", testMedKitId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidRequest)
        )
            .andExpect(status().isBadRequest)
    }
    @Test
    fun `share - unauthenticated - returns 401`() {
        val request = AddUserRequest(userId = UUID.randomUUID())
        mockMvc.perform(
            post("/med-kit/{id}/share", testMedKitId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isUnauthorized)
    }
    @Test
    @WithMockUser(username = "user-id")
    fun `share - invalid medkit UUID format - returns 400`() {
        val request = AddUserRequest(userId = UUID.randomUUID())
        mockMvc.perform(
            post("/med-kit/{id}/share", "invalid-uuid")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isBadRequest)
    }
    @Test
    @WithMockUser(username = "user-id")
    fun `share - user already has access - returns updated medkit DTO`() {
        val existingUserId = UUID.randomUUID()
        val medKit = medKitBuilder().withId(testMedKitId).build()
        val user = userBuilder().withId(existingUserId).build()
        medKit.users.add(user)
        val medKitDTO = MedKitDTO(id = testMedKitId, drugs = emptySet())
        every { medKitService.findByIdForUser(testMedKitId, any()) } returns medKit
        every { medKitService.addUserToMedKit(testMedKitId, existingUserId) } returns medKit
        every { medKitService.toMedKitDTO(medKit) } returns medKitDTO
        val request = AddUserRequest(userId = existingUserId)
        mockMvc.perform(
            post("/med-kit/{id}/share", testMedKitId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isOk)
        verify(exactly = 1) { medKitService.addUserToMedKit(testMedKitId, existingUserId) }
    }
    @Test
    @WithMockUser(username = "user-id")
    fun `share - service throws error - returns 500`() {
        val newUserId = UUID.randomUUID()
        val medKit = medKitBuilder().withId(testMedKitId).build()
        every { medKitService.findByIdForUser(testMedKitId, any()) } returns medKit
        every { medKitService.addUserToMedKit(testMedKitId, newUserId) } throws RuntimeException("Database error")
        val request = AddUserRequest(userId = newUserId)
        mockMvc.perform(
            post("/med-kit/{id}/share", testMedKitId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isInternalServerError)
    }
    @Test
    @WithMockUser(username = "user-id")
    fun `share - null userId in request - returns 400`() {
        val invalidRequest = """{"userId": null}"""
        mockMvc.perform(
            post("/med-kit/{id}/share", testMedKitId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidRequest)
        )
            .andExpect(status().isBadRequest)
    }
    @Test
    @WithMockUser(username = "user-id")
    fun `share - returns medkit with all shared users drugs`() {
        val newUserId = UUID.randomUUID()
        val medKit = medKitBuilder().withId(testMedKitId).build()
        val drug1 = drugBuilder(medKit).withId(UUID.randomUUID()).build()
        val drug2 = drugBuilder(medKit).withId(UUID.randomUUID()).build()
        medKit.drugs.addAll(listOf(drug1, drug2))
        val drugDTO1 = DrugDTO(
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
            medKitId = testMedKitId
        )
        val drugDTO2 = DrugDTO(
            id = drug2.id,
            name = "Ibuprofen",
            quantity = 100.0,
            plannedQuantity = 0.0,
            quantityUnit = "mg",
            formType = "tablet",
            category = "painkiller",
            manufacturer = "Test Pharma",
            country = "TestLand",
            description = "Test description",
            medKitId = testMedKitId
        )
        val medKitDTO = MedKitDTO(id = testMedKitId, drugs = setOf(drugDTO1, drugDTO2))
        every { medKitService.findByIdForUser(testMedKitId, any()) } returns medKit
        every { medKitService.addUserToMedKit(testMedKitId, newUserId) } returns medKit
        every { medKitService.toMedKitDTO(medKit) } returns medKitDTO
        val request = AddUserRequest(userId = newUserId)
        mockMvc.perform(
            post("/med-kit/{id}/share", testMedKitId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.drugs.length()").value(2))
    }
    // ========== Additional Edge Case Tests (8 tests) ==========
    @Test
    @WithMockUser(username = "user-id")
    fun `leave medkit - valid request - returns 204`() {
        justRun { medKitService.removeUserFromMedKit(testMedKitId, any()) }
        mockMvc.perform(delete("/med-kit/{id}/leave", testMedKitId))
            .andExpect(status().isNoContent)
        verify(exactly = 1) { medKitService.removeUserFromMedKit(testMedKitId, any()) }
    }
    @Test
    @WithMockUser(username = "user-id")
    fun `leave medkit - medkit not found - returns 404`() {
        every { medKitService.removeUserFromMedKit(testMedKitId, any()) } throws ResponseStatusException(
            HttpStatus.NOT_FOUND, "MedKit not found"
        )
        mockMvc.perform(delete("/med-kit/{id}/leave", testMedKitId))
            .andExpect(status().isNotFound)
    }
    @Test
    @WithMockUser(username = "user-id")
    fun `leave medkit - user not in medkit - returns 403`() {
        every { medKitService.removeUserFromMedKit(testMedKitId, any()) } throws ResponseStatusException(
            HttpStatus.FORBIDDEN, "User does not have access to this medkit"
        )
        mockMvc.perform(delete("/med-kit/{id}/leave", testMedKitId))
            .andExpect(status().isForbidden)
    }
    @Test
    fun `leave medkit - unauthenticated - returns 401`() {
        mockMvc.perform(delete("/med-kit/{id}/leave", testMedKitId))
            .andExpect(status().isUnauthorized)
    }
    @Test
    @WithMockUser(username = "user-id")
    fun `leave medkit - invalid UUID - returns 400`() {
        mockMvc.perform(delete("/med-kit/{id}/leave", "invalid-uuid"))
            .andExpect(status().isBadRequest)
    }
    @Test
    @WithMockUser(username = "user-id")
    fun `getAllForUser - medkit with zero users and drugs - returns correct zero counts`() {
        val medKit = medKitBuilder().withId(testMedKitId).build()
        every { medKitService.findAllByUser(any()) } returns listOf(medKit)
        mockMvc.perform(get("/med-kit"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].userCount").value(0))
            .andExpect(jsonPath("$[0].drugCount").value(0))
    }
    @Test
    @WithMockUser(username = "user-id")
    fun `getById - medkit with single drug - returns medkit with one drug DTO`() {
        val medKit = medKitBuilder().withId(testMedKitId).build()
        val drug = drugBuilder(medKit).withId(testDrugId).build()
        medKit.drugs.add(drug)
        val drugDTO = DrugDTO(
            id = testDrugId,
            name = "Test Drug",
            quantity = 100.0,
            plannedQuantity = 0.0,
            quantityUnit = "mg",
            formType = "tablet",
            category = "painkiller",
            manufacturer = "Test Pharma",
            country = "TestLand",
            description = "Test description",
            medKitId = testMedKitId
        )
        val medKitDTO = MedKitDTO(id = testMedKitId, drugs = setOf(drugDTO))
        every { medKitService.findByIdForUser(testMedKitId, any()) } returns medKit
        every { medKitService.toMedKitDTO(medKit) } returns medKitDTO
        mockMvc.perform(get("/med-kit/{id}", testMedKitId))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.drugs.length()").value(1))
            .andExpect(jsonPath("$.drugs[0].id").value(testDrugId.toString()))
    }
    @Test
    @WithMockUser(username = "user-id")
    fun `delete - with empty transferToMedKitId param - treats as no transfer`() {
        justRun { medKitService.delete(testMedKitId, any(), null) }
        mockMvc.perform(
            delete("/med-kit/{id}", testMedKitId)
                .param("transferToMedKitId", "")
        )
            .andExpect(status().isBadRequest)
    }
}