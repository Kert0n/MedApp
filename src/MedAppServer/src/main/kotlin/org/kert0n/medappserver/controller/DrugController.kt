package org.kert0n.medappserver.controller

import jakarta.validation.Valid
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotNull
import org.kert0n.medappserver.db.model.*
import org.kert0n.medappserver.services.*
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import java.util.*

@RestController
@RequestMapping("/drug")
class DrugController(
    private val drugService: DrugService,
    private val usingService: UsingService,
    private val vidalDrugService: VidalDrugService
) {
    
    private val logger = LoggerFactory.getLogger(DrugController::class.java)

    @GetMapping("/{id}")
    fun getDrug(authentication: Authentication, @PathVariable id: UUID): DrugDTO {
        logger.debug("GET /drug/{} by user {}", id, authentication.userId)
        val drug = drugService.findByIdForUser(id, authentication.userId)
        return drugService.toDrugDTO(drug)
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createDrug(authentication: Authentication, @Valid @RequestBody drugDTO: DrugCreateDTO): DrugDTO {
        logger.debug("POST /drug by user {}: {}", authentication.userId, drugDTO.name)
        val drug = drugService.create(drugDTO, authentication.userId)
        return drugService.toDrugDTO(drug)
    }

    @PutMapping("/{id}")
    fun updateDrug(
        authentication: Authentication,
        @PathVariable id: UUID,
        @Valid @RequestBody updateDTO: DrugUpdateDTO
    ): DrugDTO {
        logger.debug("PUT /drug/{} by user {}", id, authentication.userId)
        val drug = drugService.update(id, updateDTO, authentication.userId)
        return drugService.toDrugDTO(drug)
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteDrug(authentication: Authentication, @PathVariable id: UUID) {
        logger.debug("DELETE /drug/{} by user {}", id, authentication.userId)
        drugService.delete(id, authentication.userId)
    }

    @GetMapping("/quantity/{id}")
    fun getDrugQuantityInfo(authentication: Authentication, @PathVariable id: UUID): QuantityInfo {
        logger.debug("GET /drug/quantity/{} by user {}", id, authentication.userId)
        // Verify user has access
        drugService.findByIdForUser(id, authentication.userId)
        val (actual, planned) = drugService.getAvailableQuantity(id)
        return QuantityInfo(actual, planned, actual - planned)
    }

    @PutMapping("/consume/{id}")
    fun consumeDrug(
        authentication: Authentication,
        @PathVariable id: UUID,
        @Valid @RequestBody consumeRequest: ConsumeRequest
    ): DrugDTO {
        logger.debug("PUT /drug/consume/{} by user {}, quantity: {}", id, authentication.userId, consumeRequest.quantity)
        val drug = drugService.consumeDrug(id, consumeRequest.quantity, authentication.userId)
        return drugService.toDrugDTO(drug)
    }

    @PutMapping("/move/{id}")
    fun moveDrug(
        authentication: Authentication,
        @PathVariable id: UUID,
        @Valid @RequestBody moveRequest: MoveDrugRequest
    ): DrugDTO {
        logger.debug("PUT /drug/move/{} to medkit {} by user {}", id, moveRequest.targetMedKitId, authentication.userId)
        val drug = drugService.moveDrug(id, moveRequest.targetMedKitId, authentication.userId)
        return drugService.toDrugDTO(drug)
    }

    @GetMapping("/template/search")
    fun searchDrugTemplates(
        authentication: Authentication,
        @RequestParam searchTerm: String,
        @RequestParam(defaultValue = "10") limit: Int
    ): List<DrugTemplateDTO> {
        logger.debug("GET /drug/template/search?searchTerm={}&limit={} by user {}", searchTerm, limit, authentication.userId)
        return vidalDrugService.fuzzySearchByName(searchTerm, limit).map { vd ->
            DrugTemplateDTO(
                id = vd.id,
                name = vd.name,
                formType = vd.formType?.name,
                category = vd.category,
                manufacturer = vd.manufacturer,
                description = vd.description
            )
        }
    }

    @GetMapping("/template/{id}")
    fun getDrugTemplate(authentication: Authentication, @PathVariable id: UUID): DrugTemplateDTO {
        logger.debug("GET /drug/template/{} by user {}", id, authentication.userId)
        val vd = vidalDrugService.findById(id) ?: throw org.springframework.web.server.ResponseStatusException(
            HttpStatus.NOT_FOUND, "Drug template not found"
        )
        return DrugTemplateDTO(
            id = vd.id,
            name = vd.name,
            formType = vd.formType?.name,
            category = vd.category,
            manufacturer = vd.manufacturer,
            description = vd.description
        )
    }
}

data class QuantityInfo(
    val actualQuantity: Double,
    val plannedQuantity: Double,
    val availableQuantity: Double
)

data class ConsumeRequest(
    @field:NotNull
    @field:DecimalMin("0.0")
    val quantity: Double
)

data class MoveDrugRequest(
    @field:NotNull
    val targetMedKitId: UUID
)

data class DrugTemplateDTO(
    val id: UUID,
    val name: String,
    val formType: String?,
    val category: String?,
    val manufacturer: String?,
    val description: String?
)
