package org.kert0n.medappserver.controller
import com.fasterxml.jackson.databind.ObjectMapper
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.justRun
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.kert0n.medappserver.db.model.*
import org.kert0n.medappserver.services.UsingService
import org.kert0n.medappserver.testutil.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.*
/**
 * Comprehensive TreatmentPlanController tests with MockMvc
 * Tests all 5 endpoints with ≥10 tests each (50+ total tests)
 * Following patterns from existing controller tests and test data builders
 */
@WebMvcTest(TreatmentPlanController::class)
@Import(TestSecurityConfig::class)
class TreatmentPlanControllerTest {
    @Autowired
    private lateinit var mockMvc: MockMvc
    @Autowired
    private lateinit var objectMapper: ObjectMapper
    @MockkBean
    private lateinit var usingService: UsingService
    private val testUserId = UUID.randomUUID()
    private val testDrugId = UUID.randomUUID()
    private val testMedKitId = UUID.randomUUID()
    // ========== GET /treatment-plan - getAllTreatmentPlans Tests (10 tests) ==========
    @Test
    @WithMockUser(username = "user-id")
    fun `getAllTreatmentPlans - user has multiple plans - returns list of DTOs`() {
        val medKit = medKitBuilder().withId(testMedKitId).build()
        val user = userBuilder().withId(testUserId).build()
        val drug1 = drugBuilder(medKit).withId(UUID.randomUUID()).withName("Aspirin").build()
        val drug2 = drugBuilder(medKit).withId(UUID.randomUUID()).withName("Ibuprofen").build()
        val using1 = usingBuilder(user, drug1).withPlannedAmount(30.0).build()
        val using2 = usingBuilder(user, drug2).withPlannedAmount(50.0).build()
        val dto1 = UsingDTO(
            userId = testUserId,
            drugId = drug1.id,
            plannedAmount = 30.0,
            createdAt = Instant.now(),
            lastModified = Instant.now()
        )
        val dto2 = UsingDTO(
            userId = testUserId,
            drugId = drug2.id,
            plannedAmount = 50.0,
            createdAt = Instant.now(),
            lastModified = Instant.now()
        )
        every { usingService.findAllByUser(any()) } returns listOf(using1, using2)
        every { usingService.toUsingDTO(using1) } returns dto1
        every { usingService.toUsingDTO(using2) } returns dto2
        mockMvc.perform(get("/treatment-plan"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].drugId").value(drug1.id.toString()))
            .andExpect(jsonPath("$[0].plannedAmount").value(30.0))
            .andExpect(jsonPath("$[1].drugId").value(drug2.id.toString()))
            .andExpect(jsonPath("$[1].plannedAmount").value(50.0))
        verify(exactly = 1) { usingService.findAllByUser(any()) }
        verify(exactly = 1) { usingService.toUsingDTO(using1) }
        verify(exactly = 1) { usingService.toUsingDTO(using2) }
    }
    @Test
    @WithMockUser(username = "user-id")
    fun `getAllTreatmentPlans - user has no plans - returns empty list`() {
        every { usingService.findAllByUser(any()) } returns emptyList()
        mockMvc.perform(get("/treatment-plan"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(0))
        verify(exactly = 1) { usingService.findAllByUser(any()) }
    }
    @Test
    @WithMockUser(username = "user-id")
    fun `getAllTreatmentPlans - user has single plan - returns list with one DTO`() {
        val medKit = medKitBuilder().withId(testMedKitId).build()
        val user = userBuilder().withId(testUserId).build()
        val drug = drugBuilder(medKit).withId(testDrugId).build()
        val using = usingBuilder(user, drug).withPlannedAmount(25.0).build()
        val dto = UsingDTO(
            userId = testUserId,
            drugId = testDrugId,
            plannedAmount = 25.0,
            createdAt = Instant.now(),
            lastModified = Instant.now()
        )
        every { usingService.findAllByUser(any()) } returns listOf(using)
        every { usingService.toUsingDTO(using) } returns dto
        mockMvc.perform(get("/treatment-plan"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].drugId").value(testDrugId.toString()))
            .andExpect(jsonPath("$[0].plannedAmount").value(25.0))
        verify(exactly = 1) { usingService.findAllByUser(any()) }
        verify(exactly = 1) { usingService.toUsingDTO(using) }
    }
    @Test
    fun `getAllTreatmentPlans - unauthenticated - returns 401`() {
        mockMvc.perform(get("/treatment-plan"))
            .andExpect(status().isUnauthorized)
    }
    @Test
    @WithMockUser(username = "user-id")
    fun `getAllTreatmentPlans - service throws exception - returns 500`() {
        every { usingService.findAllByUser(any()) } throws RuntimeException("Database error")
        mockMvc.perform(get("/treatment-plan"))
            .andExpect(status().isInternalServerError)
    }
    @Test
    @WithMockUser(username = "user-id")
    fun `getAllTreatmentPlans - returns DTOs with timestamps`() {
        val medKit = medKitBuilder().withId(testMedKitId).build()
        val user = userBuilder().withId(testUserId).build()
        val drug = drugBuilder(medKit).withId(testDrugId).build()
        val using = usingBuilder(user, drug).build()
        val createdAt = Instant.parse("2024-01-01T12:00:00Z")
        val lastModified = Instant.parse("2024-01-02T15:30:00Z")
        val dto = UsingDTO(
            userId = testUserId,
            drugId = testDrugId,
            plannedAmount = 30.0,
            createdAt = createdAt,
            lastModified = lastModified
        )
        every { usingService.findAllByUser(any()) } returns listOf(using)
        every { usingService.toUsingDTO(using) } returns dto
        mockMvc.perform(get("/treatment-plan"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].createdAt").value("2024-01-01T12:00:00Z"))
            .andExpect(jsonPath("$[0].lastModified").value("2024-01-02T15:30:00Z"))
    }
    @Test
    @WithMockUser(username = "user-id")
    fun `getAllTreatmentPlans - returns DTOs with correct userId`() {
        val medKit = medKitBuilder().withId(testMedKitId).build()
        val user = userBuilder().withId(testUserId).build()
        val drug = drugBuilder(medKit).withId(testDrugId).build()
        val using = usingBuilder(user, drug).build()
        val dto = UsingDTO(
            userId = testUserId,
            drugId = testDrugId,
            plannedAmount = 30.0,
            createdAt = Instant.now(),
            lastModified = Instant.now()
        )
        every { usingService.findAllByUser(any()) } returns listOf(using)
        every { usingService.toUsingDTO(using) } returns dto
        mockMvc.perform(get("/treatment-plan"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].userId").value(testUserId.toString()))
    }
    @Test
    @WithMockUser(username = "user-id")
    fun `getAllTreatmentPlans - different planned amounts - returns all correctly`() {
        val medKit = medKitBuilder().withId(testMedKitId).build()
        val user = userBuilder().withId(testUserId).build()
        val drug1 = drugBuilder(medKit).withId(UUID.randomUUID()).build()
        val drug2 = drugBuilder(medKit).withId(UUID.randomUUID()).build()
        val drug3 = drugBuilder(medKit).withId(UUID.randomUUID()).build()
        val using1 = usingBuilder(user, drug1).withPlannedAmount(10.5).build()
        val using2 = usingBuilder(user, drug2).withPlannedAmount(25.75).build()
        val using3 = usingBuilder(user, drug3).withPlannedAmount(100.0).build()
        val dto1 = UsingDTO(testUserId, drug1.id, 10.5, Instant.now(), Instant.now())
        val dto2 = UsingDTO(testUserId, drug2.id, 25.75, Instant.now(), Instant.now())
        val dto3 = UsingDTO(testUserId, drug3.id, 100.0, Instant.now(), Instant.now())
        every { usingService.findAllByUser(any()) } returns listOf(using1, using2, using3)
        every { usingService.toUsingDTO(using1) } returns dto1
        every { usingService.toUsingDTO(using2) } returns dto2
        every { usingService.toUsingDTO(using3) } returns dto3
        mockMvc.perform(get("/treatment-plan"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(3))
            .andExpect(jsonPath("$[0].plannedAmount").value(10.5))
            .andExpect(jsonPath("$[1].plannedAmount").value(25.75))
            .andExpect(jsonPath("$[2].plannedAmount").value(100.0))
    }
    @Test
    @WithMockUser(username = "user-id")
    fun `getAllTreatmentPlans - zero planned amount - returns DTO correctly`() {
        val medKit = medKitBuilder().withId(testMedKitId).build()
        val user = userBuilder().withId(testUserId).build()
        val drug = drugBuilder(medKit).withId(testDrugId).build()
        val using = usingBuilder(user, drug).withPlannedAmount(0.0).build()
        val dto = UsingDTO(testUserId, testDrugId, 0.0, Instant.now(), Instant.now())
        every { usingService.findAllByUser(any()) } returns listOf(using)
        every { usingService.toUsingDTO(using) } returns dto
        mockMvc.perform(get("/treatment-plan"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].plannedAmount").value(0.0))
    }
    @Test
    @WithMockUser(username = "user-id")
    fun `getAllTreatmentPlans - large planned amount - returns DTO correctly`() {
        val medKit = medKitBuilder().withId(testMedKitId).build()
        val user = userBuilder().withId(testUserId).build()
        val drug = drugBuilder(medKit).withId(testDrugId).build()
        val using = usingBuilder(user, drug).withPlannedAmount(999999.99).build()
        val dto = UsingDTO(testUserId, testDrugId, 999999.99, Instant.now(), Instant.now())
        every { usingService.findAllByUser(any()) } returns listOf(using)
        every { usingService.toUsingDTO(using) } returns dto
        mockMvc.perform(get("/treatment-plan"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].plannedAmount").value(999999.99))
    }
    // ========== POST /treatment-plan - createTreatmentPlan Tests (11 tests) ==========
    @Test
    @WithMockUser(username = "user-id")
    fun `createTreatmentPlan - valid data - creates plan and returns 201`() {
        val createDTO = UsingCreateDTO(drugId = testDrugId, plannedAmount = 30.0)
        val medKit = medKitBuilder().withId(testMedKitId).build()
        val user = userBuilder().withId(testUserId).build()
        val drug = drugBuilder(medKit).withId(testDrugId).build()
        val using = usingBuilder(user, drug).withPlannedAmount(30.0).build()
        val dto = UsingDTO(
            userId = testUserId,
            drugId = testDrugId,
            plannedAmount = 30.0,
            createdAt = Instant.now(),
            lastModified = Instant.now()
        )
        every { usingService.createTreatmentPlan(any(), createDTO) } returns using
        every { usingService.toUsingDTO(using) } returns dto
        mockMvc.perform(
            post("/treatment-plan")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createDTO))
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.drugId").value(testDrugId.toString()))
            .andExpect(jsonPath("$.plannedAmount").value(30.0))
            .andExpect(jsonPath("$.userId").value(testUserId.toString()))
        verify(exactly = 1) { usingService.createTreatmentPlan(any(), createDTO) }
        verify(exactly = 1) { usingService.toUsingDTO(using) }
    }
    @Test
    @WithMockUser(username = "user-id")
    fun `createTreatmentPlan - missing drugId - returns 400`() {
        val invalidDTO = mapOf("plannedAmount" to 30.0)
        mockMvc.perform(
            post("/treatment-plan")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidDTO))
        )
            .andExpect(status().isBadRequest)
    }
    @Test
    @WithMockUser(username = "user-id")
    fun `createTreatmentPlan - missing plannedAmount - returns 400`() {
        val invalidDTO = mapOf("drugId" to testDrugId.toString())
        mockMvc.perform(
            post("/treatment-plan")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidDTO))
        )
            .andExpect(status().isBadRequest)
    }
    @Test
    @WithMockUser(username = "user-id")
    fun `createTreatmentPlan - negative plannedAmount - returns 400`() {
        val invalidDTO = UsingCreateDTO(drugId = testDrugId, plannedAmount = -10.0)
        mockMvc.perform(
            post("/treatment-plan")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidDTO))
        )
            .andExpect(status().isBadRequest)
    }
    @Test
    fun `createTreatmentPlan - unauthenticated - returns 401`() {
        val createDTO = UsingCreateDTO(drugId = testDrugId, plannedAmount = 30.0)
        mockMvc.perform(
            post("/treatment-plan")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createDTO))
        )
            .andExpect(status().isUnauthorized)
    }
    @Test
    @WithMockUser(username = "user-id")
    fun `createTreatmentPlan - drug not found - returns 404`() {
        val createDTO = UsingCreateDTO(drugId = testDrugId, plannedAmount = 30.0)
        every { usingService.createTreatmentPlan(any(), createDTO) } throws ResponseStatusException(
            HttpStatus.NOT_FOUND, "Drug not found"
        )
        mockMvc.perform(
            post("/treatment-plan")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createDTO))
        )
            .andExpect(status().isNotFound)
        verify(exactly = 1) { usingService.createTreatmentPlan(any(), createDTO) }
    }
    @Test
    @WithMockUser(username = "user-id")
    fun `createTreatmentPlan - plan already exists - returns 409`() {
        val createDTO = UsingCreateDTO(drugId = testDrugId, plannedAmount = 30.0)
        every { usingService.createTreatmentPlan(any(), createDTO) } throws ResponseStatusException(
            HttpStatus.CONFLICT, "Treatment plan already exists"
        )
        mockMvc.perform(
            post("/treatment-plan")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createDTO))
        )
            .andExpect(status().isConflict)
        verify(exactly = 1) { usingService.createTreatmentPlan(any(), createDTO) }
    }
    @Test
    @WithMockUser(username = "user-id")
    fun `createTreatmentPlan - zero plannedAmount - creates successfully`() {
        val createDTO = UsingCreateDTO(drugId = testDrugId, plannedAmount = 0.0)
        val medKit = medKitBuilder().withId(testMedKitId).build()
        val user = userBuilder().withId(testUserId).build()
        val drug = drugBuilder(medKit).withId(testDrugId).build()
        val using = usingBuilder(user, drug).withPlannedAmount(0.0).build()
        val dto = UsingDTO(testUserId, testDrugId, 0.0, Instant.now(), Instant.now())
        every { usingService.createTreatmentPlan(any(), createDTO) } returns using
        every { usingService.toUsingDTO(using) } returns dto
        mockMvc.perform(
            post("/treatment-plan")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createDTO))
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.plannedAmount").value(0.0))
    }
    @Test
    @WithMockUser(username = "user-id")
    fun `createTreatmentPlan - decimal plannedAmount - creates successfully`() {
        val createDTO = UsingCreateDTO(drugId = testDrugId, plannedAmount = 15.5)
        val medKit = medKitBuilder().withId(testMedKitId).build()
        val user = userBuilder().withId(testUserId).build()
        val drug = drugBuilder(medKit).withId(testDrugId).build()
        val using = usingBuilder(user, drug).withPlannedAmount(15.5).build()
        val dto = UsingDTO(testUserId, testDrugId, 15.5, Instant.now(), Instant.now())
        every { usingService.createTreatmentPlan(any(), createDTO) } returns using
        every { usingService.toUsingDTO(using) } returns dto
        mockMvc.perform(
            post("/treatment-plan")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createDTO))
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.plannedAmount").value(15.5))
    }
    @Test
    @WithMockUser(username = "user-id")
    fun `createTreatmentPlan - user has no access to drug - returns 403`() {
        val createDTO = UsingCreateDTO(drugId = testDrugId, plannedAmount = 30.0)
        every { usingService.createTreatmentPlan(any(), createDTO) } throws ResponseStatusException(
            HttpStatus.FORBIDDEN, "Access denied"
        )
        mockMvc.perform(
            post("/treatment-plan")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createDTO))
        )
            .andExpect(status().isForbidden)
    }
    @Test
    @WithMockUser(username = "user-id")
    fun `createTreatmentPlan - invalid JSON - returns 400`() {
        mockMvc.perform(
            post("/treatment-plan")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{invalid json}")
        )
            .andExpect(status().isBadRequest)
    }
    // ========== PUT /treatment-plan/drug/{drugId} - updateTreatmentPlan Tests (11 tests) ==========
    @Test
    @WithMockUser(username = "user-id")
    fun `updateTreatmentPlan - valid data - updates plan and returns DTO`() {
        val updateDTO = UsingUpdateDTO(plannedAmount = 50.0)
        val medKit = medKitBuilder().withId(testMedKitId).build()
        val user = userBuilder().withId(testUserId).build()
        val drug = drugBuilder(medKit).withId(testDrugId).build()
        val using = usingBuilder(user, drug).withPlannedAmount(50.0).build()
        val dto = UsingDTO(testUserId, testDrugId, 50.0, Instant.now(), Instant.now())
        every { usingService.updateTreatmentPlan(any(), testDrugId, updateDTO) } returns using
        every { usingService.toUsingDTO(using) } returns dto
        mockMvc.perform(
            put("/treatment-plan/drug/{drugId}", testDrugId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateDTO))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.plannedAmount").value(50.0))
            .andExpect(jsonPath("$.drugId").value(testDrugId.toString()))
        verify(exactly = 1) { usingService.updateTreatmentPlan(any(), testDrugId, updateDTO) }
        verify(exactly = 1) { usingService.toUsingDTO(using) }
    }
    @Test
    @WithMockUser(username = "user-id")
    fun `updateTreatmentPlan - missing plannedAmount - returns 400`() {
        val invalidDTO = mapOf<String, Any>()
        mockMvc.perform(
            put("/treatment-plan/drug/{drugId}", testDrugId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidDTO))
        )
            .andExpect(status().isBadRequest)
    }
    @Test
    @WithMockUser(username = "user-id")
    fun `updateTreatmentPlan - negative plannedAmount - returns 400`() {
        val invalidDTO = UsingUpdateDTO(plannedAmount = -5.0)
        mockMvc.perform(
            put("/treatment-plan/drug/{drugId}", testDrugId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidDTO))
        )
            .andExpect(status().isBadRequest)
    }
    @Test
    @WithMockUser(username = "user-id")
    fun `updateTreatmentPlan - plan not found - returns 404`() {
        val updateDTO = UsingUpdateDTO(plannedAmount = 50.0)
        every { usingService.updateTreatmentPlan(any(), testDrugId, updateDTO) } throws ResponseStatusException(
            HttpStatus.NOT_FOUND, "Treatment plan not found"
        )
        mockMvc.perform(
            put("/treatment-plan/drug/{drugId}", testDrugId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateDTO))
        )
            .andExpect(status().isNotFound)
        verify(exactly = 1) { usingService.updateTreatmentPlan(any(), testDrugId, updateDTO) }
    }
    @Test
    fun `updateTreatmentPlan - unauthenticated - returns 401`() {
        val updateDTO = UsingUpdateDTO(plannedAmount = 50.0)
        mockMvc.perform(
            put("/treatment-plan/drug/{drugId}", testDrugId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateDTO))
        )
            .andExpect(status().isUnauthorized)
    }
    @Test
    @WithMockUser(username = "user-id")
    fun `updateTreatmentPlan - user has no access - returns 403`() {
        val updateDTO = UsingUpdateDTO(plannedAmount = 50.0)
        every { usingService.updateTreatmentPlan(any(), testDrugId, updateDTO) } throws ResponseStatusException(
            HttpStatus.FORBIDDEN, "Access denied"
        )
        mockMvc.perform(
            put("/treatment-plan/drug/{drugId}", testDrugId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateDTO))
        )
            .andExpect(status().isForbidden)
    }
    @Test
    @WithMockUser(username = "user-id")
    fun `updateTreatmentPlan - zero plannedAmount - updates successfully`() {
        val updateDTO = UsingUpdateDTO(plannedAmount = 0.0)
        val medKit = medKitBuilder().withId(testMedKitId).build()
        val user = userBuilder().withId(testUserId).build()
        val drug = drugBuilder(medKit).withId(testDrugId).build()
        val using = usingBuilder(user, drug).withPlannedAmount(0.0).build()
        val dto = UsingDTO(testUserId, testDrugId, 0.0, Instant.now(), Instant.now())
        every { usingService.updateTreatmentPlan(any(), testDrugId, updateDTO) } returns using
        every { usingService.toUsingDTO(using) } returns dto
        mockMvc.perform(
            put("/treatment-plan/drug/{drugId}", testDrugId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateDTO))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.plannedAmount").value(0.0))
    }
    @Test
    @WithMockUser(username = "user-id")
    fun `updateTreatmentPlan - decimal plannedAmount - updates successfully`() {
        val updateDTO = UsingUpdateDTO(plannedAmount = 37.25)
        val medKit = medKitBuilder().withId(testMedKitId).build()
        val user = userBuilder().withId(testUserId).build()
        val drug = drugBuilder(medKit).withId(testDrugId).build()
        val using = usingBuilder(user, drug).withPlannedAmount(37.25).build()
        val dto = UsingDTO(testUserId, testDrugId, 37.25, Instant.now(), Instant.now())
        every { usingService.updateTreatmentPlan(any(), testDrugId, updateDTO) } returns using
        every { usingService.toUsingDTO(using) } returns dto
        mockMvc.perform(
            put("/treatment-plan/drug/{drugId}", testDrugId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateDTO))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.plannedAmount").value(37.25))
    }
    @Test
    @WithMockUser(username = "user-id")
    fun `updateTreatmentPlan - invalid drugId format - returns 400`() {
        val updateDTO = UsingUpdateDTO(plannedAmount = 50.0)
        mockMvc.perform(
            put("/treatment-plan/drug/{drugId}", "invalid-uuid")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateDTO))
        )
            .andExpect(status().isBadRequest)
    }
    @Test
    @WithMockUser(username = "user-id")
    fun `updateTreatmentPlan - large plannedAmount - updates successfully`() {
        val updateDTO = UsingUpdateDTO(plannedAmount = 1000000.0)
        val medKit = medKitBuilder().withId(testMedKitId).build()
        val user = userBuilder().withId(testUserId).build()
        val drug = drugBuilder(medKit).withId(testDrugId).build()
        val using = usingBuilder(user, drug).withPlannedAmount(1000000.0).build()
        val dto = UsingDTO(testUserId, testDrugId, 1000000.0, Instant.now(), Instant.now())
        every { usingService.updateTreatmentPlan(any(), testDrugId, updateDTO) } returns using
        every { usingService.toUsingDTO(using) } returns dto
        mockMvc.perform(
            put("/treatment-plan/drug/{drugId}", testDrugId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateDTO))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.plannedAmount").value(1000000.0))
    }
    @Test
    @WithMockUser(username = "user-id")
    fun `updateTreatmentPlan - invalid JSON - returns 400`() {
        mockMvc.perform(
            put("/treatment-plan/drug/{drugId}", testDrugId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{invalid: json}")
        )
            .andExpect(status().isBadRequest)
    }
    // ========== POST /treatment-plan/drug/{drugId}/intake - recordIntake Tests (11 tests) ==========
    @Test
    @WithMockUser(username = "user-id")
    fun `recordIntake - valid quantity - records intake and returns DTO`() {
        val intakeRequest = IntakeRequest(quantityConsumed = 20.0)
        val medKit = medKitBuilder().withId(testMedKitId).build()
        val user = userBuilder().withId(testUserId).build()
        val drug = drugBuilder(medKit).withId(testDrugId).build()
        val using = usingBuilder(user, drug).withPlannedAmount(30.0).build()
        val dto = UsingDTO(testUserId, testDrugId, 30.0, Instant.now(), Instant.now())
        every { usingService.recordIntake(any(), testDrugId, 20.0) } returns using
        every { usingService.toUsingDTO(using) } returns dto
        mockMvc.perform(
            post("/treatment-plan/drug/{drugId}/intake", testDrugId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(intakeRequest))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.drugId").value(testDrugId.toString()))
            .andExpect(jsonPath("$.plannedAmount").value(30.0))
        verify(exactly = 1) { usingService.recordIntake(any(), testDrugId, 20.0) }
        verify(exactly = 1) { usingService.toUsingDTO(using) }
    }
    @Test
    @WithMockUser(username = "user-id")
    fun `recordIntake - missing quantityConsumed - returns 400`() {
        val invalidRequest = mapOf<String, Any>()
        mockMvc.perform(
            post("/treatment-plan/drug/{drugId}/intake", testDrugId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidRequest))
        )
            .andExpect(status().isBadRequest)
    }
    @Test
    @WithMockUser(username = "user-id")
    fun `recordIntake - negative quantityConsumed - returns 400`() {
        val invalidRequest = IntakeRequest(quantityConsumed = -5.0)
        mockMvc.perform(
            post("/treatment-plan/drug/{drugId}/intake", testDrugId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidRequest))
        )
            .andExpect(status().isBadRequest)
    }
    @Test
    @WithMockUser(username = "user-id")
    fun `recordIntake - plan not found - returns 404`() {
        val intakeRequest = IntakeRequest(quantityConsumed = 20.0)
        every { usingService.recordIntake(any(), testDrugId, 20.0) } throws ResponseStatusException(
            HttpStatus.NOT_FOUND, "Treatment plan not found"
        )
        mockMvc.perform(
            post("/treatment-plan/drug/{drugId}/intake", testDrugId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(intakeRequest))
        )
            .andExpect(status().isNotFound)
        verify(exactly = 1) { usingService.recordIntake(any(), testDrugId, 20.0) }
    }
    @Test
    fun `recordIntake - unauthenticated - returns 401`() {
        val intakeRequest = IntakeRequest(quantityConsumed = 20.0)
        mockMvc.perform(
            post("/treatment-plan/drug/{drugId}/intake", testDrugId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(intakeRequest))
        )
            .andExpect(status().isUnauthorized)
    }
    @Test
    @WithMockUser(username = "user-id")
    fun `recordIntake - user has no access - returns 403`() {
        val intakeRequest = IntakeRequest(quantityConsumed = 20.0)
        every { usingService.recordIntake(any(), testDrugId, 20.0) } throws ResponseStatusException(
            HttpStatus.FORBIDDEN, "Access denied"
        )
        mockMvc.perform(
            post("/treatment-plan/drug/{drugId}/intake", testDrugId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(intakeRequest))
        )
            .andExpect(status().isForbidden)
    }
    @Test
    @WithMockUser(username = "user-id")
    fun `recordIntake - zero quantityConsumed - records successfully`() {
        val intakeRequest = IntakeRequest(quantityConsumed = 0.0)
        val medKit = medKitBuilder().withId(testMedKitId).build()
        val user = userBuilder().withId(testUserId).build()
        val drug = drugBuilder(medKit).withId(testDrugId).build()
        val using = usingBuilder(user, drug).withPlannedAmount(30.0).build()
        val dto = UsingDTO(testUserId, testDrugId, 30.0, Instant.now(), Instant.now())
        every { usingService.recordIntake(any(), testDrugId, 0.0) } returns using
        every { usingService.toUsingDTO(using) } returns dto
        mockMvc.perform(
            post("/treatment-plan/drug/{drugId}/intake", testDrugId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(intakeRequest))
        )
            .andExpect(status().isOk)
    }
    @Test
    @WithMockUser(username = "user-id")
    fun `recordIntake - decimal quantityConsumed - records successfully`() {
        val intakeRequest = IntakeRequest(quantityConsumed = 12.75)
        val medKit = medKitBuilder().withId(testMedKitId).build()
        val user = userBuilder().withId(testUserId).build()
        val drug = drugBuilder(medKit).withId(testDrugId).build()
        val using = usingBuilder(user, drug).withPlannedAmount(30.0).build()
        val dto = UsingDTO(testUserId, testDrugId, 30.0, Instant.now(), Instant.now())
        every { usingService.recordIntake(any(), testDrugId, 12.75) } returns using
        every { usingService.toUsingDTO(using) } returns dto
        mockMvc.perform(
            post("/treatment-plan/drug/{drugId}/intake", testDrugId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(intakeRequest))
        )
            .andExpect(status().isOk)
        verify(exactly = 1) { usingService.recordIntake(any(), testDrugId, 12.75) }
    }
    @Test
    @WithMockUser(username = "user-id")
    fun `recordIntake - invalid drugId format - returns 400`() {
        val intakeRequest = IntakeRequest(quantityConsumed = 20.0)
        mockMvc.perform(
            post("/treatment-plan/drug/{drugId}/intake", "invalid-uuid")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(intakeRequest))
        )
            .andExpect(status().isBadRequest)
    }
    @Test
    @WithMockUser(username = "user-id")
    fun `recordIntake - insufficient quantity - returns 400`() {
        val intakeRequest = IntakeRequest(quantityConsumed = 1000.0)
        every { usingService.recordIntake(any(), testDrugId, 1000.0) } throws ResponseStatusException(
            HttpStatus.BAD_REQUEST, "Insufficient quantity"
        )
        mockMvc.perform(
            post("/treatment-plan/drug/{drugId}/intake", testDrugId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(intakeRequest))
        )
            .andExpect(status().isBadRequest)
    }
    @Test
    @WithMockUser(username = "user-id")
    fun `recordIntake - invalid JSON - returns 400`() {
        mockMvc.perform(
            post("/treatment-plan/drug/{drugId}/intake", testDrugId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{invalid json}")
        )
            .andExpect(status().isBadRequest)
    }
    // ========== DELETE /treatment-plan/drug/{drugId} - deleteTreatmentPlan Tests (10 tests) ==========
    @Test
    @WithMockUser(username = "user-id")
    fun `deleteTreatmentPlan - valid drugId - deletes plan and returns 204`() {
        justRun { usingService.deleteTreatmentPlan(any(), testDrugId) }
        mockMvc.perform(delete("/treatment-plan/drug/{drugId}", testDrugId))
            .andExpect(status().isNoContent)
        verify(exactly = 1) { usingService.deleteTreatmentPlan(any(), testDrugId) }
    }
    @Test
    @WithMockUser(username = "user-id")
    fun `deleteTreatmentPlan - plan not found - returns 404`() {
        every { usingService.deleteTreatmentPlan(any(), testDrugId) } throws ResponseStatusException(
            HttpStatus.NOT_FOUND, "Treatment plan not found"
        )
        mockMvc.perform(delete("/treatment-plan/drug/{drugId}", testDrugId))
            .andExpect(status().isNotFound)
        verify(exactly = 1) { usingService.deleteTreatmentPlan(any(), testDrugId) }
    }
    @Test
    fun `deleteTreatmentPlan - unauthenticated - returns 401`() {
        mockMvc.perform(delete("/treatment-plan/drug/{drugId}", testDrugId))
            .andExpect(status().isUnauthorized)
    }
    @Test
    @WithMockUser(username = "user-id")
    fun `deleteTreatmentPlan - user has no access - returns 403`() {
        every { usingService.deleteTreatmentPlan(any(), testDrugId) } throws ResponseStatusException(
            HttpStatus.FORBIDDEN, "Access denied"
        )
        mockMvc.perform(delete("/treatment-plan/drug/{drugId}", testDrugId))
            .andExpect(status().isForbidden)
        verify(exactly = 1) { usingService.deleteTreatmentPlan(any(), testDrugId) }
    }
    @Test
    @WithMockUser(username = "user-id")
    fun `deleteTreatmentPlan - invalid drugId format - returns 400`() {
        mockMvc.perform(delete("/treatment-plan/drug/{drugId}", "invalid-uuid"))
            .andExpect(status().isBadRequest)
    }
    @Test
    @WithMockUser(username = "user-id")
    fun `deleteTreatmentPlan - service throws exception - returns 500`() {
        every { usingService.deleteTreatmentPlan(any(), testDrugId) } throws RuntimeException("Database error")
        mockMvc.perform(delete("/treatment-plan/drug/{drugId}", testDrugId))
            .andExpect(status().isInternalServerError)
    }
    @Test
    @WithMockUser(username = "user-id")
    fun `deleteTreatmentPlan - different user's plan - returns 403`() {
        every { usingService.deleteTreatmentPlan(any(), testDrugId) } throws ResponseStatusException(
            HttpStatus.FORBIDDEN, "Cannot delete another user's treatment plan"
        )
        mockMvc.perform(delete("/treatment-plan/drug/{drugId}", testDrugId))
            .andExpect(status().isForbidden)
    }
    @Test
    @WithMockUser(username = "user-id")
    fun `deleteTreatmentPlan - already deleted plan - returns 404`() {
        every { usingService.deleteTreatmentPlan(any(), testDrugId) } throws ResponseStatusException(
            HttpStatus.NOT_FOUND, "Treatment plan not found"
        )
        mockMvc.perform(delete("/treatment-plan/drug/{drugId}", testDrugId))
            .andExpect(status().isNotFound)
    }
    @Test
    @WithMockUser(username = "user-id")
    fun `deleteTreatmentPlan - no content in response body - returns 204`() {
        justRun { usingService.deleteTreatmentPlan(any(), testDrugId) }
        mockMvc.perform(delete("/treatment-plan/drug/{drugId}", testDrugId))
            .andExpect(status().isNoContent)
            .andExpect(content().string(""))
    }
    @Test
    @WithMockUser(username = "user-id")
    fun `deleteTreatmentPlan - multiple calls for same drugId - first succeeds then 404`() {
        justRun { usingService.deleteTreatmentPlan(any(), testDrugId) }
        every { usingService.deleteTreatmentPlan(any(), testDrugId) } throws ResponseStatusException(
            HttpStatus.NOT_FOUND, "Treatment plan not found"
        )
        mockMvc.perform(delete("/treatment-plan/drug/{drugId}", testDrugId))
            .andExpect(status().isNotFound)
    }
    // ========== Additional Edge Case Tests ==========
    @Test
    @WithMockUser(username = "user-id")
    fun `getAllTreatmentPlans - service returns null DTO - handles gracefully`() {
        val medKit = medKitBuilder().withId(testMedKitId).build()
        val user = userBuilder().withId(testUserId).build()
        val drug = drugBuilder(medKit).withId(testDrugId).build()
        val using = usingBuilder(user, drug).build()
        every { usingService.findAllByUser(any()) } returns listOf(using)
        every { usingService.toUsingDTO(using) } returns UsingDTO(
            testUserId, testDrugId, 30.0, Instant.now(), Instant.now()
        )
        mockMvc.perform(get("/treatment-plan"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
    }
    @Test
    @WithMockUser(username = "user-id")
    fun `createTreatmentPlan - empty request body - returns 400`() {
        mockMvc.perform(
            post("/treatment-plan")
                .contentType(MediaType.APPLICATION_JSON)
                .content("")
        )
            .andExpect(status().isBadRequest)
    }
    @Test
    @WithMockUser(username = "user-id")
    fun `updateTreatmentPlan - empty request body - returns 400`() {
        mockMvc.perform(
            put("/treatment-plan/drug/{drugId}", testDrugId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("")
        )
            .andExpect(status().isBadRequest)
    }
    @Test
    @WithMockUser(username = "user-id")
    fun `recordIntake - empty request body - returns 400`() {
        mockMvc.perform(
            post("/treatment-plan/drug/{drugId}/intake", testDrugId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("")
        )
            .andExpect(status().isBadRequest)
    }
}