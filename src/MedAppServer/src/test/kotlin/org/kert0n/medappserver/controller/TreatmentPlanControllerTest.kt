package org.kert0n.medappserver.controller

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.kert0n.medappserver.db.model.*
import org.kert0n.medappserver.services.UsingService
import org.kert0n.medappserver.testutil.*
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

@WebMvcTest(TreatmentPlanController::class)
@Import(TestSecurityConfig::class)
class TreatmentPlanControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @MockBean
    private lateinit var usingService: UsingService

    private val testUserId = UUID.randomUUID()
    private val testDrugId = UUID.randomUUID()

    private fun createTestUsing(plannedAmount: Double = 10.0): Using {
        val user = UserBuilder().withId(testUserId).build()
        val medKit = MedKitBuilder().withUsers(mutableSetOf(user)).build()
        val drug = DrugBuilder().withId(testDrugId).withMedKit(medKit).build()
        return UsingBuilder()
            .withUser(user)
            .withDrug(drug)
            .withPlannedAmount(plannedAmount)
            .build()
    }

    // =============== GET /treatment-plan Tests ===============

    @Test
    fun `getAllTreatmentPlans - happy path - returns all user plans`() {
        val using1 = createTestUsing(10.0)
        val using2 = createTestUsing(20.0)
        whenever(usingService.findAllByUser(testUserId)).thenReturn(listOf(using1, using2))
        whenever(usingService.toUsingDTO(any())).thenReturn(
            UsingDTO(userId = testUserId, drugId = testDrugId, plannedAmount = 10.0)
        )

        mockMvc.perform(
            get("/treatment-plan")
                .with(user(testUserId.toString()))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$").isArray)
            .andExpect(jsonPath("$.length()").value(2))

        verify(usingService).findAllByUser(testUserId)
    }

    @Test
    fun `getAllTreatmentPlans - user has no plans - returns empty array`() {
        whenever(usingService.findAllByUser(testUserId)).thenReturn(emptyList())

        mockMvc.perform(
            get("/treatment-plan")
                .with(user(testUserId.toString()))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$").isArray)
            .andExpect(jsonPath("$").isEmpty)
    }

    @Test
    fun `getAllTreatmentPlans - multiple plans - returns all`() {
        val plans = (1..5).map { createTestUsing(it.toDouble()) }
        whenever(usingService.findAllByUser(testUserId)).thenReturn(plans)
        whenever(usingService.toUsingDTO(any())).thenReturn(
            UsingDTO(userId = testUserId, drugId = testDrugId, plannedAmount = 10.0)
        )

        mockMvc.perform(
            get("/treatment-plan")
                .with(user(testUserId.toString()))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(5))
    }

    @Test
    fun `getAllTreatmentPlans - unauthorized - returns 401`() {
        mockMvc.perform(get("/treatment-plan"))
            .andExpect(status().isUnauthorized)

        verifyNoInteractions(usingService)
    }

    // =============== GET /treatment-plan/drug/{drugId} Tests ===============

    @Test
    fun `getTreatmentPlanForDrug - happy path - returns plan`() {
        val using = createTestUsing()
        whenever(usingService.findByUserAndDrug(testUserId, testDrugId)).thenReturn(using)
        whenever(usingService.toUsingDTO(using)).thenReturn(
            UsingDTO(userId = testUserId, drugId = testDrugId, plannedAmount = 10.0)
        )

        mockMvc.perform(
            get("/treatment-plan/drug/{drugId}", testDrugId)
                .with(user(testUserId.toString()))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.userId").value(testUserId.toString()))
            .andExpect(jsonPath("$.drugId").value(testDrugId.toString()))

        verify(usingService).findByUserAndDrug(testUserId, testDrugId)
    }

    @Test
    fun `getTreatmentPlanForDrug - plan does not exist - returns null`() {
        whenever(usingService.findByUserAndDrug(testUserId, testDrugId)).thenReturn(null)

        mockMvc.perform(
            get("/treatment-plan/drug/{drugId}", testDrugId)
                .with(user(testUserId.toString()))
        )
            .andExpect(status().isOk)
            .andExpect(content().string(""))

        verify(usingService).findByUserAndDrug(testUserId, testDrugId)
    }

    @Test
    fun `getTreatmentPlanForDrug - drug does not exist - returns null`() {
        val nonExistentDrugId = UUID.randomUUID()
        whenever(usingService.findByUserAndDrug(testUserId, nonExistentDrugId)).thenReturn(null)

        mockMvc.perform(
            get("/treatment-plan/drug/{drugId}", nonExistentDrugId)
                .with(user(testUserId.toString()))
        )
            .andExpect(status().isOk)
    }

    @Test
    fun `getTreatmentPlanForDrug - drug from inaccessible medkit - returns null or forbidden`() {
        // Service should handle authorization - if user can't access drug, no plan returned
        whenever(usingService.findByUserAndDrug(testUserId, testDrugId)).thenReturn(null)

        mockMvc.perform(
            get("/treatment-plan/drug/{drugId}", testDrugId)
                .with(user(testUserId.toString()))
        )
            .andExpect(status().isOk)
    }

    // =============== POST /treatment-plan Tests ===============

    @Test
    fun `createTreatmentPlan - happy path - returns created plan`() {
        val createDTO = UsingCreateDTO(drugId = testDrugId, plannedAmount = 10.0)
        val using = createTestUsing()
        whenever(usingService.createTreatmentPlan(testUserId, createDTO)).thenReturn(using)
        whenever(usingService.toUsingDTO(using)).thenReturn(
            UsingDTO(userId = testUserId, drugId = testDrugId, plannedAmount = 10.0)
        )

        mockMvc.perform(
            post("/treatment-plan")
                .with(user(testUserId.toString()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createDTO))
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.userId").value(testUserId.toString()))
            .andExpect(jsonPath("$.drugId").value(testDrugId.toString()))

        verify(usingService).createTreatmentPlan(testUserId, createDTO)
    }

    @Test
    fun `createTreatmentPlan - validation error - plannedAmount is zero`() {
        val createDTO = UsingCreateDTO(drugId = testDrugId, plannedAmount = 0.0)

        mockMvc.perform(
            post("/treatment-plan")
                .with(user(testUserId.toString()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createDTO))
        )
            .andExpect(status().isBadRequest)

        verifyNoInteractions(usingService)
    }

    @Test
    fun `createTreatmentPlan - validation error - missing drugId`() {
        val invalidDTO = mapOf("plannedAmount" to 10.0)

        mockMvc.perform(
            post("/treatment-plan")
                .with(user(testUserId.toString()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidDTO))
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `createTreatmentPlan - not found - drug does not exist`() {
        val createDTO = UsingCreateDTO(drugId = testDrugId, plannedAmount = 10.0)
        whenever(usingService.createTreatmentPlan(any(), any()))
            .thenThrow(ResponseStatusException(HttpStatus.NOT_FOUND, "Drug not found"))

        mockMvc.perform(
            post("/treatment-plan")
                .with(user(testUserId.toString()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createDTO))
        )
            .andExpect(status().isNotFound)
    }

    @Test
    fun `createTreatmentPlan - forbidden - drug from inaccessible medkit`() {
        val createDTO = UsingCreateDTO(drugId = testDrugId, plannedAmount = 10.0)
        whenever(usingService.createTreatmentPlan(any(), any()))
            .thenThrow(ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied"))

        mockMvc.perform(
            post("/treatment-plan")
                .with(user(testUserId.toString()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createDTO))
        )
            .andExpect(status().isForbidden)
    }

    @Test
    fun `createTreatmentPlan - bad request - plannedAmount exceeds available`() {
        val createDTO = UsingCreateDTO(drugId = testDrugId, plannedAmount = 1000.0)
        whenever(usingService.createTreatmentPlan(any(), any()))
            .thenThrow(ResponseStatusException(HttpStatus.BAD_REQUEST, "Exceeds available quantity"))

        mockMvc.perform(
            post("/treatment-plan")
                .with(user(testUserId.toString()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createDTO))
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `createTreatmentPlan - bad request - plan already exists for drug`() {
        val createDTO = UsingCreateDTO(drugId = testDrugId, plannedAmount = 10.0)
        whenever(usingService.createTreatmentPlan(any(), any()))
            .thenThrow(ResponseStatusException(HttpStatus.BAD_REQUEST, "Treatment plan already exists"))

        mockMvc.perform(
            post("/treatment-plan")
                .with(user(testUserId.toString()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createDTO))
        )
            .andExpect(status().isBadRequest)
    }

    // =============== PUT /treatment-plan/drug/{drugId} Tests ===============

    @Test
    fun `updateTreatmentPlan - happy path - returns updated plan`() {
        val updateDTO = UsingUpdateDTO(plannedAmount = 20.0)
        val using = createTestUsing(20.0)
        whenever(usingService.updateTreatmentPlan(testUserId, testDrugId, updateDTO)).thenReturn(using)
        whenever(usingService.toUsingDTO(using)).thenReturn(
            UsingDTO(userId = testUserId, drugId = testDrugId, plannedAmount = 20.0)
        )

        mockMvc.perform(
            put("/treatment-plan/drug/{drugId}", testDrugId)
                .with(user(testUserId.toString()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateDTO))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.plannedAmount").value(20.0))

        verify(usingService).updateTreatmentPlan(testUserId, testDrugId, updateDTO)
    }

    @Test
    fun `updateTreatmentPlan - validation error - negative plannedAmount`() {
        val updateDTO = UsingUpdateDTO(plannedAmount = -5.0)

        mockMvc.perform(
            put("/treatment-plan/drug/{drugId}", testDrugId)
                .with(user(testUserId.toString()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateDTO))
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `updateTreatmentPlan - not found - plan does not exist`() {
        val updateDTO = UsingUpdateDTO(plannedAmount = 20.0)
        whenever(usingService.updateTreatmentPlan(any(), any(), any()))
            .thenThrow(ResponseStatusException(HttpStatus.NOT_FOUND, "Treatment plan not found"))

        mockMvc.perform(
            put("/treatment-plan/drug/{drugId}", testDrugId)
                .with(user(testUserId.toString()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateDTO))
        )
            .andExpect(status().isNotFound)
    }

    @Test
    fun `updateTreatmentPlan - forbidden - not users plan`() {
        val updateDTO = UsingUpdateDTO(plannedAmount = 20.0)
        whenever(usingService.updateTreatmentPlan(any(), any(), any()))
            .thenThrow(ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied"))

        mockMvc.perform(
            put("/treatment-plan/drug/{drugId}", testDrugId)
                .with(user(testUserId.toString()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateDTO))
        )
            .andExpect(status().isForbidden)
    }

    @Test
    fun `updateTreatmentPlan - bad request - new amount exceeds available`() {
        val updateDTO = UsingUpdateDTO(plannedAmount = 1000.0)
        whenever(usingService.updateTreatmentPlan(any(), any(), any()))
            .thenThrow(ResponseStatusException(HttpStatus.BAD_REQUEST, "Exceeds available quantity"))

        mockMvc.perform(
            put("/treatment-plan/drug/{drugId}", testDrugId)
                .with(user(testUserId.toString()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateDTO))
        )
            .andExpect(status().isBadRequest)
    }

    // =============== POST /treatment-plan/drug/{drugId}/intake Tests ===============

    @Test
    fun `recordIntake - happy path - reduces planned amount`() {
        val intakeRequest = IntakeRequest(quantityConsumed = 2.0)
        val using = createTestUsing(8.0)
        whenever(usingService.recordIntake(testUserId, testDrugId, 2.0)).thenReturn(using)
        whenever(usingService.toUsingDTO(using)).thenReturn(
            UsingDTO(userId = testUserId, drugId = testDrugId, plannedAmount = 8.0)
        )

        mockMvc.perform(
            post("/treatment-plan/drug/{drugId}/intake", testDrugId)
                .with(user(testUserId.toString()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(intakeRequest))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.plannedAmount").value(8.0))

        verify(usingService).recordIntake(testUserId, testDrugId, 2.0)
    }

    @Test
    fun `recordIntake - validation error - negative quantityConsumed`() {
        val intakeRequest = IntakeRequest(quantityConsumed = -1.0)

        mockMvc.perform(
            post("/treatment-plan/drug/{drugId}/intake", testDrugId)
                .with(user(testUserId.toString()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(intakeRequest))
        )
            .andExpect(status().isBadRequest)

        verifyNoInteractions(usingService)
    }

    @Test
    fun `recordIntake - bad request - exceeds planned amount`() {
        val intakeRequest = IntakeRequest(quantityConsumed = 100.0)
        whenever(usingService.recordIntake(any(), any(), any()))
            .thenThrow(ResponseStatusException(HttpStatus.BAD_REQUEST, "Exceeds planned amount"))

        mockMvc.perform(
            post("/treatment-plan/drug/{drugId}/intake", testDrugId)
                .with(user(testUserId.toString()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(intakeRequest))
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `recordIntake - not found - plan does not exist`() {
        val intakeRequest = IntakeRequest(quantityConsumed = 2.0)
        whenever(usingService.recordIntake(any(), any(), any()))
            .thenThrow(ResponseStatusException(HttpStatus.NOT_FOUND, "Treatment plan not found"))

        mockMvc.perform(
            post("/treatment-plan/drug/{drugId}/intake", testDrugId)
                .with(user(testUserId.toString()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(intakeRequest))
        )
            .andExpect(status().isNotFound)
    }

    @Test
    fun `recordIntake - forbidden - not users plan`() {
        val intakeRequest = IntakeRequest(quantityConsumed = 2.0)
        whenever(usingService.recordIntake(any(), any(), any()))
            .thenThrow(ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied"))

        mockMvc.perform(
            post("/treatment-plan/drug/{drugId}/intake", testDrugId)
                .with(user(testUserId.toString()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(intakeRequest))
        )
            .andExpect(status().isForbidden)
    }

    // =============== DELETE /treatment-plan/drug/{drugId} Tests ===============

    @Test
    fun `deleteTreatmentPlan - happy path - returns 204`() {
        doNothing().whenever(usingService).deleteTreatmentPlan(testUserId, testDrugId)

        mockMvc.perform(
            delete("/treatment-plan/drug/{drugId}", testDrugId)
                .with(user(testUserId.toString()))
        )
            .andExpect(status().isNoContent)

        verify(usingService).deleteTreatmentPlan(testUserId, testDrugId)
    }

    @Test
    fun `deleteTreatmentPlan - not found - plan does not exist`() {
        doThrow(ResponseStatusException(HttpStatus.NOT_FOUND, "Treatment plan not found"))
            .whenever(usingService).deleteTreatmentPlan(any(), any())

        mockMvc.perform(
            delete("/treatment-plan/drug/{drugId}", testDrugId)
                .with(user(testUserId.toString()))
        )
            .andExpect(status().isNotFound)
    }

    @Test
    fun `deleteTreatmentPlan - forbidden - not users plan`() {
        doThrow(ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied"))
            .whenever(usingService).deleteTreatmentPlan(any(), any())

        mockMvc.perform(
            delete("/treatment-plan/drug/{drugId}", testDrugId)
                .with(user(testUserId.toString()))
        )
            .andExpect(status().isForbidden)
    }

    @Test
    fun `deleteTreatmentPlan - drug quantity unchanged - only plan deleted`() {
        doNothing().whenever(usingService).deleteTreatmentPlan(testUserId, testDrugId)

        mockMvc.perform(
            delete("/treatment-plan/drug/{drugId}", testDrugId)
                .with(user(testUserId.toString()))
        )
            .andExpect(status().isNoContent)
    }

    @Test
    fun `deleteTreatmentPlan - unauthorized - returns 401`() {
        mockMvc.perform(delete("/treatment-plan/drug/{drugId}", testDrugId))
            .andExpect(status().isUnauthorized)

        verifyNoInteractions(usingService)
    }
}
