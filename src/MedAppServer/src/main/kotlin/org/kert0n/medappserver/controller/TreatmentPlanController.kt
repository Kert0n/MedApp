package org.kert0n.medappserver.controller

import jakarta.validation.Valid
import org.kert0n.medappserver.db.model.UsingCreateDTO
import org.kert0n.medappserver.db.model.UsingDTO
import org.kert0n.medappserver.db.model.UsingUpdateDTO
import org.kert0n.medappserver.services.UsingService
import org.kert0n.medappserver.services.userId
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import java.util.*

@RestController
@RequestMapping("/treatment-plan")
class TreatmentPlanController(
    private val usingService: UsingService
) {

    private val logger = LoggerFactory.getLogger(TreatmentPlanController::class.java)

    @GetMapping
    fun getAllTreatmentPlans(authentication: Authentication): List<UsingDTO> {
        logger.debug("GET /treatment-plan by user {}", authentication.userId)
        val usings = usingService.findAllByUser(authentication.userId)
        return usings.map { usingService.toUsingDTO(it) }
    }

    @GetMapping("/drug/{drugId}")
    fun getTreatmentPlanForDrug(authentication: Authentication, @PathVariable drugId: UUID): UsingDTO? {
        logger.debug("GET /treatment-plan/drug/{} by user {}", drugId, authentication.userId)
        val using = usingService.findByUserAndDrug(authentication.userId, drugId)
        return using?.let { usingService.toUsingDTO(it) }
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createTreatmentPlan(
        authentication: Authentication,
        @Valid @RequestBody createDTO: UsingCreateDTO
    ): UsingDTO {
        logger.debug("POST /treatment-plan by user {} for drug {}", authentication.userId, createDTO.drugId)
        val using = usingService.createTreatmentPlan(authentication.userId, createDTO)
        return usingService.toUsingDTO(using)
    }

    @PutMapping("/drug/{drugId}")
    fun updateTreatmentPlan(
        authentication: Authentication,
        @PathVariable drugId: UUID,
        @Valid @RequestBody updateDTO: UsingUpdateDTO
    ): UsingDTO {
        logger.debug("PUT /treatment-plan/drug/{} by user {}", drugId, authentication.userId)
        val using = usingService.updateTreatmentPlan(authentication.userId, drugId, updateDTO)
        return usingService.toUsingDTO(using)
    }

    @PostMapping("/drug/{drugId}/intake")
    fun recordIntake(
        authentication: Authentication,
        @PathVariable drugId: UUID,
        @Valid @RequestBody intakeRequest: IntakeRequest
    ): UsingDTO {
        logger.debug("POST /treatment-plan/drug/{}/intake by user {}, quantity: {}", 
            drugId, authentication.userId, intakeRequest.quantityConsumed)
        val using = usingService.recordIntake(authentication.userId, drugId, intakeRequest.quantityConsumed)
        return usingService.toUsingDTO(using)
    }

    @DeleteMapping("/drug/{drugId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteTreatmentPlan(authentication: Authentication, @PathVariable drugId: UUID) {
        logger.debug("DELETE /treatment-plan/drug/{} by user {}", drugId, authentication.userId)
        usingService.deleteTreatmentPlan(authentication.userId, drugId)
    }
}

data class IntakeRequest(
    val quantityConsumed: Double
)
