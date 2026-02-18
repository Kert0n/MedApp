package org.kert0n.medappserver.controller

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.kert0n.medappserver.db.model.*
import org.kert0n.medappserver.services.*
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

@WebMvcTest(DrugController::class)
@Import(TestSecurityConfig::class)
class DrugControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @MockBean
    private lateinit var drugService: DrugService

    @MockBean
    private lateinit var usingService: UsingService

    @MockBean
    private lateinit var vidalDrugService: VidalDrugService

    private val testUserId = UUID.randomUUID()
    private val testDrugId = UUID.randomUUID()
    private val testMedKitId = UUID.randomUUID()

    // Helper to create test drug
    private fun createTestDrug(id: UUID = testDrugId, quantity: Double = 10.0): Drug {
        val user = UserBuilder().withId(testUserId).build()
        val medKit = MedKitBuilder().withId(testMedKitId).withUsers(mutableSetOf(user)).build()
        return Drug(
            id = id,
            name = "Test Drug",
            quantity = quantity,
            medKit = medKit
        )
    }

    // =============== GET /drug/{id} Tests ===============

    @Test
    fun `getDrug - happy path - returns drug DTO`() {
        val drug = createTestDrug()
        whenever(drugService.findByIdForUser(testDrugId, testUserId)).thenReturn(drug)
        whenever(drugService.toDrugDTO(drug)).thenReturn(
            DrugDTO(
                id = drug.id,
                name = drug.name,
                quantity = drug.quantity,
                medKitId = drug.medKit.id
            )
        )

        mockMvc.perform(
            get("/drug/{id}", testDrugId)
                .with(user(testUserId.toString()))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(testDrugId.toString()))
            .andExpect(jsonPath("$.name").value("Test Drug"))
            .andExpect(jsonPath("$.quantity").value(10.0))

        verify(drugService).findByIdForUser(testDrugId, testUserId)
        verify(drugService).toDrugDTO(drug)
    }

    @Test
    fun `getDrug - not found - returns 404`() {
        whenever(drugService.findByIdForUser(any(), any()))
            .thenThrow(ResponseStatusException(HttpStatus.NOT_FOUND, "Drug not found"))

        mockMvc.perform(
            get("/drug/{id}", testDrugId)
                .with(user(testUserId.toString()))
        )
            .andExpect(status().isNotFound)

        verify(drugService).findByIdForUser(testDrugId, testUserId)
    }

    @Test
    fun `getDrug - forbidden - user has no access to drug`() {
        whenever(drugService.findByIdForUser(any(), any()))
            .thenThrow(ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied"))

        mockMvc.perform(
            get("/drug/{id}", testDrugId)
                .with(user(testUserId.toString()))
        )
            .andExpect(status().isForbidden)
    }

    @Test
    fun `getDrug - unauthorized - no authentication`() {
        mockMvc.perform(get("/drug/{id}", testDrugId))
            .andExpect(status().isUnauthorized)

        verifyNoInteractions(drugService)
    }

    @Test
    fun `getDrug - zero quantity - returns drug with zero`() {
        val drug = createTestDrug(quantity = 0.0)
        whenever(drugService.findByIdForUser(testDrugId, testUserId)).thenReturn(drug)
        whenever(drugService.toDrugDTO(drug)).thenReturn(
            DrugDTO(id = drug.id, name = drug.name, quantity = 0.0, medKitId = drug.medKit.id)
        )

        mockMvc.perform(
            get("/drug/{id}", testDrugId)
                .with(user(testUserId.toString()))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.quantity").value(0.0))
    }

    // =============== POST /drug Tests ===============

    @Test
    fun `createDrug - happy path - returns 201 with created drug`() {
        val createDTO = DrugCreateDTO(
            name = "New Drug",
            quantity = 5.0,
            medKitId = testMedKitId
        )
        val createdDrug = createTestDrug()
        whenever(drugService.create(any(), eq(testUserId))).thenReturn(createdDrug)
        whenever(drugService.toDrugDTO(createdDrug)).thenReturn(
            DrugDTO(id = createdDrug.id, name = createdDrug.name, quantity = createdDrug.quantity, medKitId = testMedKitId)
        )

        mockMvc.perform(
            post("/drug")
                .with(user(testUserId.toString()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createDTO))
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.id").exists())
            .andExpect(jsonPath("$.name").value("Test Drug"))

        verify(drugService).create(any(), eq(testUserId))
    }

    @Test
    fun `createDrug - validation error - missing required field`() {
        val invalidDTO = mapOf("quantity" to 5.0) // missing name

        mockMvc.perform(
            post("/drug")
                .with(user(testUserId.toString()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidDTO))
        )
            .andExpect(status().isBadRequest)

        verifyNoInteractions(drugService)
    }

    @Test
    fun `createDrug - validation error - negative quantity`() {
        val createDTO = DrugCreateDTO(
            name = "Drug",
            quantity = -1.0,
            medKitId = testMedKitId
        )

        mockMvc.perform(
            post("/drug")
                .with(user(testUserId.toString()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createDTO))
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `createDrug - forbidden - medkit not accessible`() {
        val createDTO = DrugCreateDTO(name = "Drug", quantity = 5.0, medKitId = testMedKitId)
        whenever(drugService.create(any(), any()))
            .thenThrow(ResponseStatusException(HttpStatus.FORBIDDEN, "No access to medkit"))

        mockMvc.perform(
            post("/drug")
                .with(user(testUserId.toString()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createDTO))
        )
            .andExpect(status().isForbidden)
    }

    @Test
    fun `createDrug - not found - medkit does not exist`() {
        val createDTO = DrugCreateDTO(name = "Drug", quantity = 5.0, medKitId = UUID.randomUUID())
        whenever(drugService.create(any(), any()))
            .thenThrow(ResponseStatusException(HttpStatus.NOT_FOUND, "MedKit not found"))

        mockMvc.perform(
            post("/drug")
                .with(user(testUserId.toString()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createDTO))
        )
            .andExpect(status().isNotFound)
    }

    // =============== PUT /drug/{id} Tests ===============

    @Test
    fun `updateDrug - happy path - returns updated drug`() {
        val updateDTO = DrugUpdateDTO(quantity = 15.0)
        val updatedDrug = createTestDrug(quantity = 15.0)
        whenever(drugService.update(eq(testDrugId), any(), eq(testUserId))).thenReturn(updatedDrug)
        whenever(drugService.toDrugDTO(updatedDrug)).thenReturn(
            DrugDTO(id = updatedDrug.id, name = updatedDrug.name, quantity = 15.0, medKitId = testMedKitId)
        )

        mockMvc.perform(
            put("/drug/{id}", testDrugId)
                .with(user(testUserId.toString()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateDTO))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.quantity").value(15.0))

        verify(drugService).update(eq(testDrugId), any(), eq(testUserId))
    }

    @Test
    fun `updateDrug - not found - drug does not exist`() {
        val updateDTO = DrugUpdateDTO(quantity = 15.0)
        whenever(drugService.update(any(), any(), any()))
            .thenThrow(ResponseStatusException(HttpStatus.NOT_FOUND, "Drug not found"))

        mockMvc.perform(
            put("/drug/{id}", testDrugId)
                .with(user(testUserId.toString()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateDTO))
        )
            .andExpect(status().isNotFound)
    }

    @Test
    fun `updateDrug - forbidden - not users drug`() {
        val updateDTO = DrugUpdateDTO(quantity = 15.0)
        whenever(drugService.update(any(), any(), any()))
            .thenThrow(ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied"))

        mockMvc.perform(
            put("/drug/{id}", testDrugId)
                .with(user(testUserId.toString()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateDTO))
        )
            .andExpect(status().isForbidden)
    }

    @Test
    fun `updateDrug - validation error - negative quantity`() {
        val updateDTO = DrugUpdateDTO(quantity = -5.0)

        mockMvc.perform(
            put("/drug/{id}", testDrugId)
                .with(user(testUserId.toString()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateDTO))
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `updateDrug - partial update - only quantity changed`() {
        val updateDTO = DrugUpdateDTO(quantity = 20.0)
        val updatedDrug = createTestDrug(quantity = 20.0)
        whenever(drugService.update(any(), any(), any())).thenReturn(updatedDrug)
        whenever(drugService.toDrugDTO(updatedDrug)).thenReturn(
            DrugDTO(id = updatedDrug.id, name = updatedDrug.name, quantity = 20.0, medKitId = testMedKitId)
        )

        mockMvc.perform(
            put("/drug/{id}", testDrugId)
                .with(user(testUserId.toString()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateDTO))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.name").value("Test Drug")) // name unchanged
    }

    // =============== DELETE /drug/{id} Tests ===============

    @Test
    fun `deleteDrug - happy path - returns 204`() {
        doNothing().whenever(drugService).delete(testDrugId, testUserId)

        mockMvc.perform(
            delete("/drug/{id}", testDrugId)
                .with(user(testUserId.toString()))
        )
            .andExpect(status().isNoContent)

        verify(drugService).delete(testDrugId, testUserId)
    }

    @Test
    fun `deleteDrug - not found - returns 404`() {
        doThrow(ResponseStatusException(HttpStatus.NOT_FOUND, "Drug not found"))
            .whenever(drugService).delete(any(), any())

        mockMvc.perform(
            delete("/drug/{id}", testDrugId)
                .with(user(testUserId.toString()))
        )
            .andExpect(status().isNotFound)
    }

    @Test
    fun `deleteDrug - forbidden - not users drug`() {
        doThrow(ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied"))
            .whenever(drugService).delete(any(), any())

        mockMvc.perform(
            delete("/drug/{id}", testDrugId)
                .with(user(testUserId.toString()))
        )
            .andExpect(status().isForbidden)
    }

    @Test
    fun `deleteDrug - unauthorized - no authentication`() {
        mockMvc.perform(delete("/drug/{id}", testDrugId))
            .andExpect(status().isUnauthorized)

        verifyNoInteractions(drugService)
    }

    @Test
    fun `deleteDrug - with active treatment plan - still deletes`() {
        // Business logic: deleting drug should cascade delete treatment plans
        doNothing().whenever(drugService).delete(testDrugId, testUserId)

        mockMvc.perform(
            delete("/drug/{id}", testDrugId)
                .with(user(testUserId.toString()))
        )
            .andExpect(status().isNoContent)
    }

    // =============== GET /drug/quantity/{id} Tests ===============

    @Test
    fun `getDrugQuantityInfo - happy path - returns quantity info`() {
        val drug = createTestDrug(quantity = 10.0)
        whenever(drugService.findByIdForUser(testDrugId, testUserId)).thenReturn(drug)
        whenever(drugService.getAvailableQuantity(testDrugId)).thenReturn(Pair(10.0, 3.0))

        mockMvc.perform(
            get("/drug/quantity/{id}", testDrugId)
                .with(user(testUserId.toString()))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.actualQuantity").value(10.0))
            .andExpect(jsonPath("$.plannedQuantity").value(3.0))
            .andExpect(jsonPath("$.availableQuantity").value(7.0))

        verify(drugService).findByIdForUser(testDrugId, testUserId)
        verify(drugService).getAvailableQuantity(testDrugId)
    }

    @Test
    fun `getDrugQuantityInfo - not found - returns 404`() {
        whenever(drugService.findByIdForUser(any(), any()))
            .thenThrow(ResponseStatusException(HttpStatus.NOT_FOUND, "Drug not found"))

        mockMvc.perform(
            get("/drug/quantity/{id}", testDrugId)
                .with(user(testUserId.toString()))
        )
            .andExpect(status().isNotFound)
    }

    @Test
    fun `getDrugQuantityInfo - forbidden - no access`() {
        whenever(drugService.findByIdForUser(any(), any()))
            .thenThrow(ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied"))

        mockMvc.perform(
            get("/drug/quantity/{id}", testDrugId)
                .with(user(testUserId.toString()))
        )
            .andExpect(status().isForbidden)
    }

    @Test
    fun `getDrugQuantityInfo - with active treatment plan - returns correct planned`() {
        val drug = createTestDrug(quantity = 100.0)
        whenever(drugService.findByIdForUser(testDrugId, testUserId)).thenReturn(drug)
        whenever(drugService.getAvailableQuantity(testDrugId)).thenReturn(Pair(100.0, 50.0))

        mockMvc.perform(
            get("/drug/quantity/{id}", testDrugId)
                .with(user(testUserId.toString()))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.plannedQuantity").value(50.0))
            .andExpect(jsonPath("$.availableQuantity").value(50.0))
    }

    @Test
    fun `getDrugQuantityInfo - zero quantity - returns zero available`() {
        val drug = createTestDrug(quantity = 0.0)
        whenever(drugService.findByIdForUser(testDrugId, testUserId)).thenReturn(drug)
        whenever(drugService.getAvailableQuantity(testDrugId)).thenReturn(Pair(0.0, 0.0))

        mockMvc.perform(
            get("/drug/quantity/{id}", testDrugId)
                .with(user(testUserId.toString()))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.actualQuantity").value(0.0))
            .andExpect(jsonPath("$.availableQuantity").value(0.0))
    }

    // =============== PUT /drug/consume/{id} Tests ===============

    @Test
    fun `consumeDrug - happy path - reduces quantity`() {
        val request = ConsumeRequest(quantity = 2.0)
        val drug = createTestDrug(quantity = 8.0)
        whenever(drugService.consumeDrug(testDrugId, 2.0, testUserId)).thenReturn(drug)
        whenever(drugService.toDrugDTO(drug)).thenReturn(
            DrugDTO(id = drug.id, name = drug.name, quantity = 8.0, medKitId = testMedKitId)
        )

        mockMvc.perform(
            put("/drug/consume/{id}", testDrugId)
                .with(user(testUserId.toString()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.quantity").value(8.0))

        verify(drugService).consumeDrug(testDrugId, 2.0, testUserId)
    }

    @Test
    fun `consumeDrug - validation error - negative quantity`() {
        val request = ConsumeRequest(quantity = -1.0)

        mockMvc.perform(
            put("/drug/consume/{id}", testDrugId)
                .with(user(testUserId.toString()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isBadRequest)

        verifyNoInteractions(drugService)
    }

    @Test
    fun `consumeDrug - exceeds available - returns 400`() {
        val request = ConsumeRequest(quantity = 20.0)
        whenever(drugService.consumeDrug(any(), any(), any()))
            .thenThrow(ResponseStatusException(HttpStatus.BAD_REQUEST, "Insufficient quantity"))

        mockMvc.perform(
            put("/drug/consume/{id}", testDrugId)
                .with(user(testUserId.toString()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `consumeDrug - not found - returns 404`() {
        val request = ConsumeRequest(quantity = 2.0)
        whenever(drugService.consumeDrug(any(), any(), any()))
            .thenThrow(ResponseStatusException(HttpStatus.NOT_FOUND, "Drug not found"))

        mockMvc.perform(
            put("/drug/consume/{id}", testDrugId)
                .with(user(testUserId.toString()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isNotFound)
    }

    @Test
    fun `consumeDrug - forbidden - no access`() {
        val request = ConsumeRequest(quantity = 2.0)
        whenever(drugService.consumeDrug(any(), any(), any()))
            .thenThrow(ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied"))

        mockMvc.perform(
            put("/drug/consume/{id}", testDrugId)
                .with(user(testUserId.toString()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isForbidden)
    }

    @Test
    fun `consumeDrug - consume all - results in zero`() {
        val request = ConsumeRequest(quantity = 10.0)
        val drug = createTestDrug(quantity = 0.0)
        whenever(drugService.consumeDrug(testDrugId, 10.0, testUserId)).thenReturn(drug)
        whenever(drugService.toDrugDTO(drug)).thenReturn(
            DrugDTO(id = drug.id, name = drug.name, quantity = 0.0, medKitId = testMedKitId)
        )

        mockMvc.perform(
            put("/drug/consume/{id}", testDrugId)
                .with(user(testUserId.toString()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.quantity").value(0.0))
    }

    // =============== PUT /drug/move/{id} Tests ===============

    @Test
    fun `moveDrug - happy path - moves to target medkit`() {
        val targetMedKitId = UUID.randomUUID()
        val request = MoveDrugRequest(targetMedKitId = targetMedKitId)
        val movedDrug = createTestDrug()
        whenever(drugService.moveDrug(testDrugId, targetMedKitId, testUserId)).thenReturn(movedDrug)
        whenever(drugService.toDrugDTO(movedDrug)).thenReturn(
            DrugDTO(id = movedDrug.id, name = movedDrug.name, quantity = movedDrug.quantity, medKitId = targetMedKitId)
        )

        mockMvc.perform(
            put("/drug/move/{id}", testDrugId)
                .with(user(testUserId.toString()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(testDrugId.toString()))

        verify(drugService).moveDrug(testDrugId, targetMedKitId, testUserId)
    }

    @Test
    fun `moveDrug - not found - drug does not exist`() {
        val request = MoveDrugRequest(targetMedKitId = UUID.randomUUID())
        whenever(drugService.moveDrug(any(), any(), any()))
            .thenThrow(ResponseStatusException(HttpStatus.NOT_FOUND, "Drug not found"))

        mockMvc.perform(
            put("/drug/move/{id}", testDrugId)
                .with(user(testUserId.toString()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isNotFound)
    }

    @Test
    fun `moveDrug - not found - target medkit does not exist`() {
        val request = MoveDrugRequest(targetMedKitId = UUID.randomUUID())
        whenever(drugService.moveDrug(any(), any(), any()))
            .thenThrow(ResponseStatusException(HttpStatus.NOT_FOUND, "Target MedKit not found"))

        mockMvc.perform(
            put("/drug/move/{id}", testDrugId)
                .with(user(testUserId.toString()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isNotFound)
    }

    @Test
    fun `moveDrug - forbidden - no access to source drug`() {
        val request = MoveDrugRequest(targetMedKitId = UUID.randomUUID())
        whenever(drugService.moveDrug(any(), any(), any()))
            .thenThrow(ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied to source drug"))

        mockMvc.perform(
            put("/drug/move/{id}", testDrugId)
                .with(user(testUserId.toString()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isForbidden)
    }

    @Test
    fun `moveDrug - forbidden - no access to target medkit`() {
        val request = MoveDrugRequest(targetMedKitId = UUID.randomUUID())
        whenever(drugService.moveDrug(any(), any(), any()))
            .thenThrow(ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied to target medkit"))

        mockMvc.perform(
            put("/drug/move/{id}", testDrugId)
                .with(user(testUserId.toString()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isForbidden)
    }

    @Test
    fun `moveDrug - with treatment plans - moves successfully`() {
        val targetMedKitId = UUID.randomUUID()
        val request = MoveDrugRequest(targetMedKitId = targetMedKitId)
        val movedDrug = createTestDrug()
        whenever(drugService.moveDrug(any(), any(), any())).thenReturn(movedDrug)
        whenever(drugService.toDrugDTO(movedDrug)).thenReturn(
            DrugDTO(id = movedDrug.id, name = movedDrug.name, quantity = movedDrug.quantity, medKitId = targetMedKitId)
        )

        mockMvc.perform(
            put("/drug/move/{id}", testDrugId)
                .with(user(testUserId.toString()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isOk)
    }

    // =============== GET /drug/template/search Tests ===============

    @Test
    fun `searchDrugTemplates - happy path - returns results`() {
        val templates = listOf(
            VidalDrug(id = UUID.randomUUID(), name = "Aspirin", formType = null, category = "Painkiller", manufacturer = "Bayer", description = null)
        )
        whenever(vidalDrugService.fuzzySearchByName("aspirin", 10)).thenReturn(templates)

        mockMvc.perform(
            get("/drug/template/search")
                .with(user(testUserId.toString()))
                .param("searchTerm", "aspirin")
                .param("limit", "10")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$").isArray)
            .andExpect(jsonPath("$[0].name").value("Aspirin"))
            .andExpect(jsonPath("$[0].category").value("Painkiller"))

        verify(vidalDrugService).fuzzySearchByName("aspirin", 10)
    }

    @Test
    fun `searchDrugTemplates - no results - returns empty array`() {
        whenever(vidalDrugService.fuzzySearchByName(any(), any())).thenReturn(emptyList())

        mockMvc.perform(
            get("/drug/template/search")
                .with(user(testUserId.toString()))
                .param("searchTerm", "nonexistent")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$").isArray)
            .andExpect(jsonPath("$").isEmpty)
    }

    @Test
    fun `searchDrugTemplates - empty search term - returns results`() {
        whenever(vidalDrugService.fuzzySearchByName("", 10)).thenReturn(emptyList())

        mockMvc.perform(
            get("/drug/template/search")
                .with(user(testUserId.toString()))
                .param("searchTerm", "")
        )
            .andExpect(status().isOk)
    }

    @Test
    fun `searchDrugTemplates - custom limit - respects limit parameter`() {
        whenever(vidalDrugService.fuzzySearchByName("drug", 5)).thenReturn(emptyList())

        mockMvc.perform(
            get("/drug/template/search")
                .with(user(testUserId.toString()))
                .param("searchTerm", "drug")
                .param("limit", "5")
        )
            .andExpect(status().isOk)

        verify(vidalDrugService).fuzzySearchByName("drug", 5)
    }

    @Test
    fun `searchDrugTemplates - default limit - uses 10`() {
        whenever(vidalDrugService.fuzzySearchByName("drug", 10)).thenReturn(emptyList())

        mockMvc.perform(
            get("/drug/template/search")
                .with(user(testUserId.toString()))
                .param("searchTerm", "drug")
        )
            .andExpect(status().isOk)

        verify(vidalDrugService).fuzzySearchByName("drug", 10)
    }

    @Test
    fun `searchDrugTemplates - special characters in search - handles correctly`() {
        whenever(vidalDrugService.fuzzySearchByName("drug-123!@#", 10)).thenReturn(emptyList())

        mockMvc.perform(
            get("/drug/template/search")
                .with(user(testUserId.toString()))
                .param("searchTerm", "drug-123!@#")
        )
            .andExpect(status().isOk)

        verify(vidalDrugService).fuzzySearchByName("drug-123!@#", 10)
    }

    // =============== GET /drug/template/{id} Tests ===============

    @Test
    fun `getDrugTemplate - happy path - returns template`() {
        val templateId = UUID.randomUUID()
        val template = VidalDrug(
            id = templateId,
            name = "Aspirin",
            formType = null,
            category = "Painkiller",
            manufacturer = "Bayer",
            description = "Pain reliever"
        )
        whenever(vidalDrugService.findById(templateId)).thenReturn(template)

        mockMvc.perform(
            get("/drug/template/{id}", templateId)
                .with(user(testUserId.toString()))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(templateId.toString()))
            .andExpect(jsonPath("$.name").value("Aspirin"))
            .andExpect(jsonPath("$.category").value("Painkiller"))

        verify(vidalDrugService).findById(templateId)
    }

    @Test
    fun `getDrugTemplate - not found - returns 404`() {
        val templateId = UUID.randomUUID()
        whenever(vidalDrugService.findById(templateId)).thenReturn(null)

        mockMvc.perform(
            get("/drug/template/{id}", templateId)
                .with(user(testUserId.toString()))
        )
            .andExpect(status().isNotFound)
    }

    @Test
    fun `getDrugTemplate - minimal data - returns partial template`() {
        val templateId = UUID.randomUUID()
        val template = VidalDrug(
            id = templateId,
            name = "Basic Drug",
            formType = null,
            category = null,
            manufacturer = null,
            description = null
        )
        whenever(vidalDrugService.findById(templateId)).thenReturn(template)

        mockMvc.perform(
            get("/drug/template/{id}", templateId)
                .with(user(testUserId.toString()))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.name").value("Basic Drug"))
            .andExpect(jsonPath("$.category").isEmpty)
    }
}
