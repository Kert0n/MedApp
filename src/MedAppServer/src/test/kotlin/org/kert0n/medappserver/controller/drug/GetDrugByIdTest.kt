package org.kert0n.medappserver.controller.drug

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.kert0n.medappserver.controller.DrugController
import org.kert0n.medappserver.controller.TestSecurityConfig
import org.kert0n.medappserver.db.model.*
import org.kert0n.medappserver.services.DrugService
import org.kert0n.medappserver.services.UsingService
import org.kert0n.medappserver.services.VidalDrugService
import org.kert0n.medappserver.testutil.drugBuilder
import org.kert0n.medappserver.testutil.medKitBuilder
import org.kert0n.medappserver.testutil.userBuilder
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.web.server.ResponseStatusException
import java.util.*

@WebMvcTest(DrugController::class)
@Import(TestSecurityConfig::class)
class GetDrugByIdTest {

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

    @Test
    fun `GET drug by ID - success - returns drug when user has access`() {
        // Arrange
        val userId = UUID.randomUUID()
        val drugId = UUID.randomUUID()
        val medKit = medKitBuilder().build()
        val drug = drugBuilder(medKit)
            .withId(drugId)
            .withName("Aspirin")
            .withQuantity(50.0)
            .build()
        
        val drugDTO = DrugDTO(
            id = drug.id,
            name = drug.name,
            quantity = drug.quantity,
            quantityUnit = drug.quantityUnit,
            medKitId = medKit.id,
            formType = drug.formType,
            category = drug.category,
            manufacturer = drug.manufacturer,
            country = drug.country,
            description = drug.description
        )

        whenever(drugService.findByIdForUser(drugId, userId)).thenReturn(drug)
        whenever(drugService.toDrugDTO(drug)).thenReturn(drugDTO)

        // Act & Assert
        mockMvc.perform(
            get("/drug/{id}", drugId)
                .with(user(userId.toString()))
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.id").value(drugId.toString()))
            .andExpect(jsonPath("$.name").value("Aspirin"))
            .andExpect(jsonPath("$.quantity").value(50.0))
    }

    @Test
    fun `GET drug by ID - not found - returns 404 when drug doesn't exist`() {
        // Arrange
        val userId = UUID.randomUUID()
        val drugId = UUID.randomUUID()

        whenever(drugService.findByIdForUser(drugId, userId))
            .thenThrow(ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Drug not found"))

        // Act & Assert
        mockMvc.perform(
            get("/drug/{id}", drugId)
                .with(user(userId.toString()))
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isNotFound)
    }

    @Test
    fun `GET drug by ID - forbidden - returns 403 when user doesn't have access`() {
        // Arrange
        val userId = UUID.randomUUID()
        val drugId = UUID.randomUUID()

        whenever(drugService.findByIdForUser(drugId, userId))
            .thenThrow(ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN, "Access denied"))

        // Act & Assert
        mockMvc.perform(
            get("/drug/{id}", drugId)
                .with(user(userId.toString()))
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isForbidden)
    }

    @Test
    fun `GET drug by ID - invalid ID format - returns 400`() {
        // Arrange
        val userId = UUID.randomUUID()
        val invalidId = "not-a-uuid"

        // Act & Assert
        mockMvc.perform(
            get("/drug/{id}", invalidId)
                .with(user(userId.toString()))
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `GET drug by ID - drug with all fields populated - returns complete data`() {
        // Arrange
        val userId = UUID.randomUUID()
        val drugId = UUID.randomUUID()
        val medKit = medKitBuilder().build()
        val drug = drugBuilder(medKit)
            .withId(drugId)
            .withName("Paracetamol 500mg")
            .withQuantity(250.5)
            .withQuantityUnit("tablets")
            .withFormType("tablet")
            .withCategory("painkiller")
            .withManufacturer("PharmaCorp")
            .withCountry("Germany")
            .withDescription("For headaches and fever")
            .build()
        
        val drugDTO = DrugDTO(
            id = drug.id,
            name = drug.name,
            quantity = drug.quantity,
            quantityUnit = drug.quantityUnit,
            medKitId = medKit.id,
            formType = drug.formType,
            category = drug.category,
            manufacturer = drug.manufacturer,
            country = drug.country,
            description = drug.description
        )

        whenever(drugService.findByIdForUser(drugId, userId)).thenReturn(drug)
        whenever(drugService.toDrugDTO(drug)).thenReturn(drugDTO)

        // Act & Assert
        mockMvc.perform(
            get("/drug/{id}", drugId)
                .with(user(userId.toString()))
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(drugId.toString()))
            .andExpect(jsonPath("$.name").value("Paracetamol 500mg"))
            .andExpect(jsonPath("$.quantity").value(250.5))
            .andExpect(jsonPath("$.quantityUnit").value("tablets"))
            .andExpect(jsonPath("$.formType").value("tablet"))
            .andExpect(jsonPath("$.category").value("painkiller"))
            .andExpect(jsonPath("$.manufacturer").value("PharmaCorp"))
            .andExpect(jsonPath("$.country").value("Germany"))
            .andExpect(jsonPath("$.description").value("For headaches and fever"))
    }

    @Test
    fun `GET drug by ID - drug with minimal fields - returns data with nulls`() {
        // Arrange
        val userId = UUID.randomUUID()
        val drugId = UUID.randomUUID()
        val medKit = medKitBuilder().build()
        val drug = drugBuilder(medKit)
            .withId(drugId)
            .withName("Generic Med")
            .withQuantity(10.0)
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
            quantityUnit = drug.quantityUnit,
            medKitId = medKit.id,
            formType = null,
            category = null,
            manufacturer = null,
            country = null,
            description = null
        )

        whenever(drugService.findByIdForUser(drugId, userId)).thenReturn(drug)
        whenever(drugService.toDrugDTO(drug)).thenReturn(drugDTO)

        // Act & Assert
        mockMvc.perform(
            get("/drug/{id}", drugId)
                .with(user(userId.toString()))
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(drugId.toString()))
            .andExpect(jsonPath("$.name").value("Generic Med"))
            .andExpect(jsonPath("$.quantity").value(10.0))
            .andExpect(jsonPath("$.formType").doesNotExist())
            .andExpect(jsonPath("$.category").doesNotExist())
            .andExpect(jsonPath("$.manufacturer").doesNotExist())
    }
}
