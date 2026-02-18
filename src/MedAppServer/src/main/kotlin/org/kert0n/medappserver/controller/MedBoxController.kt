package org.kert0n.medappserver.controller

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
class MedBoxController(
    private val medKitService: MedKitService,
    private val drugService: DrugService
) {

    /**
     * Get all medicine kits for the authenticated user
     */
    @GetMapping
    fun getAllMedKits(authentication: Authentication): List<MedKitDTO> {
        return medKitService.getAllMedKitsForUser(authentication.userId)
    }

    /**
     * Create a new medicine kit
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createMedKit(authentication: Authentication): MedKitDTO {
        return medKitService.createMedKit(authentication.userId)
    }

    /**
     * Get a specific medicine kit by ID
     */
    @GetMapping("/{id}")
    fun getMedKit(authentication: Authentication, @PathVariable id: UUID): MedKitDTO {
        return medKitService.getMedKitById(authentication.userId, id)
    }

    /**
     * Get all drugs in a specific medicine kit
     */
    @GetMapping("/{id}/drugs")
    fun getDrugsInMedKit(authentication: Authentication, @PathVariable id: UUID): List<DrugDTO> {
        return drugService.getDrugsInMedKit(authentication.userId, id)
    }

    /**
     * Delete a medicine kit
     * Optional: move drugs to another kit before deletion
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteMedKit(
        authentication: Authentication,
        @PathVariable id: UUID,
        @RequestParam(required = false) targetMedKitId: UUID?
    ) {
        medKitService.deleteMedKit(authentication.userId, id, targetMedKitId)
    }

    /**
     * Join a shared medicine kit using its ID (for QR code/text code sharing)
     */
    @PostMapping("/{id}/join")
    @ResponseStatus(HttpStatus.CREATED)
    fun joinMedKit(authentication: Authentication, @PathVariable id: UUID): Map<String, String> {
        medKitService.addUserToMedKit(authentication.userId, id)
        return mapOf("message" to "Successfully joined medicine kit")
    }
}