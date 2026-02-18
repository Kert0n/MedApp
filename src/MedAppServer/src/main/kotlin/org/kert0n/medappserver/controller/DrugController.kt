package org.kert0n.medappserver.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.Min
import org.kert0n.medappserver.db.model.DrugDTO
import org.kert0n.medappserver.db.model.DrugPostDTO
import org.kert0n.medappserver.db.model.parsed.VidalDrug
import org.kert0n.medappserver.services.DrugService
import org.kert0n.medappserver.services.UsingService
import org.kert0n.medappserver.services.VidalDrugService
import org.kert0n.medappserver.services.userId
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*
import java.util.*

@RestController
@RequestMapping("/drug")
@Tag(name = "Drug Management", description = "APIs for managing drugs/medicines")
@Validated
class DrugController(
    private val drugService: DrugService,
    private val usingService: UsingService,
    private val vidalDrugService: VidalDrugService
) {

    @Operation(summary = "Get drug by ID", description = "Retrieves detailed information about a specific drug")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Drug found"),
        ApiResponse(responseCode = "404", description = "Drug not found or access denied")
    ])
    @GetMapping("/{id}")
    fun getDrug(
        @Parameter(hidden = true) authentication: Authentication,
        @Parameter(description = "Drug UUID") @PathVariable id: UUID
    ): DrugDTO {
        return drugService.getDrugById(authentication.userId, id)
    }

    @Operation(summary = "Get all drugs", description = "Retrieves all drugs accessible to the authenticated user")
    @ApiResponse(responseCode = "200", description = "List of drugs")
    @GetMapping
    fun getAllDrugs(@Parameter(hidden = true) authentication: Authentication): List<DrugDTO> {
        return drugService.getAllDrugsForUser(authentication.userId)
    }

    @Operation(summary = "Add drugs", description = "Adds new drugs to a medicine kit")
    @ApiResponses(value = [
        ApiResponse(responseCode = "201", description = "Drugs created successfully"),
        ApiResponse(responseCode = "400", description = "Invalid request data"),
        ApiResponse(responseCode = "403", description = "Access denied to medicine kit")
    ])
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun addDrugs(
        @Parameter(hidden = true) authentication: Authentication,
        @Valid @RequestBody drugs: Set<DrugPostDTO>
    ): List<DrugDTO> {
        return drugs.map { drugService.addDrug(authentication.userId, it) }
    }

    @Operation(summary = "Get drug state", description = "Gets current and planned quantity (lightweight)")
    @ApiResponse(responseCode = "200", description = "Drug state retrieved")
    @GetMapping("/light/{id}")
    fun getDrugState(
        @Parameter(hidden = true) authentication: Authentication,
        @Parameter(description = "Drug UUID") @PathVariable id: UUID
    ): Map<String, Double> {
        val (quantity, plannedQuantity) = drugService.getDrugState(authentication.userId, id)
        return mapOf(
            "quantity" to quantity,
            "plannedQuantity" to plannedQuantity
        )
    }

    @Operation(summary = "Consume drug", description = "Reduces drug quantity by specified amount")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Drug consumed successfully"),
        ApiResponse(responseCode = "400", description = "Invalid quantity or not enough available")
    ])
    @PutMapping("/{id}/consume")
    fun consumeDrug(
        @Parameter(hidden = true) authentication: Authentication,
        @Parameter(description = "Drug UUID") @PathVariable id: UUID,
        @Parameter(description = "Amount to consume") @RequestParam quantity: Double
    ): Map<String, Double> {
        val remainingQuantity = drugService.consumeDrug(authentication.userId, id, quantity)
        return mapOf("remainingQuantity" to remainingQuantity)
    }

    @Operation(summary = "Delete drug", description = "Permanently deletes a drug and all associated treatment plans")
    @ApiResponse(responseCode = "204", description = "Drug deleted successfully")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteDrug(
        @Parameter(hidden = true) authentication: Authentication,
        @Parameter(description = "Drug UUID") @PathVariable id: UUID
    ) {
        drugService.deleteDrug(authentication.userId, id)
    }

    @Operation(summary = "Move drug", description = "Moves a drug to another medicine kit (preserves treatment plans)")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Drug moved successfully"),
        ApiResponse(responseCode = "403", description = "Access denied to target medicine kit")
    ])
    @PutMapping("/{id}/move")
    fun moveDrug(
        @Parameter(hidden = true) authentication: Authentication,
        @Parameter(description = "Drug UUID") @PathVariable id: UUID,
        @Parameter(description = "Target medicine kit UUID") @RequestParam targetMedKitId: UUID
    ): DrugDTO {
        return drugService.moveDrugToMedKit(authentication.userId, id, targetMedKitId)
    }

    @Operation(summary = "Create treatment plan", description = "Creates a new treatment plan (reserves drug quantity)")
    @ApiResponses(value = [
        ApiResponse(responseCode = "201", description = "Treatment plan created"),
        ApiResponse(responseCode = "400", description = "Not enough quantity or invalid amount"),
        ApiResponse(responseCode = "409", description = "Treatment plan already exists")
    ])
    @PostMapping("/plan")
    @ResponseStatus(HttpStatus.CREATED)
    fun createTreatmentPlan(
        @Parameter(hidden = true) authentication: Authentication,
        @Parameter(description = "Drug UUID") @RequestParam drugId: UUID,
        @Parameter(description = "Planned amount to reserve") @RequestParam plannedAmount: Double
    ): Map<String, String> {
        usingService.createUsing(authentication.userId, drugId, plannedAmount)
        return mapOf("message" to "Treatment plan created successfully")
    }

    @Operation(summary = "Record planned intake", description = "Records consumption from a treatment plan")
    @ApiResponse(responseCode = "200", description = "Intake recorded successfully")
    @PutMapping("/plan/{drugId}/intake")
    fun recordPlannedIntake(
        @Parameter(hidden = true) authentication: Authentication,
        @Parameter(description = "Drug UUID") @PathVariable drugId: UUID,
        @Parameter(description = "Amount consumed") @RequestParam consumedAmount: Double
    ): Map<String, Double> {
        val remainingQuantity = usingService.recordPlannedIntake(authentication.userId, drugId, consumedAmount)
        return mapOf("remainingQuantity" to remainingQuantity)
    }

    @Operation(summary = "Update treatment plan", description = "Updates the planned amount for a treatment plan")
    @ApiResponse(responseCode = "200", description = "Treatment plan updated")
    @PutMapping("/plan/{drugId}")
    fun updateTreatmentPlan(
        @Parameter(hidden = true) authentication: Authentication,
        @Parameter(description = "Drug UUID") @PathVariable drugId: UUID,
        @Parameter(description = "New planned amount") @RequestParam newPlannedAmount: Double
    ): Map<String, String> {
        usingService.updateUsing(authentication.userId, drugId, newPlannedAmount)
        return mapOf("message" to "Treatment plan updated successfully")
    }

    @Operation(summary = "Delete treatment plan", description = "Deletes a treatment plan for a drug")
    @ApiResponse(responseCode = "204", description = "Treatment plan deleted")
    @DeleteMapping("/plan/{drugId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteTreatmentPlan(
        @Parameter(hidden = true) authentication: Authentication,
        @Parameter(description = "Drug UUID") @PathVariable drugId: UUID
    ) {
        usingService.deleteUsing(authentication.userId, drugId)
    }

    @Operation(summary = "Search drug templates", description = "Fuzzy search for drugs in catalog using PostgreSQL trigrams")
    @ApiResponse(responseCode = "200", description = "Search results")
    @GetMapping("/template")
    fun searchDrugTemplates(
        @Parameter(hidden = true) authentication: Authentication,
        @Parameter(description = "Search term") @RequestParam searchTerm: String
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

    @Operation(summary = "Get drug template", description = "Gets complete information about a drug template from catalog")
    @ApiResponse(responseCode = "200", description = "Drug template found")
    @GetMapping("/template/{id}")
    fun getDrugTemplate(
        @Parameter(hidden = true) authentication: Authentication,
        @Parameter(description = "Template UUID") @PathVariable id: UUID
    ): VidalDrug {
        return vidalDrugService.getDrugById(id)
    }
}