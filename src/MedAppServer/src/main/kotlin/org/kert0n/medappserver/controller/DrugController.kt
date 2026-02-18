package org.kert0n.medappserver.controller

import org.kert0n.medappserver.db.model.DrugDTO
import org.kert0n.medappserver.db.model.DrugPostDTO
import org.kert0n.medappserver.db.model.parsed.VidalDrug
import org.kert0n.medappserver.services.DrugService
import org.kert0n.medappserver.services.UsingService
import org.kert0n.medappserver.services.VidalDrugService
import org.kert0n.medappserver.services.userId
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

    /**
     * Get a specific drug by ID
     */
    @GetMapping("/{id}")
    fun getDrug(authentication: Authentication, @PathVariable id: UUID): DrugDTO {
        return drugService.getDrugById(authentication.userId, id)
    }

    /**
     * Get all drugs for the authenticated user
     */
    @GetMapping
    fun getAllDrugs(authentication: Authentication): List<DrugDTO> {
        return drugService.getAllDrugsForUser(authentication.userId)
    }

    /**
     * Add new drugs to a medicine kit
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun addDrugs(authentication: Authentication, @RequestBody drugs: Set<DrugPostDTO>): List<DrugDTO> {
        return drugs.map { drugService.addDrug(authentication.userId, it) }
    }

    /**
     * Get drug quantity and planned quantity (lightweight endpoint)
     */
    @GetMapping("/light/{id}")
    fun getDrugState(authentication: Authentication, @PathVariable id: UUID): Map<String, Double> {
        val (quantity, plannedQuantity) = drugService.getDrugState(authentication.userId, id)
        return mapOf(
            "quantity" to quantity,
            "plannedQuantity" to plannedQuantity
        )
    }

    /**
     * Consume a drug (reduce quantity)
     */
    @PutMapping("/{id}/consume")
    fun consumeDrug(
        authentication: Authentication, 
        @PathVariable id: UUID, 
        @RequestParam quantity: Double
    ): Map<String, Double> {
        val remainingQuantity = drugService.consumeDrug(authentication.userId, id, quantity)
        return mapOf("remainingQuantity" to remainingQuantity)
    }

    /**
     * Delete a drug
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteDrug(authentication: Authentication, @PathVariable id: UUID) {
        drugService.deleteDrug(authentication.userId, id)
    }

    /**
     * Move a drug to another medicine kit
     */
    @PutMapping("/{id}/move")
    fun moveDrug(
        authentication: Authentication,
        @PathVariable id: UUID,
        @RequestParam targetMedKitId: UUID
    ): DrugDTO {
        return drugService.moveDrugToMedKit(authentication.userId, id, targetMedKitId)
    }

    /**
     * Create a treatment plan for a drug
     */
    @PostMapping("/plan")
    @ResponseStatus(HttpStatus.CREATED)
    fun createTreatmentPlan(
        authentication: Authentication,
        @RequestParam drugId: UUID,
        @RequestParam plannedAmount: Double
    ): Map<String, String> {
        usingService.createUsing(authentication.userId, drugId, plannedAmount)
        return mapOf("message" to "Treatment plan created successfully")
    }

    /**
     * Record a planned intake
     */
    @PutMapping("/plan/{drugId}/intake")
    fun recordPlannedIntake(
        authentication: Authentication,
        @PathVariable drugId: UUID,
        @RequestParam consumedAmount: Double
    ): Map<String, Double> {
        val remainingQuantity = usingService.recordPlannedIntake(authentication.userId, drugId, consumedAmount)
        return mapOf("remainingQuantity" to remainingQuantity)
    }

    /**
     * Update a treatment plan
     */
    @PutMapping("/plan/{drugId}")
    fun updateTreatmentPlan(
        authentication: Authentication,
        @PathVariable drugId: UUID,
        @RequestParam newPlannedAmount: Double
    ): Map<String, String> {
        usingService.updateUsing(authentication.userId, drugId, newPlannedAmount)
        return mapOf("message" to "Treatment plan updated successfully")
    }

    /**
     * Delete a treatment plan
     */
    @DeleteMapping("/plan/{drugId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteTreatmentPlan(authentication: Authentication, @PathVariable drugId: UUID) {
        usingService.deleteUsing(authentication.userId, drugId)
    }

    /**
     * Fuzzy search for drug templates by name
     */
    @GetMapping("/template")
    fun searchDrugTemplates(
        authentication: Authentication,
        @RequestParam searchTerm: String
    ): List<Map<String, Any>> {
        val drugs = vidalDrugService.fuzzySearchByName(searchTerm)
        return drugs.map { drug ->
            mapOf(
                "id" to drug.id,
                "name" to drug.name,
                "formType" to (drug.formType?.name ?: ""),
                "manufacturer" to drug.manufacturer
            )
        }
    }

    /**
     * Get a specific drug template by ID
     */
    @GetMapping("/template/{id}")
    fun getDrugTemplate(
        authentication: Authentication,
        @PathVariable id: UUID
    ): VidalDrug {
        return vidalDrugService.getDrugById(id)
    }
}