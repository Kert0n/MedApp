package org.kert0n.medappserver.services

import jakarta.transaction.Transactional
import org.kert0n.medappserver.db.model.*
import org.kert0n.medappserver.db.repository.DrugRepository
import org.kert0n.medappserver.db.repository.MedKitRepository
import org.kert0n.medappserver.db.repository.UsingRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.*

@Service
class DrugService(
    private val drugRepository: DrugRepository,
    private val medKitRepository: MedKitRepository,
    private val usingRepository: UsingRepository
) {

    /**
     * Get all drugs accessible to a user
     */
    fun getAllDrugsForUser(userId: UUID): List<DrugDTO> {
        val drugs = drugRepository.findAllByUserId(userId)
        return drugs.map { it.toDTO() }
    }

    /**
     * Get all drugs in a specific medicine kit (with access check)
     */
    fun getDrugsInMedKit(userId: UUID, medKitId: UUID): List<DrugDTO> {
        // Verify user has access to this medicine kit
        medKitRepository.findByIdAndUserId(medKitId, userId)
            ?: throw ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied to medicine kit")
        
        val drugs = drugRepository.findAllByMedKitId(medKitId)
        return drugs.map { it.toDTO() }
    }

    /**
     * Get a specific drug by ID (with access check)
     */
    fun getDrugById(userId: UUID, drugId: UUID): DrugDTO {
        val drug = drugRepository.findByIdAndUserId(drugId, userId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Drug not found or access denied")
        return drug.toDTO()
    }

    /**
     * Get drug quantity and planned quantity
     */
    fun getDrugState(userId: UUID, drugId: UUID): Pair<Double, Double> {
        val drug = drugRepository.findByIdAndUserId(drugId, userId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Drug not found or access denied")
        return drug.quantity to getPlannedQuantity(drug)
    }

    /**
     * Add a new drug to a medicine kit
     */
    @Transactional
    fun addDrug(userId: UUID, drugPostDTO: DrugPostDTO): DrugDTO {
        // Verify user has access to the medicine kit
        val medKit = medKitRepository.findByIdAndUserId(drugPostDTO.owner, userId)
            ?: throw ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied to medicine kit")
        
        val drug = Drug(
            name = drugPostDTO.name,
            quantity = drugPostDTO.quantity,
            quantityUnit = drugPostDTO.quantityUnit,
            formType = drugPostDTO.formType,
            category = drugPostDTO.category,
            manufacturer = drugPostDTO.manufacturer,
            country = drugPostDTO.country,
            description = drugPostDTO.description,
            medKit = medKit
        )
        
        val savedDrug = drugRepository.save(drug)
        return savedDrug.toDTO()
    }

    /**
     * Update drug quantity (consume)
     */
    @Transactional
    fun consumeDrug(userId: UUID, drugId: UUID, quantity: Double): Double {
        if (quantity <= 0) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Quantity must be positive")
        }
        
        val drug = drugRepository.findByIdAndUserId(drugId, userId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Drug not found or access denied")
        
        if (quantity > drug.quantity) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Not enough quantity available")
        }
        
        drug.quantity -= quantity
        drugRepository.save(drug)
        
        // Check if planned quantity is affected
        val plannedQuantity = getPlannedQuantity(drug)
        if (plannedQuantity > drug.quantity) {
            // Conflict: not enough drugs for planned usings
            resolveQuantityConflict(drug)
        }
        
        return drug.quantity
    }

    /**
     * Delete a drug
     */
    @Transactional
    fun deleteDrug(userId: UUID, drugId: UUID) {
        val drug = drugRepository.findByIdAndUserId(drugId, userId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Drug not found or access denied")
        
        // Delete all usings associated with this drug
        usingRepository.deleteAllByDrugId(drugId)
        
        drugRepository.delete(drug)
    }

    /**
     * Move drug to another medicine kit
     */
    @Transactional
    fun moveDrugToMedKit(userId: UUID, drugId: UUID, targetMedKitId: UUID): DrugDTO {
        val drug = drugRepository.findByIdAndUserId(drugId, userId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Drug not found or access denied")
        
        val targetMedKit = medKitRepository.findByIdAndUserId(targetMedKitId, userId)
            ?: throw ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied to target medicine kit")
        
        // Simply update the medKit reference
        drug.medKit = targetMedKit
        val savedDrug = drugRepository.save(drug)
        
        return savedDrug.toDTO()
    }

    /**
     * Calculate total planned quantity for a drug
     */
    fun getPlannedQuantity(drug: Drug): Double {
        return drug.usings.sumOf { it.plannedAmount }
    }

    /**
     * Resolve conflicts when actual quantity < planned quantity
     * Reduces planned amounts proportionally across all users
     */
    private fun resolveQuantityConflict(drug: Drug) {
        val plannedTotal = getPlannedQuantity(drug)
        val actualQuantity = drug.quantity
        
        if (plannedTotal <= actualQuantity) {
            return // No conflict
        }
        
        // Calculate reduction factor
        val reductionFactor = actualQuantity / plannedTotal
        
        // Reduce all planned amounts proportionally
        val updatedUsings = drug.usings.map { using ->
            using.plannedAmount *= reductionFactor
            using
        }
        usingRepository.saveAll(updatedUsings)
    }

    /**
     * Convert Drug entity to DTO
     */
    private fun Drug.toDTO(): DrugDTO {
        return DrugDTO(
            id = this.id,
            name = this.name,
            quantity = this.quantity,
            plannedQuantity = getPlannedQuantity(this),
            quantityUnit = this.quantityUnit,
            formType = this.formType ?: "",
            category = this.category ?: "",
            manufacturer = this.manufacturer ?: "",
            country = this.country ?: "",
            description = this.description ?: "",
            medKit = this.medKit.id
        )
    }
}