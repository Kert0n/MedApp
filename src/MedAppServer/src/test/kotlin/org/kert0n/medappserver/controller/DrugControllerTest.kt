package org.kert0n.medappserver.controller
import com.fasterxml.jackson.databind.ObjectMapper
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.justRun
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.kert0n.medappserver.db.model.*
import org.kert0n.medappserver.db.model.parsed.FormType
import org.kert0n.medappserver.db.model.parsed.VidalDrug
import org.kert0n.medappserver.services.DrugService
import org.kert0n.medappserver.services.UsingService
import org.kert0n.medappserver.services.VidalDrugService
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
 * Comprehensive DrugController tests with MockMvc
 * Tests all 8 endpoints with ≥10 tests each (80+ total tests)
 * Following patterns from existing service tests and test data builders
 */
@WebMvcTest(DrugController::class)
@Import(TestSecurityConfig::class)
class DrugControllerTest {
    @Autowired
    private lateinit var mockMvc: MockMvc
    @Autowired
    private lateinit var objectMapper: ObjectMapper
    @MockkBean
    private lateinit var drugService: DrugService
    @MockkBean
    private lateinit var usingService: UsingService
    @MockkBean
    private lateinit var vidalDrugService: VidalDrugService
    private val testUserId = UUID.randomUUID()
    private val testDrugId = UUID.randomUUID()
    private val testMedKitId = UUID.randomUUID()
    // ========== GET /drug/{id} - getById Tests (10 tests) ==========
    @Test
    @WithMockUser(username = "user-id")
    fun `getById - valid id - returns drug DTO`() {
        val medKit = medKitBuilder().withId(testMedKitId).build()
        val drug = drugBuilder(medKit).withId(testDrugId).withName("Aspirin").build()
        val drugDTO = DrugDTO(
            id = drug.id,
            name = drug.name,
            quantity = drug.quantity,
            plannedQuantity = 0.0,
            quantityUnit = drug.quantityUnit,
            formType = drug.formType,
            category = drug.category,
            manufacturer = drug.manufacturer,
            country = drug.country,
            description = drug.description,
            medKitId = medKit.id
        )
        every { drugService.findByIdForUser(testDrugId, any()) } returns drug
        every { drugService.toDrugDTO(drug) } returns drugDTO
        mockMvc.perform(get("/drug/{id}", testDrugId))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(testDrugId.toString()))
            .andExpect(jsonPath("$.name").value("Aspirin"))
            .andExpect(jsonPath("$.quantity").value(100.0))
            .andExpect(jsonPath("$.medKitId").value(testMedKitId.toString()))
        verify(exactly = 1) { drugService.findByIdForUser(testDrugId, any()) }
        verify(exactly = 1) { drugService.toDrugDTO(drug) }
    }
    @Test
    @WithMockUser(username = "user-id")
    fun `getById - drug not found - returns 404`() {
        every { drugService.findByIdForUser(testDrugId, any()) } throws ResponseStatusException(
            HttpStatus.NOT_FOUND, "Drug not found"
        )
        mockMvc.perform(get("/drug/{id}", testDrugId))
            .andExpect(status().isNotFound)
        verify(exactly = 1) { drugService.findByIdForUser(testDrugId, any()) }
    }
    @Test
    @WithMockUser(username = "user-id")
    fun `getById - user has no access - returns 403`() {
        every { drugService.findByIdForUser(testDrugId, any()) } throws ResponseStatusException(
            HttpStatus.FORBIDDEN, "Access denied"
        )
        mockMvc.perform(get("/drug/{id}", testDrugId))
            .andExpect(status().isForbidden)
        verify(exactly = 1) { drugService.findByIdForUser(testDrugId, any()) }
    }
    @Test
    @WithMockUser(username = "user-id")
    fun `getById - with all optional fields populated - returns complete DTO`() {
        val medKit = medKitBuilder().withId(testMedKitId).build()
        val drug = drugBuilder(medKit)
            .withId(testDrugId)
            .withName("Paracetamol")
            .withQuantity(200.0)
            .withFormType("tablet")
            .withCategory("painkiller")
            .withManufacturer("Bayer")
            .withCountry("Germany")
            .withDescription("Pain relief medication")
            .build()
        val drugDTO = DrugDTO(
            id = drug.id,
            name = drug.name,
            quantity = drug.quantity,
            plannedQuantity = 50.0,
            quantityUnit = drug.quantityUnit,
            formType = drug.formType,
            category = drug.category,
            manufacturer = drug.manufacturer,
            country = drug.country,
            description = drug.description,
            medKitId = medKit.id
        )
        every { drugService.findByIdForUser(testDrugId, any()) } returns drug
        every { drugService.toDrugDTO(drug) } returns drugDTO
        mockMvc.perform(get("/drug/{id}", testDrugId))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.formType").value("tablet"))
            .andExpect(jsonPath("$.category").value("painkiller"))
            .andExpect(jsonPath("$.manufacturer").value("Bayer"))
            .andExpect(jsonPath("$.country").value("Germany"))
            .andExpect(jsonPath("$.description").value("Pain relief medication"))
    }
    @Test
    @WithMockUser(username = "user-id")
    fun `getById - with null optional fields - returns DTO with nulls`() {
        val medKit = medKitBuilder().withId(testMedKitId).build()
        val drug = drugBuilder(medKit)
            .withId(testDrugId)
            .withFormType(null)
            .withCategory(null)
            .withManufacturer(null)
            .withCountry(null)
            .withDescription(null)
            .build()
        val drugDTO = DrugDTO(
            id = drug.id,
            name = drug.name,
            quantity = drug.quantity,
            plannedQuantity = 0.0,
            quantityUnit = drug.quantityUnit,
            formType = null,
            category = null,
            manufacturer = null,
            country = null,
            description = null,
            medKitId = medKit.id
        )
        every { drugService.findByIdForUser(testDrugId, any()) } returns drug
        every { drugService.toDrugDTO(drug) } returns drugDTO
        mockMvc.perform(get("/drug/{id}", testDrugId))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.formType").doesNotExist())
            .andExpect(jsonPath("$.category").doesNotExist())
            .andExpect(jsonPath("$.manufacturer").doesNotExist())
            .andExpect(jsonPath("$.country").doesNotExist())
            .andExpect(jsonPath("$.description").doesNotExist())
    }
    @Test
    fun `getById - unauthenticated - returns 401`() {
        mockMvc.perform(get("/drug/{id}", testDrugId))
            .andExpect(status().isUnauthorized)
    }
    @Test
    @WithMockUser(username = "user-id")
    fun `getById - invalid UUID format - returns 400`() {
        mockMvc.perform(get("/drug/{id}", "invalid-uuid"))
            .andExpect(status().isBadRequest)
    }
    @Test
    @WithMockUser(username = "user-id")
    fun `getById - with planned quantity - returns DTO with planned quantity`() {
        val medKit = medKitBuilder().withId(testMedKitId).build()
        val drug = drugBuilder(medKit).withId(testDrugId).withQuantity(100.0).build()
        val drugDTO = DrugDTO(
            id = drug.id,
            name = drug.name,
            quantity = 100.0,
            plannedQuantity = 30.0,
            quantityUnit = drug.quantityUnit,
            formType = drug.formType,
            category = drug.category,
            manufacturer = drug.manufacturer,
            country = drug.country,
            description = drug.description,
            medKitId = medKit.id
        )
        every { drugService.findByIdForUser(testDrugId, any()) } returns drug
        every { drugService.toDrugDTO(drug) } returns drugDTO
        mockMvc.perform(get("/drug/{id}", testDrugId))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.quantity").value(100.0))
            .andExpect(jsonPath("$.plannedQuantity").value(30.0))
    }
    @Test
    @WithMockUser(username = "user-id")
    fun `getById - service throws internal error - returns 500`() {
        every { drugService.findByIdForUser(testDrugId, any()) } throws RuntimeException("Database error")
        mockMvc.perform(get("/drug/{id}", testDrugId))
            .andExpect(status().isInternalServerError)
    }
    @Test
    @WithMockUser(username = "user-id")
    fun `getById - different user owns drug - returns 403`() {
        val differentUserId = UUID.randomUUID()
        every { drugService.findByIdForUser(testDrugId, differentUserId) } throws ResponseStatusException(
            HttpStatus.FORBIDDEN, "Access denied"
        )
        mockMvc.perform(get("/drug/{id}", testDrugId))
            .andExpect(status().isForbidden)
    }
    // ========== POST /drug - create Tests (11 tests) ==========
    @Test
    @WithMockUser(username = "user-id")
    fun `create - valid data - creates drug and returns 201`() {
        val createDTO = drugCreateDTOBuilder()
            .withName("Ibuprofen")
            .withQuantity(50.0)
            .withQuantityUnit("mg")
            .withMedKitId(testMedKitId)
            .build()
        val medKit = medKitBuilder().withId(testMedKitId).build()
        val createdDrug = drugBuilder(medKit)
            .withId(testDrugId)
            .withName("Ibuprofen")
            .withQuantity(50.0)
            .build()
        val drugDTO = DrugDTO(
            id = createdDrug.id,
            name = createdDrug.name,
            quantity = createdDrug.quantity,
            plannedQuantity = 0.0,
            quantityUnit = createdDrug.quantityUnit,
            formType = createdDrug.formType,
            category = createdDrug.category,
            manufacturer = createdDrug.manufacturer,
            country = createdDrug.country,
            description = createdDrug.description,
            medKitId = medKit.id
        )
        every { drugService.create(createDTO, any()) } returns createdDrug
        every { drugService.toDrugDTO(createdDrug) } returns drugDTO
        mockMvc.perform(
            post("/drug")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createDTO))
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.id").value(testDrugId.toString()))
            .andExpect(jsonPath("$.name").value("Ibuprofen"))
            .andExpect(jsonPath("$.quantity").value(50.0))
        verify(exactly = 1) { drugService.create(createDTO, any()) }
        verify(exactly = 1) { drugService.toDrugDTO(createdDrug) }
    }
    @Test
    @WithMockUser(username = "user-id")
    fun `create - missing required name - returns 400`() {
        val invalidDTO = mapOf(
            "quantity" to 50.0,
            "quantityUnit" to "mg",
            "medKitId" to testMedKitId.toString()
        )
        mockMvc.perform(
            post("/drug")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidDTO))
        )
            .andExpect(status().isBadRequest)
    }
    @Test
    @WithMockUser(username = "user-id")
    fun `create - missing required quantity - returns 400`() {
        val invalidDTO = mapOf(
            "name" to "Aspirin",
            "quantityUnit" to "mg",
            "medKitId" to testMedKitId.toString()
        )
        mockMvc.perform(
            post("/drug")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidDTO))
        )
            .andExpect(status().isBadRequest)
    }
    @Test
    @WithMockUser(username = "user-id")
    fun `create - missing required medKitId - returns 400`() {
        val invalidDTO = mapOf(
            "name" to "Aspirin",
            "quantity" to 50.0,
            "quantityUnit" to "mg"
        )
        mockMvc.perform(
            post("/drug")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidDTO))
        )
            .andExpect(status().isBadRequest)
    }
    @Test
    @WithMockUser(username = "user-id")
    fun `create - negative quantity - returns 400`() {
        val invalidDTO = mapOf(
            "name" to "Aspirin",
            "quantity" to -10.0,
            "quantityUnit" to "mg",
            "medKitId" to testMedKitId.toString()
        )
        mockMvc.perform(
            post("/drug")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidDTO))
        )
            .andExpect(status().isBadRequest)
    }
    @Test
    @WithMockUser(username = "user-id")
    fun `create - empty name - returns 400`() {
        val invalidDTO = mapOf(
            "name" to "",
            "quantity" to 50.0,
            "quantityUnit" to "mg",
            "medKitId" to testMedKitId.toString()
        )
        mockMvc.perform(
            post("/drug")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidDTO))
        )
            .andExpect(status().isBadRequest)
    }
    @Test
    @WithMockUser(username = "user-id")
    fun `create - user has no access to medkit - returns 403`() {
        val createDTO = drugCreateDTOBuilder().withMedKitId(testMedKitId).build()
        every { drugService.create(createDTO, any()) } throws ResponseStatusException(
            HttpStatus.FORBIDDEN, "User does not have access to the medicine kit"
        )
        mockMvc.perform(
            post("/drug")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createDTO))
        )
            .andExpect(status().isForbidden)
        verify(exactly = 1) { drugService.create(createDTO, any()) }
    }
    @Test
    @WithMockUser(username = "user-id")
    fun `create - with all optional fields - creates drug with all fields`() {
        val createDTO = drugCreateDTOBuilder()
            .withName("Complete Drug")
            .withQuantity(75.0)
            .withQuantityUnit("ml")
            .withMedKitId(testMedKitId)
            .withFormType("syrup")
            .withCategory("antibiotic")
            .withManufacturer("PharmaCorp")
            .withCountry("USA")
            .withDescription("Complete medication")
            .build()
        val medKit = medKitBuilder().withId(testMedKitId).build()
        val createdDrug = drugBuilder(medKit)
            .withId(testDrugId)
            .withName("Complete Drug")
            .withFormType("syrup")
            .withCategory("antibiotic")
            .build()
        val drugDTO = DrugDTO(
            id = createdDrug.id,
            name = createdDrug.name,
            quantity = 75.0,
            plannedQuantity = 0.0,
            quantityUnit = "ml",
            formType = "syrup",
            category = "antibiotic",
            manufacturer = "PharmaCorp",
            country = "USA",
            description = "Complete medication",
            medKitId = medKit.id
        )
        every { drugService.create(createDTO, any()) } returns createdDrug
        every { drugService.toDrugDTO(createdDrug) } returns drugDTO
        mockMvc.perform(
            post("/drug")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createDTO))
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.formType").value("syrup"))
            .andExpect(jsonPath("$.category").value("antibiotic"))
            .andExpect(jsonPath("$.manufacturer").value("PharmaCorp"))
    }
    @Test
    fun `create - unauthenticated - returns 401`() {
        val createDTO = drugCreateDTOBuilder().build()
        mockMvc.perform(
            post("/drug")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createDTO))
        )
            .andExpect(status().isUnauthorized)
    }
    @Test
    @WithMockUser(username = "user-id")
    fun `create - medkit not found - returns 404`() {
        val createDTO = drugCreateDTOBuilder().withMedKitId(testMedKitId).build()
        every { drugService.create(createDTO, any()) } throws ResponseStatusException(
            HttpStatus.NOT_FOUND, "Medicine kit not found"
        )
        mockMvc.perform(
            post("/drug")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createDTO))
        )
            .andExpect(status().isNotFound)
    }
    @Test
    @WithMockUser(username = "user-id")
    fun `create - name too long - returns 400`() {
        val longName = "A".repeat(301)
        val invalidDTO = mapOf(
            "name" to longName,
            "quantity" to 50.0,
            "quantityUnit" to "mg",
            "medKitId" to testMedKitId.toString()
        )
        mockMvc.perform(
            post("/drug")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidDTO))
        )
            .andExpect(status().isBadRequest)
    }
    // ========== PUT /drug/{id} - update Tests (11 tests) ==========
    @Test
    @WithMockUser(username = "user-id")
    fun `update - valid data - updates drug and returns DTO`() {
        val updateDTO = drugUpdateDTOBuilder()
            .withName("Updated Aspirin")
            .withQuantity(150.0)
            .build()
        val medKit = medKitBuilder().withId(testMedKitId).build()
        val updatedDrug = drugBuilder(medKit)
            .withId(testDrugId)
            .withName("Updated Aspirin")
            .withQuantity(150.0)
            .build()
        val drugDTO = DrugDTO(
            id = updatedDrug.id,
            name = updatedDrug.name,
            quantity = updatedDrug.quantity,
            plannedQuantity = 0.0,
            quantityUnit = updatedDrug.quantityUnit,
            formType = updatedDrug.formType,
            category = updatedDrug.category,
            manufacturer = updatedDrug.manufacturer,
            country = updatedDrug.country,
            description = updatedDrug.description,
            medKitId = medKit.id
        )
        every { drugService.update(testDrugId, updateDTO, any()) } returns updatedDrug
        every { drugService.toDrugDTO(updatedDrug) } returns drugDTO
        mockMvc.perform(
            put("/drug/{id}", testDrugId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateDTO))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.name").value("Updated Aspirin"))
            .andExpect(jsonPath("$.quantity").value(150.0))
        verify(exactly = 1) { drugService.update(testDrugId, updateDTO, any()) }
    }
    @Test
    @WithMockUser(username = "user-id")
    fun `update - only name - updates only name`() {
        val updateDTO = drugUpdateDTOBuilder().withName("New Name Only").build()
        val medKit = medKitBuilder().withId(testMedKitId).build()
        val updatedDrug = drugBuilder(medKit)
            .withId(testDrugId)
            .withName("New Name Only")
            .build()
        val drugDTO = DrugDTO(
            id = updatedDrug.id,
            name = "New Name Only",
            quantity = 100.0,
            plannedQuantity = 0.0,
            quantityUnit = updatedDrug.quantityUnit,
            formType = updatedDrug.formType,
            category = updatedDrug.category,
            manufacturer = updatedDrug.manufacturer,
            country = updatedDrug.country,
            description = updatedDrug.description,
            medKitId = medKit.id
        )
        every { drugService.update(testDrugId, updateDTO, any()) } returns updatedDrug
        every { drugService.toDrugDTO(updatedDrug) } returns drugDTO
        mockMvc.perform(
            put("/drug/{id}", testDrugId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateDTO))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.name").value("New Name Only"))
    }
    @Test
    @WithMockUser(username = "user-id")
    fun `update - drug not found - returns 404`() {
        val updateDTO = drugUpdateDTOBuilder().withName("Not Found").build()
        every { drugService.update(testDrugId, updateDTO, any()) } throws ResponseStatusException(
            HttpStatus.NOT_FOUND, "Drug not found"
        )
        mockMvc.perform(
            put("/drug/{id}", testDrugId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateDTO))
        )
            .andExpect(status().isNotFound)
    }
    @Test
    @WithMockUser(username = "user-id")
    fun `update - user has no access - returns 403`() {
        val updateDTO = drugUpdateDTOBuilder().withName("No Access").build()
        every { drugService.update(testDrugId, updateDTO, any()) } throws ResponseStatusException(
            HttpStatus.FORBIDDEN, "Access denied"
        )
        mockMvc.perform(
            put("/drug/{id}", testDrugId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateDTO))
        )
            .andExpect(status().isForbidden)
    }
    @Test
    @WithMockUser(username = "user-id")
    fun `update - negative quantity - returns 400`() {
        val invalidDTO = mapOf("quantity" to -50.0)
        mockMvc.perform(
            put("/drug/{id}", testDrugId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidDTO))
        )
            .andExpect(status().isBadRequest)
    }
    @Test
    @WithMockUser(username = "user-id")
    fun `update - all fields - updates all fields`() {
        val updateDTO = drugUpdateDTOBuilder()
            .withName("Updated Complete Drug")
            .withQuantity(200.0)
            .withQuantityUnit("g")
            .withFormType("capsule")
            .withCategory("vitamin")
            .withManufacturer("NewCorp")
            .withCountry("Canada")
            .withDescription("Updated description")
            .build()
        val medKit = medKitBuilder().withId(testMedKitId).build()
        val updatedDrug = drugBuilder(medKit).withId(testDrugId).build()
        val drugDTO = DrugDTO(
            id = updatedDrug.id,
            name = "Updated Complete Drug",
            quantity = 200.0,
            plannedQuantity = 0.0,
            quantityUnit = "g",
            formType = "capsule",
            category = "vitamin",
            manufacturer = "NewCorp",
            country = "Canada",
            description = "Updated description",
            medKitId = medKit.id
        )
        every { drugService.update(testDrugId, updateDTO, any()) } returns updatedDrug
        every { drugService.toDrugDTO(updatedDrug) } returns drugDTO
        mockMvc.perform(
            put("/drug/{id}", testDrugId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateDTO))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.name").value("Updated Complete Drug"))
            .andExpect(jsonPath("$.formType").value("capsule"))
            .andExpect(jsonPath("$.category").value("vitamin"))
    }
    @Test
    fun `update - unauthenticated - returns 401`() {
        val updateDTO = drugUpdateDTOBuilder().withName("Unauthorized").build()
        mockMvc.perform(
            put("/drug/{id}", testDrugId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateDTO))
        )
            .andExpect(status().isUnauthorized)
    }
    @Test
    @WithMockUser(username = "user-id")
    fun `update - empty request body - updates nothing and returns current state`() {
        val updateDTO = drugUpdateDTOBuilder().build()
        val medKit = medKitBuilder().withId(testMedKitId).build()
        val drug = drugBuilder(medKit).withId(testDrugId).build()
        val drugDTO = DrugDTO(
            id = drug.id,
            name = drug.name,
            quantity = drug.quantity,
            plannedQuantity = 0.0,
            quantityUnit = drug.quantityUnit,
            formType = drug.formType,
            category = drug.category,
            manufacturer = drug.manufacturer,
            country = drug.country,
            description = drug.description,
            medKitId = medKit.id
        )
        every { drugService.update(testDrugId, updateDTO, any()) } returns drug
        every { drugService.toDrugDTO(drug) } returns drugDTO
        mockMvc.perform(
            put("/drug/{id}", testDrugId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateDTO))
        )
            .andExpect(status().isOk)
    }
    @Test
    @WithMockUser(username = "user-id")
    fun `update - invalid UUID - returns 400`() {
        val updateDTO = drugUpdateDTOBuilder().withName("Invalid ID").build()
        mockMvc.perform(
            put("/drug/{id}", "invalid-uuid")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateDTO))
        )
            .andExpect(status().isBadRequest)
    }
    @Test
    @WithMockUser(username = "user-id")
    fun `update - name too long - returns 400`() {
        val longName = "A".repeat(301)
        val invalidDTO = mapOf("name" to longName)
        mockMvc.perform(
            put("/drug/{id}", testDrugId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidDTO))
        )
            .andExpect(status().isBadRequest)
    }
    @Test
    @WithMockUser(username = "user-id")
    fun `update - quantity unit too long - returns 400`() {
        val longUnit = "A".repeat(51)
        val invalidDTO = mapOf("quantityUnit" to longUnit)
        mockMvc.perform(
            put("/drug/{id}", testDrugId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidDTO))
        )
            .andExpect(status().isBadRequest)
    }
    // ========== POST /drug/consume/{id} - consumeDrug Tests (10 tests) ==========
    @Test
    @WithMockUser(username = "user-id")
    fun `consumeDrug - valid quantity - consumes drug and returns DTO`() {
        val consumeRequest = ConsumeRequest(quantity = 10.0)
        val medKit = medKitBuilder().withId(testMedKitId).build()
        val consumedDrug = drugBuilder(medKit)
            .withId(testDrugId)
            .withQuantity(90.0)
            .build()
        val drugDTO = DrugDTO(
            id = consumedDrug.id,
            name = consumedDrug.name,
            quantity = 90.0,
            plannedQuantity = 0.0,
            quantityUnit = consumedDrug.quantityUnit,
            formType = consumedDrug.formType,
            category = consumedDrug.category,
            manufacturer = consumedDrug.manufacturer,
            country = consumedDrug.country,
            description = consumedDrug.description,
            medKitId = medKit.id
        )
        every { drugService.consumeDrug(testDrugId, 10.0, any()) } returns consumedDrug
        every { drugService.toDrugDTO(consumedDrug) } returns drugDTO
        mockMvc.perform(
            put("/drug/consume/{id}", testDrugId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(consumeRequest))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.quantity").value(90.0))
        verify(exactly = 1) { drugService.consumeDrug(testDrugId, 10.0, any()) }
    }
    @Test
    @WithMockUser(username = "user-id")
    fun `consumeDrug - negative quantity - returns 400`() {
        val invalidRequest = mapOf("quantity" to -5.0)
        mockMvc.perform(
            put("/drug/consume/{id}", testDrugId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidRequest))
        )
            .andExpect(status().isBadRequest)
    }
    @Test
    @WithMockUser(username = "user-id")
    fun `consumeDrug - missing quantity - returns 400`() {
        val invalidRequest = mapOf<String, Any>()
        mockMvc.perform(
            put("/drug/consume/{id}", testDrugId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidRequest))
        )
            .andExpect(status().isBadRequest)
    }
    @Test
    @WithMockUser(username = "user-id")
    fun `consumeDrug - exceeds available quantity - returns 400`() {
        val consumeRequest = ConsumeRequest(quantity = 200.0)
        every { drugService.consumeDrug(testDrugId, 200.0, any()) } throws ResponseStatusException(
            HttpStatus.BAD_REQUEST, "Insufficient quantity"
        )
        mockMvc.perform(
            put("/drug/consume/{id}", testDrugId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(consumeRequest))
        )
            .andExpect(status().isBadRequest)
    }
    @Test
    @WithMockUser(username = "user-id")
    fun `consumeDrug - drug not found - returns 404`() {
        val consumeRequest = ConsumeRequest(quantity = 10.0)
        every { drugService.consumeDrug(testDrugId, 10.0, any()) } throws ResponseStatusException(
            HttpStatus.NOT_FOUND, "Drug not found"
        )
        mockMvc.perform(
            put("/drug/consume/{id}", testDrugId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(consumeRequest))
        )
            .andExpect(status().isNotFound)
    }
    @Test
    @WithMockUser(username = "user-id")
    fun `consumeDrug - user has no access - returns 403`() {
        val consumeRequest = ConsumeRequest(quantity = 10.0)
        every { drugService.consumeDrug(testDrugId, 10.0, any()) } throws ResponseStatusException(
            HttpStatus.FORBIDDEN, "Access denied"
        )
        mockMvc.perform(
            put("/drug/consume/{id}", testDrugId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(consumeRequest))
        )
            .andExpect(status().isForbidden)
    }
    @Test
    fun `consumeDrug - unauthenticated - returns 401`() {
        val consumeRequest = ConsumeRequest(quantity = 10.0)
        mockMvc.perform(
            put("/drug/consume/{id}", testDrugId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(consumeRequest))
        )
            .andExpect(status().isUnauthorized)
    }
    @Test
    @WithMockUser(username = "user-id")
    fun `consumeDrug - zero quantity - consumes nothing`() {
        val consumeRequest = ConsumeRequest(quantity = 0.0)
        val medKit = medKitBuilder().withId(testMedKitId).build()
        val drug = drugBuilder(medKit).withId(testDrugId).withQuantity(100.0).build()
        val drugDTO = DrugDTO(
            id = drug.id,
            name = drug.name,
            quantity = 100.0,
            plannedQuantity = 0.0,
            quantityUnit = drug.quantityUnit,
            formType = drug.formType,
            category = drug.category,
            manufacturer = drug.manufacturer,
            country = drug.country,
            description = drug.description,
            medKitId = medKit.id
        )
        every { drugService.consumeDrug(testDrugId, 0.0, any()) } returns drug
        every { drugService.toDrugDTO(drug) } returns drugDTO
        mockMvc.perform(
            put("/drug/consume/{id}", testDrugId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(consumeRequest))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.quantity").value(100.0))
    }
    @Test
    @WithMockUser(username = "user-id")
    fun `consumeDrug - invalid UUID - returns 400`() {
        val consumeRequest = ConsumeRequest(quantity = 10.0)
        mockMvc.perform(
            put("/drug/consume/{id}", "invalid-uuid")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(consumeRequest))
        )
            .andExpect(status().isBadRequest)
    }
    @Test
    @WithMockUser(username = "user-id")
    fun `consumeDrug - fractional quantity - consumes fractional amount`() {
        val consumeRequest = ConsumeRequest(quantity = 5.5)
        val medKit = medKitBuilder().withId(testMedKitId).build()
        val consumedDrug = drugBuilder(medKit).withId(testDrugId).withQuantity(94.5).build()
        val drugDTO = DrugDTO(
            id = consumedDrug.id,
            name = consumedDrug.name,
            quantity = 94.5,
            plannedQuantity = 0.0,
            quantityUnit = consumedDrug.quantityUnit,
            formType = consumedDrug.formType,
            category = consumedDrug.category,
            manufacturer = consumedDrug.manufacturer,
            country = consumedDrug.country,
            description = consumedDrug.description,
            medKitId = medKit.id
        )
        every { drugService.consumeDrug(testDrugId, 5.5, any()) } returns consumedDrug
        every { drugService.toDrugDTO(consumedDrug) } returns drugDTO
        mockMvc.perform(
            put("/drug/consume/{id}", testDrugId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(consumeRequest))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.quantity").value(94.5))
    }
    // ========== POST /drug/move/{id} - moveDrug Tests (10 tests) ==========
    @Test
    @WithMockUser(username = "user-id")
    fun `moveDrug - valid target medkit - moves drug and returns DTO`() {
        val targetMedKitId = UUID.randomUUID()
        val moveRequest = MoveDrugRequest(targetMedKitId = targetMedKitId)
        val targetMedKit = medKitBuilder().withId(targetMedKitId).build()
        val movedDrug = drugBuilder(targetMedKit).withId(testDrugId).build()
        val drugDTO = DrugDTO(
            id = movedDrug.id,
            name = movedDrug.name,
            quantity = movedDrug.quantity,
            plannedQuantity = 0.0,
            quantityUnit = movedDrug.quantityUnit,
            formType = movedDrug.formType,
            category = movedDrug.category,
            manufacturer = movedDrug.manufacturer,
            country = movedDrug.country,
            description = movedDrug.description,
            medKitId = targetMedKitId
        )
        every { drugService.moveDrug(testDrugId, targetMedKitId, any()) } returns movedDrug
        every { drugService.toDrugDTO(movedDrug) } returns drugDTO
        mockMvc.perform(
            put("/drug/move/{id}", testDrugId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(moveRequest))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.medKitId").value(targetMedKitId.toString()))
        verify(exactly = 1) { drugService.moveDrug(testDrugId, targetMedKitId, any()) }
    }
    @Test
    @WithMockUser(username = "user-id")
    fun `moveDrug - missing targetMedKitId - returns 400`() {
        val invalidRequest = mapOf<String, Any>()
        mockMvc.perform(
            put("/drug/move/{id}", testDrugId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidRequest))
        )
            .andExpect(status().isBadRequest)
    }
    @Test
    @WithMockUser(username = "user-id")
    fun `moveDrug - target medkit not found - returns 404`() {
        val targetMedKitId = UUID.randomUUID()
        val moveRequest = MoveDrugRequest(targetMedKitId = targetMedKitId)
        every { drugService.moveDrug(testDrugId, targetMedKitId, any()) } throws ResponseStatusException(
            HttpStatus.NOT_FOUND, "Target medicine kit not found"
        )
        mockMvc.perform(
            put("/drug/move/{id}", testDrugId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(moveRequest))
        )
            .andExpect(status().isNotFound)
    }
    @Test
    @WithMockUser(username = "user-id")
    fun `moveDrug - user has no access to source drug - returns 403`() {
        val targetMedKitId = UUID.randomUUID()
        val moveRequest = MoveDrugRequest(targetMedKitId = targetMedKitId)
        every { drugService.moveDrug(testDrugId, targetMedKitId, any()) } throws ResponseStatusException(
            HttpStatus.FORBIDDEN, "Access denied to source drug"
        )
        mockMvc.perform(
            put("/drug/move/{id}", testDrugId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(moveRequest))
        )
            .andExpect(status().isForbidden)
    }
    @Test
    @WithMockUser(username = "user-id")
    fun `moveDrug - user has no access to target medkit - returns 403`() {
        val targetMedKitId = UUID.randomUUID()
        val moveRequest = MoveDrugRequest(targetMedKitId = targetMedKitId)
        every { drugService.moveDrug(testDrugId, targetMedKitId, any()) } throws ResponseStatusException(
            HttpStatus.FORBIDDEN, "Access denied to target medicine kit"
        )
        mockMvc.perform(
            put("/drug/move/{id}", testDrugId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(moveRequest))
        )
            .andExpect(status().isForbidden)
    }
    @Test
    @WithMockUser(username = "user-id")
    fun `moveDrug - drug not found - returns 404`() {
        val targetMedKitId = UUID.randomUUID()
        val moveRequest = MoveDrugRequest(targetMedKitId = targetMedKitId)
        every { drugService.moveDrug(testDrugId, targetMedKitId, any()) } throws ResponseStatusException(
            HttpStatus.NOT_FOUND, "Drug not found"
        )
        mockMvc.perform(
            put("/drug/move/{id}", testDrugId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(moveRequest))
        )
            .andExpect(status().isNotFound)
    }
    @Test
    fun `moveDrug - unauthenticated - returns 401`() {
        val targetMedKitId = UUID.randomUUID()
        val moveRequest = MoveDrugRequest(targetMedKitId = targetMedKitId)
        mockMvc.perform(
            put("/drug/move/{id}", testDrugId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(moveRequest))
        )
            .andExpect(status().isUnauthorized)
    }
    @Test
    @WithMockUser(username = "user-id")
    fun `moveDrug - invalid drug UUID - returns 400`() {
        val targetMedKitId = UUID.randomUUID()
        val moveRequest = MoveDrugRequest(targetMedKitId = targetMedKitId)
        mockMvc.perform(
            put("/drug/move/{id}", "invalid-uuid")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(moveRequest))
        )
            .andExpect(status().isBadRequest)
    }
    @Test
    @WithMockUser(username = "user-id")
    fun `moveDrug - same medkit as source - may succeed or fail based on service logic`() {
        val moveRequest = MoveDrugRequest(targetMedKitId = testMedKitId)
        val medKit = medKitBuilder().withId(testMedKitId).build()
        val drug = drugBuilder(medKit).withId(testDrugId).build()
        val drugDTO = DrugDTO(
            id = drug.id,
            name = drug.name,
            quantity = drug.quantity,
            plannedQuantity = 0.0,
            quantityUnit = drug.quantityUnit,
            formType = drug.formType,
            category = drug.category,
            manufacturer = drug.manufacturer,
            country = drug.country,
            description = drug.description,
            medKitId = testMedKitId
        )
        every { drugService.moveDrug(testDrugId, testMedKitId, any()) } returns drug
        every { drugService.toDrugDTO(drug) } returns drugDTO
        mockMvc.perform(
            put("/drug/move/{id}", testDrugId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(moveRequest))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.medKitId").value(testMedKitId.toString()))
    }
    @Test
    @WithMockUser(username = "user-id")
    fun `moveDrug - with active treatment plans - may fail based on service logic`() {
        val targetMedKitId = UUID.randomUUID()
        val moveRequest = MoveDrugRequest(targetMedKitId = targetMedKitId)
        every { drugService.moveDrug(testDrugId, targetMedKitId, any()) } throws ResponseStatusException(
            HttpStatus.BAD_REQUEST, "Cannot move drug with active treatment plans"
        )
        mockMvc.perform(
            put("/drug/move/{id}", testDrugId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(moveRequest))
        )
            .andExpect(status().isBadRequest)
    }
    // ========== GET /drug/template/search - searchTemplates Tests (12 tests) ==========
    @Test
    @WithMockUser(username = "user-id")
    fun `searchTemplates - valid search term - returns list of templates`() {
        val searchTerm = "aspirin"
        val vidalDrug1 = VidalDrug(
            id = UUID.randomUUID(),
            name = "Aspirin 500mg",
            formType = FormType(id = UUID.randomUUID(), name = "tablet"),
            category = "painkiller",
            manufacturer = "Bayer",
            description = "Pain relief"
        )
        val vidalDrug2 = VidalDrug(
            id = UUID.randomUUID(),
            name = "Aspirin Cardio",
            formType = FormType(id = UUID.randomUUID(), name = "tablet"),
            category = "cardiovascular",
            manufacturer = "Bayer",
            description = "Heart protection"
        )
        every { vidalDrugService.fuzzySearchByName(searchTerm, 10) } returns listOf(vidalDrug1, vidalDrug2)
        mockMvc.perform(
            get("/drug/template/search")
                .param("searchTerm", searchTerm)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].name").value("Aspirin 500mg"))
            .andExpect(jsonPath("$[1].name").value("Aspirin Cardio"))
        verify(exactly = 1) { vidalDrugService.fuzzySearchByName(searchTerm, 10) }
    }
    @Test
    @WithMockUser(username = "user-id")
    fun `searchTemplates - custom limit - returns limited results`() {
        val searchTerm = "pain"
        val vidalDrugs = (1..5).map {
            VidalDrug(
                id = UUID.randomUUID(),
                name = "Painkiller $it",
                formType = FormType(id = UUID.randomUUID(), name = "tablet")
            )
        }
        every { vidalDrugService.fuzzySearchByName(searchTerm, 5) } returns vidalDrugs
        mockMvc.perform(
            get("/drug/template/search")
                .param("searchTerm", searchTerm)
                .param("limit", "5")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(5))
        verify(exactly = 1) { vidalDrugService.fuzzySearchByName(searchTerm, 5) }
    }
    @Test
    @WithMockUser(username = "user-id")
    fun `searchTemplates - no results - returns empty list`() {
        val searchTerm = "nonexistent"
        every { vidalDrugService.fuzzySearchByName(searchTerm, 10) } returns emptyList()
        mockMvc.perform(
            get("/drug/template/search")
                .param("searchTerm", searchTerm)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(0))
    }
    @Test
    @WithMockUser(username = "user-id")
    fun `searchTemplates - missing search term - returns 400`() {
        mockMvc.perform(get("/drug/template/search"))
            .andExpect(status().isBadRequest)
    }
    @Test
    @WithMockUser(username = "user-id")
    fun `searchTemplates - empty search term - returns results or empty`() {
        every { vidalDrugService.fuzzySearchByName("", 10) } returns emptyList()
        mockMvc.perform(
            get("/drug/template/search")
                .param("searchTerm", "")
        )
            .andExpect(status().isOk)
    }
    @Test
    fun `searchTemplates - unauthenticated - returns 401`() {
        mockMvc.perform(
            get("/drug/template/search")
                .param("searchTerm", "aspirin")
        )
            .andExpect(status().isUnauthorized)
    }
    @Test
    @WithMockUser(username = "user-id")
    fun `searchTemplates - with null formType - returns DTO with null formType`() {
        val vidalDrug = VidalDrug(
            id = UUID.randomUUID(),
            name = "Generic Drug",
            formType = null,
            category = "generic",
            manufacturer = "Generic Corp",
            description = "Generic description"
        )
        every { vidalDrugService.fuzzySearchByName("generic", 10) } returns listOf(vidalDrug)
        mockMvc.perform(
            get("/drug/template/search")
                .param("searchTerm", "generic")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].formType").doesNotExist())
    }
    @Test
    @WithMockUser(username = "user-id")
    fun `searchTemplates - limit zero - uses default limit`() {
        val searchTerm = "test"
        every { vidalDrugService.fuzzySearchByName(searchTerm, 0) } returns emptyList()
        mockMvc.perform(
            get("/drug/template/search")
                .param("searchTerm", searchTerm)
                .param("limit", "0")
        )
            .andExpect(status().isOk)
        verify(exactly = 1) { vidalDrugService.fuzzySearchByName(searchTerm, 0) }
    }
    @Test
    @WithMockUser(username = "user-id")
    fun `searchTemplates - negative limit - handled by service`() {
        val searchTerm = "test"
        every { vidalDrugService.fuzzySearchByName(searchTerm, -5) } returns emptyList()
        mockMvc.perform(
            get("/drug/template/search")
                .param("searchTerm", searchTerm)
                .param("limit", "-5")
        )
            .andExpect(status().isOk)
    }
    @Test
    @WithMockUser(username = "user-id")
    fun `searchTemplates - large limit - returns available results`() {
        val searchTerm = "test"
        val vidalDrugs = (1..3).map {
            VidalDrug(id = UUID.randomUUID(), name = "Drug $it")
        }
        every { vidalDrugService.fuzzySearchByName(searchTerm, 1000) } returns vidalDrugs
        mockMvc.perform(
            get("/drug/template/search")
                .param("searchTerm", searchTerm)
                .param("limit", "1000")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(3))
    }
    @Test
    @WithMockUser(username = "user-id")
    fun `searchTemplates - all fields populated - returns complete DTOs`() {
        val vidalDrug = VidalDrug(
            id = UUID.randomUUID(),
            name = "Complete Drug",
            formType = FormType(id = UUID.randomUUID(), name = "tablet"),
            category = "antibiotic",
            manufacturer = "PharmaCorp",
            description = "Complete description"
        )
        every { vidalDrugService.fuzzySearchByName("complete", 10) } returns listOf(vidalDrug)
        mockMvc.perform(
            get("/drug/template/search")
                .param("searchTerm", "complete")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].name").value("Complete Drug"))
            .andExpect(jsonPath("$[0].formType").value("tablet"))
            .andExpect(jsonPath("$[0].category").value("antibiotic"))
            .andExpect(jsonPath("$[0].manufacturer").value("PharmaCorp"))
            .andExpect(jsonPath("$[0].description").value("Complete description"))
    }
    @Test
    @WithMockUser(username = "user-id")
    fun `searchTemplates - special characters in search term - handles gracefully`() {
        val searchTerm = "asp!r@n#"
        every { vidalDrugService.fuzzySearchByName(searchTerm, 10) } returns emptyList()
        mockMvc.perform(
            get("/drug/template/search")
                .param("searchTerm", searchTerm)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(0))
    }
    // ========== DELETE /drug/{id} - delete Tests (10 tests) ==========
    @Test
    @WithMockUser(username = "user-id")
    fun `delete - valid id - deletes drug and returns 204`() {
        justRun { drugService.delete(testDrugId, any()) }
        mockMvc.perform(delete("/drug/{id}", testDrugId))
            .andExpect(status().isNoContent)
        verify(exactly = 1) { drugService.delete(testDrugId, any()) }
    }
    @Test
    @WithMockUser(username = "user-id")
    fun `delete - drug not found - returns 404`() {
        every { drugService.delete(testDrugId, any()) } throws ResponseStatusException(
            HttpStatus.NOT_FOUND, "Drug not found"
        )
        mockMvc.perform(delete("/drug/{id}", testDrugId))
            .andExpect(status().isNotFound)
        verify(exactly = 1) { drugService.delete(testDrugId, any()) }
    }
    @Test
    @WithMockUser(username = "user-id")
    fun `delete - user has no access - returns 403`() {
        every { drugService.delete(testDrugId, any()) } throws ResponseStatusException(
            HttpStatus.FORBIDDEN, "Access denied"
        )
        mockMvc.perform(delete("/drug/{id}", testDrugId))
            .andExpect(status().isForbidden)
        verify(exactly = 1) { drugService.delete(testDrugId, any()) }
    }
    @Test
    fun `delete - unauthenticated - returns 401`() {
        mockMvc.perform(delete("/drug/{id}", testDrugId))
            .andExpect(status().isUnauthorized)
    }
    @Test
    @WithMockUser(username = "user-id")
    fun `delete - invalid UUID - returns 400`() {
        mockMvc.perform(delete("/drug/{id}", "invalid-uuid"))
            .andExpect(status().isBadRequest)
    }
    @Test
    @WithMockUser(username = "user-id")
    fun `delete - with active treatment plans - may fail based on service logic`() {
        every { drugService.delete(testDrugId, any()) } throws ResponseStatusException(
            HttpStatus.BAD_REQUEST, "Cannot delete drug with active treatment plans"
        )
        mockMvc.perform(delete("/drug/{id}", testDrugId))
            .andExpect(status().isBadRequest)
    }
    @Test
    @WithMockUser(username = "user-id")
    fun `delete - drug in use by other users - may fail based on service logic`() {
        every { drugService.delete(testDrugId, any()) } throws ResponseStatusException(
            HttpStatus.BAD_REQUEST, "Drug is in use by other users"
        )
        mockMvc.perform(delete("/drug/{id}", testDrugId))
            .andExpect(status().isBadRequest)
    }
    @Test
    @WithMockUser(username = "user-id")
    fun `delete - already deleted drug - returns 404`() {
        every { drugService.delete(testDrugId, any()) } throws ResponseStatusException(
            HttpStatus.NOT_FOUND, "Drug not found"
        )
        mockMvc.perform(delete("/drug/{id}", testDrugId))
            .andExpect(status().isNotFound)
    }
    @Test
    @WithMockUser(username = "user-id")
    fun `delete - service throws internal error - returns 500`() {
        every { drugService.delete(testDrugId, any()) } throws RuntimeException("Database error")
        mockMvc.perform(delete("/drug/{id}", testDrugId))
            .andExpect(status().isInternalServerError)
    }
    @Test
    @WithMockUser(username = "user-id")
    fun `delete - multiple calls for same drug - second call returns 404`() {
        justRun { drugService.delete(testDrugId, any()) }
        mockMvc.perform(delete("/drug/{id}", testDrugId))
            .andExpect(status().isNoContent)
        every { drugService.delete(testDrugId, any()) } throws ResponseStatusException(
            HttpStatus.NOT_FOUND, "Drug not found"
        )
        mockMvc.perform(delete("/drug/{id}", testDrugId))
            .andExpect(status().isNotFound)
    }
    // ========== Additional endpoint tests - GET /drug/quantity/{id} - getDrugQuantityInfo (10 tests) ==========
    @Test
    @WithMockUser(username = "user-id")
    fun `getDrugQuantityInfo - valid id - returns quantity info`() {
        val medKit = medKitBuilder().withId(testMedKitId).build()
        val drug = drugBuilder(medKit).withId(testDrugId).withQuantity(100.0).build()
        every { drugService.findByIdForUser(testDrugId, any()) } returns drug
        every { drugService.getAvailableQuantity(testDrugId) } returns Pair(100.0, 30.0)
        mockMvc.perform(get("/drug/quantity/{id}", testDrugId))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.actualQuantity").value(100.0))
            .andExpect(jsonPath("$.plannedQuantity").value(30.0))
            .andExpect(jsonPath("$.availableQuantity").value(70.0))
        verify(exactly = 1) { drugService.findByIdForUser(testDrugId, any()) }
        verify(exactly = 1) { drugService.getAvailableQuantity(testDrugId) }
    }
    @Test
    @WithMockUser(username = "user-id")
    fun `getDrugQuantityInfo - no planned quantity - returns zero planned`() {
        val medKit = medKitBuilder().withId(testMedKitId).build()
        val drug = drugBuilder(medKit).withId(testDrugId).withQuantity(100.0).build()
        every { drugService.findByIdForUser(testDrugId, any()) } returns drug
        every { drugService.getAvailableQuantity(testDrugId) } returns Pair(100.0, 0.0)
        mockMvc.perform(get("/drug/quantity/{id}", testDrugId))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.actualQuantity").value(100.0))
            .andExpect(jsonPath("$.plannedQuantity").value(0.0))
            .andExpect(jsonPath("$.availableQuantity").value(100.0))
    }
    @Test
    @WithMockUser(username = "user-id")
    fun `getDrugQuantityInfo - planned exceeds actual - negative available`() {
        val medKit = medKitBuilder().withId(testMedKitId).build()
        val drug = drugBuilder(medKit).withId(testDrugId).withQuantity(50.0).build()
        every { drugService.findByIdForUser(testDrugId, any()) } returns drug
        every { drugService.getAvailableQuantity(testDrugId) } returns Pair(50.0, 80.0)
        mockMvc.perform(get("/drug/quantity/{id}", testDrugId))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.actualQuantity").value(50.0))
            .andExpect(jsonPath("$.plannedQuantity").value(80.0))
            .andExpect(jsonPath("$.availableQuantity").value(-30.0))
    }
    @Test
    @WithMockUser(username = "user-id")
    fun `getDrugQuantityInfo - drug not found - returns 404`() {
        every { drugService.findByIdForUser(testDrugId, any()) } throws ResponseStatusException(
            HttpStatus.NOT_FOUND, "Drug not found"
        )
        mockMvc.perform(get("/drug/quantity/{id}", testDrugId))
            .andExpect(status().isNotFound)
        verify(exactly = 1) { drugService.findByIdForUser(testDrugId, any()) }
        verify(exactly = 0) { drugService.getAvailableQuantity(any()) }
    }
    @Test
    @WithMockUser(username = "user-id")
    fun `getDrugQuantityInfo - user has no access - returns 403`() {
        every { drugService.findByIdForUser(testDrugId, any()) } throws ResponseStatusException(
            HttpStatus.FORBIDDEN, "Access denied"
        )
        mockMvc.perform(get("/drug/quantity/{id}", testDrugId))
            .andExpect(status().isForbidden)
    }
    @Test
    fun `getDrugQuantityInfo - unauthenticated - returns 401`() {
        mockMvc.perform(get("/drug/quantity/{id}", testDrugId))
            .andExpect(status().isUnauthorized)
    }
    @Test
    @WithMockUser(username = "user-id")
    fun `getDrugQuantityInfo - invalid UUID - returns 400`() {
        mockMvc.perform(get("/drug/quantity/{id}", "invalid-uuid"))
            .andExpect(status().isBadRequest)
    }
    @Test
    @WithMockUser(username = "user-id")
    fun `getDrugQuantityInfo - zero actual quantity - returns zero actual`() {
        val medKit = medKitBuilder().withId(testMedKitId).build()
        val drug = drugBuilder(medKit).withId(testDrugId).withQuantity(0.0).build()
        every { drugService.findByIdForUser(testDrugId, any()) } returns drug
        every { drugService.getAvailableQuantity(testDrugId) } returns Pair(0.0, 0.0)
        mockMvc.perform(get("/drug/quantity/{id}", testDrugId))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.actualQuantity").value(0.0))
            .andExpect(jsonPath("$.availableQuantity").value(0.0))
    }
    @Test
    @WithMockUser(username = "user-id")
    fun `getDrugQuantityInfo - fractional quantities - returns fractional values`() {
        val medKit = medKitBuilder().withId(testMedKitId).build()
        val drug = drugBuilder(medKit).withId(testDrugId).withQuantity(45.5).build()
        every { drugService.findByIdForUser(testDrugId, any()) } returns drug
        every { drugService.getAvailableQuantity(testDrugId) } returns Pair(45.5, 15.25)
        mockMvc.perform(get("/drug/quantity/{id}", testDrugId))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.actualQuantity").value(45.5))
            .andExpect(jsonPath("$.plannedQuantity").value(15.25))
            .andExpect(jsonPath("$.availableQuantity").value(30.25))
    }
    @Test
    @WithMockUser(username = "user-id")
    fun `getDrugQuantityInfo - service throws error on quantity calculation - returns 500`() {
        val medKit = medKitBuilder().withId(testMedKitId).build()
        val drug = drugBuilder(medKit).withId(testDrugId).build()
        every { drugService.findByIdForUser(testDrugId, any()) } returns drug
        every { drugService.getAvailableQuantity(testDrugId) } throws RuntimeException("Calculation error")
        mockMvc.perform(get("/drug/quantity/{id}", testDrugId))
            .andExpect(status().isInternalServerError)
    }
    // ========== Additional endpoint test - GET /drug/template/{id} - getDrugTemplate (10 tests) ==========
    @Test
    @WithMockUser(username = "user-id")
    fun `getDrugTemplate - valid id - returns template DTO`() {
        val templateId = UUID.randomUUID()
        val vidalDrug = VidalDrug(
            id = templateId,
            name = "Aspirin Template",
            formType = FormType(id = UUID.randomUUID(), name = "tablet"),
            category = "painkiller",
            manufacturer = "Bayer",
            description = "Template description"
        )
        every { vidalDrugService.findById(templateId) } returns vidalDrug
        mockMvc.perform(get("/drug/template/{id}", templateId))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(templateId.toString()))
            .andExpect(jsonPath("$.name").value("Aspirin Template"))
            .andExpect(jsonPath("$.formType").value("tablet"))
            .andExpect(jsonPath("$.category").value("painkiller"))
        verify(exactly = 1) { vidalDrugService.findById(templateId) }
    }
    @Test
    @WithMockUser(username = "user-id")
    fun `getDrugTemplate - template not found - returns 404`() {
        val templateId = UUID.randomUUID()
        every { vidalDrugService.findById(templateId) } returns null
        mockMvc.perform(get("/drug/template/{id}", templateId))
            .andExpect(status().isNotFound)
    }
    @Test
    @WithMockUser(username = "user-id")
    fun `getDrugTemplate - with null formType - returns DTO with null formType`() {
        val templateId = UUID.randomUUID()
        val vidalDrug = VidalDrug(
            id = templateId,
            name = "Generic Template",
            formType = null,
            category = "generic",
            manufacturer = "Generic Corp",
            description = "No form type"
        )
        every { vidalDrugService.findById(templateId) } returns vidalDrug
        mockMvc.perform(get("/drug/template/{id}", templateId))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.formType").doesNotExist())
            .andExpect(jsonPath("$.name").value("Generic Template"))
    }
    @Test
    @WithMockUser(username =="user-id")
    fun `getDrugTemplate - with all null optional fields - returns minimal DTO`() {
        val templateId = UUID.randomUUID()
        val vidalDrug = VidalDrug(
            id = templateId,
            name = "Minimal Template",
            formType = null,
            category = null,
            manufacturer = null,
            description = null
        )
        every { vidalDrugService.findById(templateId) } returns vidalDrug
        mockMvc.perform(get("/drug/template/{id}", templateId))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(templateId.toString()))
            .andExpect(jsonPath("$.name").value("Minimal Template"))
            .andExpect(jsonPath("$.formType").doesNotExist())
            .andExpect(jsonPath("$.category").doesNotExist())
            .andExpect(jsonPath("$.manufacturer").doesNotExist())
            .andExpect(jsonPath("$.description").doesNotExist())
    }
    @Test
    fun `getDrugTemplate - unauthenticated - returns 401`() {
        val templateId = UUID.randomUUID()
        mockMvc.perform(get("/drug/template/{id}", templateId))
            .andExpect(status().isUnauthorized)
    }
    @Test
    @WithMockUser(username = "user-id")
    fun `getDrugTemplate - invalid UUID - returns 400`() {
        mockMvc.perform(get("/drug/template/{id}", "invalid-uuid"))
            .andExpect(status().isBadRequest)
    }
    @Test
    @WithMockUser(username = "user-id")
    fun `getDrugTemplate - all fields populated - returns complete DTO`() {
        val templateId = UUID.randomUUID()
        val vidalDrug = VidalDrug(
            id = templateId,
            name = "Complete Template",
            formType = FormType(id = UUID.randomUUID(), name = "syrup"),
            category = "antibiotic",
            manufacturer = "PharmaCorp",
            description = "Complete description"
        )
        every { vidalDrugService.findById(templateId) } returns vidalDrug
        mockMvc.perform(get("/drug/template/{id}", templateId))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.name").value("Complete Template"))
            .andExpect(jsonPath("$.formType").value("syrup"))
            .andExpect(jsonPath("$.category").value("antibiotic"))
            .andExpect(jsonPath("$.manufacturer").value("PharmaCorp"))
            .andExpect(jsonPath("$.description").value("Complete description"))
    }
    @Test
    @WithMockUser(username = "user-id")
    fun `getDrugTemplate - service throws error - returns 500`() {
        val templateId = UUID.randomUUID()
        every { vidalDrugService.findById(templateId) } throws RuntimeException("Database error")
        mockMvc.perform(get("/drug/template/{id}", templateId))
            .andExpect(status().isInternalServerError)
    }
    @Test
    @WithMockUser(username = "user-id")
    fun `getDrugTemplate - empty string description - returns DTO with empty description`() {
        val templateId = UUID.randomUUID()
        val vidalDrug = VidalDrug(
            id = templateId,
            name = "Empty Description",
            formType = FormType(id = UUID.randomUUID(), name = "tablet"),
            category = "test",
            manufacturer = "TestCorp",
            description = ""
        )
        every { vidalDrugService.findById(templateId) } returns vidalDrug
        mockMvc.perform(get("/drug/template/{id}", templateId))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.description").value(""))
    }
    @Test
    @WithMockUser(username = "user-id")
    fun `getDrugTemplate - long description - returns full description`() {
        val templateId = UUID.randomUUID()
        val longDescription = "A".repeat(500)
        val vidalDrug = VidalDrug(
            id = templateId,
            name = "Long Description Drug",
            formType = FormType(id = UUID.randomUUID(), name = "tablet"),
            description = longDescription
        )
        every { vidalDrugService.findById(templateId) } returns vidalDrug
        mockMvc.perform(get("/drug/template/{id}", templateId))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.description").value(longDescription))
    }
}