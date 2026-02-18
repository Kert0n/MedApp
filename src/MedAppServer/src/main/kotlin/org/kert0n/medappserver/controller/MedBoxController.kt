package org.kert0n.medappserver.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.kert0n.medappserver.db.model.DrugDTO
import org.kert0n.medappserver.db.model.MedKitDTO
import org.kert0n.medappserver.services.DrugService
import org.kert0n.medappserver.services.MedKitService
import org.kert0n.medappserver.services.userId
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import java.util.*

@RestController
@RequestMapping("/med-kit")
@Tag(name = "Medicine Kit Management", description = "APIs for managing medicine kits/first-aid kits")
class MedBoxController(
    private val medKitService: MedKitService,
    private val drugService: DrugService
) {

    @Operation(summary = "Get all medicine kits", description = "Retrieves all medicine kits accessible to the user")
    @ApiResponse(responseCode = "200", description = "List of medicine kits")
    @GetMapping
    fun getAllMedKits(@Parameter(hidden = true) authentication: Authentication): List<MedKitDTO> {
        return medKitService.getAllMedKitsForUser(authentication.userId)
    }

    @Operation(summary = "Create medicine kit", description = "Creates a new medicine kit for the user")
    @ApiResponse(responseCode = "201", description = "Medicine kit created successfully")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createMedKit(@Parameter(hidden = true) authentication: Authentication): MedKitDTO {
        return medKitService.createMedKit(authentication.userId)
    }

    @Operation(summary = "Get medicine kit", description = "Retrieves a specific medicine kit by ID")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Medicine kit found"),
        ApiResponse(responseCode = "404", description = "Medicine kit not found or access denied")
    ])
    @GetMapping("/{id}")
    fun getMedKit(
        @Parameter(hidden = true) authentication: Authentication,
        @Parameter(description = "Medicine kit UUID") @PathVariable id: UUID
    ): MedKitDTO {
        return medKitService.getMedKitById(authentication.userId, id)
    }

    @Operation(summary = "Get drugs in kit", description = "Retrieves all drugs in a specific medicine kit")
    @ApiResponse(responseCode = "200", description = "List of drugs")
    @GetMapping("/{id}/drugs")
    fun getDrugsInMedKit(
        @Parameter(hidden = true) authentication: Authentication,
        @Parameter(description = "Medicine kit UUID") @PathVariable id: UUID
    ): List<DrugDTO> {
        return drugService.getDrugsInMedKit(authentication.userId, id)
    }

    @Operation(
        summary = "Delete medicine kit",
        description = "Deletes a medicine kit. Optionally moves drugs to another kit before deletion."
    )
    @ApiResponse(responseCode = "204", description = "Medicine kit deleted successfully")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteMedKit(
        @Parameter(hidden = true) authentication: Authentication,
        @Parameter(description = "Medicine kit UUID to delete") @PathVariable id: UUID,
        @Parameter(description = "Target medicine kit UUID for drug migration (optional)") @RequestParam(required = false) targetMedKitId: UUID?
    ) {
        medKitService.deleteMedKit(authentication.userId, id, targetMedKitId)
    }

    @Operation(
        summary = "Join shared medicine kit",
        description = "Joins a shared medicine kit using its ID (from QR code or text code)"
    )
    @ApiResponses(value = [
        ApiResponse(responseCode = "201", description = "Successfully joined medicine kit"),
        ApiResponse(responseCode = "404", description = "Medicine kit not found"),
        ApiResponse(responseCode = "409", description = "User already has access to this medicine kit")
    ])
    @PostMapping("/{id}/join")
    @ResponseStatus(HttpStatus.CREATED)
    fun joinMedKit(
        @Parameter(hidden = true) authentication: Authentication,
        @Parameter(description = "Medicine kit UUID to join") @PathVariable id: UUID
    ): Map<String, String> {
        medKitService.addUserToMedKit(authentication.userId, id)
        return mapOf("message" to "Successfully joined medicine kit")
    }
}