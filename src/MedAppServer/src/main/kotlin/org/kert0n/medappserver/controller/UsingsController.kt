package org.kert0n.medappserver.controller

import jakarta.validation.Valid
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotNull
import org.kert0n.medappserver.services.UsingService
import org.kert0n.medappserver.services.userId
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*
import java.time.Instant
import java.util.*

@RestController
@RequestMapping("/using")
class UsingsController(
    private val usingService: UsingService,
    private val logger: Logger = LoggerFactory.getLogger(UsingsController::class.java)
) {



    @GetMapping
    fun getUsings(authentication: Authentication): List<UsingDTO> {
        logger.debug("GET /using by user {}", authentication.userId)
        val usings = usingService.findAllByUser(authentication.userId)
        return usings.map { usingService.toUsingDTO(it) }
    }

    @GetMapping("/drug/{drugId}")
    fun getSpecificUsing(authentication: Authentication, @PathVariable drugId: UUID): UsingDTO? {
        logger.debug("GET /using/drug/{} by user {}", drugId, authentication.userId)
        val using = usingService.findByUserAndDrug(authentication.userId, drugId)
        return using?.let { usingService.toUsingDTO(it) }
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createUsing(
        authentication: Authentication,
        @Valid @RequestBody createDTO: UsingCreateDTO
    ): UsingDTO {
        logger.debug("POST /using by user {} for drug {}", authentication.userId, createDTO.drugId)
        val using = usingService.createTreatmentPlan(authentication.userId, createDTO)
        return usingService.toUsingDTO(using)
    }

    @PutMapping("/drug/{drugId}")
    fun updateUsing(
        authentication: Authentication,
        @PathVariable drugId: UUID,
        @Valid @RequestBody updateDTO: UsingUpdateDTO
    ): UsingDTO {
        logger.debug("PUT /using/drug/{} by user {}", drugId, authentication.userId)
        val using = usingService.updateTreatmentPlan(authentication.userId, drugId, updateDTO)
        return usingService.toUsingDTO(using)
    }

    @PostMapping("/drug/{drugId}/intake")
    fun recordRegularUsing(
        authentication: Authentication,
        @PathVariable drugId: UUID,
        @Valid @RequestBody intakeRequest: IntakeRequest
    ): UsingDTO {
        logger.debug("POST /using/drug/{}/intake by user {}, quantity: {}",
            drugId, authentication.userId, intakeRequest.quantityConsumed)
        val using = usingService.recordIntake(authentication.userId, drugId, intakeRequest.quantityConsumed)
        return usingService.toUsingDTO(using)
    }

    @DeleteMapping("/drug/{drugId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteTreatmentPlan(authentication: Authentication, @PathVariable drugId: UUID) {
        logger.debug("DELETE /using/drug/{} by user {}", drugId, authentication.userId)
        usingService.deleteTreatmentPlan(authentication.userId, drugId)
    }
}

data class IntakeRequest(
    @NotNull
    @DecimalMin("0.0")
    val quantityConsumed: Double
)

data class UsingDTO(
    val userId: UUID,
    val drugId: UUID,
    val plannedAmount: Double,
    val createdAt: Instant,
    val lastModified: Instant
)
data class UsingCreateDTO(
    @NotNull
    val drugId: UUID,

    @NotNull
    @DecimalMin("0.0")
    val plannedAmount: Double
)

data class UsingUpdateDTO(
    @NotNull
    @DecimalMin("0.0")
    val plannedAmount: Double
)
