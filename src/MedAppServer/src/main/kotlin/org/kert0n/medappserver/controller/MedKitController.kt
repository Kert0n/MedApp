package org.kert0n.medappserver.controller

import jakarta.validation.Valid
import jakarta.validation.constraints.NotNull
import org.kert0n.medappserver.services.MedKitService
import org.kert0n.medappserver.services.userId
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import java.util.*

@RestController
@RequestMapping("/med-kit")
class MedKitController(
    private val medKitService: MedKitService,
    private val logger: Logger = LoggerFactory.getLogger(MedKitController::class.java)
) {
    data class MedKitCreatedResponse(
        @NotNull
        val id: UUID
    )

    data class MedKitSummaryDTO(
        @NotNull
        val id: UUID,
        @NotNull
        val userCount: Int,
        @NotNull
        val drugCount: Int
    )

    data class AddUserRequest(
        @NotNull
        val userId: UUID
    )



    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createNew(authentication: Authentication): MedKitCreatedResponse {
        logger.debug("POST /med-kit by user {}", authentication.userId)
        val medKit = medKitService.createNew(authentication.userId)
        return MedKitCreatedResponse(medKit.id)
    }

    @GetMapping("/{id}")
    fun getMedKit(authentication: Authentication, @PathVariable id: UUID): MedKitDTO {
        logger.debug("GET /med-kit/{} by user {}", id, authentication.userId)
        val medKit = medKitService.findByIdForUser(id, authentication.userId)
        return medKitService.toMedKitDTO(medKit)
    }

    @GetMapping
    fun getAllMedKits(authentication: Authentication): List<MedKitSummaryDTO> {
        logger.debug("GET /med-kit by user {}", authentication.userId)
        val medKits = medKitService.findAllByUser(authentication.userId)
        return medKits.map { MedKitSummaryDTO(it.id, it.users.size, it.drugs.size) }
    }

    @PostMapping("/{medKitId}/share")
    fun generateKeyToMedKit(
        authentication: Authentication,
        @PathVariable medKitId: UUID,
        @Valid @RequestBody request: AddUserRequest
    ): String {
        logger.debug("POST /med-kit/{}/share by user {}", medKitId, authentication.userId)
        return medKitService.generateMedKitShareKey(
            medKitService.findByIdForUser(medKitId, authentication.userId),
            authentication.userId
        )
    }

    @DeleteMapping("/{id}/leave")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun leaveMedKit(authentication: Authentication, @PathVariable id: UUID) {
        logger.debug("DELETE /med-kit/{}/leave by user {}", id, authentication.userId)
        medKitService.removeUserFromMedKit(id, authentication.userId)
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteMedKit(
        authentication: Authentication,
        @PathVariable id: UUID,
        @RequestParam(required = false) transferToMedKitId: UUID?
    ) {
        logger.debug("DELETE /med-kit/{} by user {}, transfer to: {}", id, authentication.userId, transferToMedKitId)
        medKitService.delete(id, authentication.userId, transferToMedKitId)
    }
}

data class MedKitDTO(
    val id: UUID,
    val drugs: Set<DrugDTO>
)
