package org.kert0n.medappserver.services

import org.kert0n.medappserver.db.model.*
import org.kert0n.medappserver.db.repository.DrugRepository
import org.kert0n.medappserver.db.repository.MedKitRepository
import org.kert0n.medappserver.db.repository.UsingRepository
import org.slf4j.LoggerFactory
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

@Service
class DrugService(
    private val drugRepository: DrugRepository,
    private val medKitRepository: MedKitRepository,
    private val usingRepository: UsingRepository
) {
    
    private val logger = LoggerFactory.getLogger(DrugService::class.java)

    @Transactional(readOnly = true)
    fun findById(drugId: UUID): Drug {
        logger.debug("Finding drug by ID: {}", drugId)
        return drugRepository.findById(drugId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Drug not found: $drugId") }
    }

    @Transactional(readOnly = true)
    fun findByIdForUser(drugId: UUID, userId: UUID): Drug {
        logger.debug("Finding drug {} for user {}", drugId, userId)
        return drugRepository.findByIdAndMedKitUserId(drugId, userId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Drug not found or access denied")
    }

    @Transactional(readOnly = true)
    fun findAllByMedKit(medKitId: UUID): List<Drug> {
        logger.debug("Finding all drugs for medkit: {}", medKitId)
        return drugRepository.findAllByMedKitId(medKitId)
    }

    @Transactional(readOnly = true)
    fun findAllByUser(userId: UUID): List<Drug> {
        logger.debug("Finding all drugs for user: {}", userId)
        return drugRepository.findByUsingsUserId(userId)
    }

    @Transactional
    fun create(createDTO: DrugCreateDTO, userId: UUID): Drug {
        logger.debug("Creating drug: {} for user: {}", createDTO.name, userId)
        
        val medKit = medKitRepository.findById(createDTO.medKitId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "MedKit not found") }
        
        // Check if user has access to this medkit
        if (!medKit.users.any { it.id == userId }) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "User does not have access to this medkit")
        }
        
        val drug = Drug(
            name = createDTO.name,
            quantity = createDTO.quantity,
            quantityUnit = createDTO.quantityUnit,
            formType = createDTO.formType,
            category = createDTO.category,
            manufacturer = createDTO.manufacturer,
            country = createDTO.country,
            description = createDTO.description,
            medKit = medKit
        )
        
        return drugRepository.save(drug)
    }

    @Transactional
    fun update(drugId: UUID, updateDTO: DrugUpdateDTO, userId: UUID): Drug {
        logger.debug("Updating drug: {}", drugId)
        
        val drug = findByIdForUser(drugId, userId)
        
        updateDTO.name?.let { drug.name = it }
        updateDTO.quantity?.let { 
            val oldQuantity = drug.quantity
            drug.quantity = it
            // Handle quantity reduction - may need to adjust treatment plans
            if (it < oldQuantity) {
                handleQuantityReduction(drug, userId)
            }
        }
        updateDTO.quantityUnit?.let { drug.quantityUnit = it }
        updateDTO.formType?.let { drug.formType = it }
        updateDTO.category?.let { drug.category = it }
        updateDTO.manufacturer?.let { drug.manufacturer = it }
        updateDTO.country?.let { drug.country = it }
        updateDTO.description?.let { drug.description = it }
        
        return drugRepository.save(drug)
    }

    @Transactional
    fun delete(drugId: UUID, userId: UUID) {
        logger.debug("Deleting drug: {}", drugId)
        
        val drug = findByIdForUser(drugId, userId)
        drugRepository.delete(drug)
    }

    @Transactional
    fun moveDrug(drugId: UUID, targetMedKitId: UUID, userId: UUID): Drug {
        logger.debug("Moving drug {} to medkit {}", drugId, targetMedKitId)
        
        val drug = findByIdForUser(drugId, userId)
        val targetMedKit = medKitRepository.findByIdOrNull(targetMedKitId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Target MedKit not found")
        
        if (!targetMedKit.users.any { it.id == userId }) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "User does not have access to target medkit")
        }
        
        // Remove from old medkit and add to new one
        drug.medKit.drugs.remove(drug)
        targetMedKit.drugs.add(drug)
        
        return drugRepository.save(drug)
    }

    @Transactional
    fun consumeDrug(drugId: UUID, quantity: Double, userId: UUID): Drug {
        logger.debug("Consuming {} of drug {}", quantity, drugId)
        
        if (quantity < 0) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Quantity must be positive")
        }
        
        val drug = findByIdForUser(drugId, userId)
        
        if (quantity > drug.quantity) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Insufficient quantity available")
        }
        
        drug.quantity -= quantity
        return drugRepository.save(drug)
    }

    @Transactional(readOnly = true)
    fun getPlannedQuantity(drug: Drug): Double {
        return drug.usings.sumOf { it.plannedAmount }
    }

    @Transactional(readOnly = true)
    fun getAvailableQuantity(drugId: UUID): Pair<Double, Double> {
        val drug = drugRepository.findByIdWithUsings(drugId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Drug not found")
        
        val planned = getPlannedQuantity(drug)
        return drug.quantity to planned
    }

    @Transactional(readOnly = true)
    fun toDrugDTO(drug: Drug): DrugDTO {
        val plannedQuantity = getPlannedQuantity(drug)
        return DrugDTO(
            id = drug.id,
            name = drug.name,
            quantity = drug.quantity,
            plannedQuantity = plannedQuantity,
            quantityUnit = drug.quantityUnit,
            formType = drug.formType,
            category = drug.category,
            manufacturer = drug.manufacturer,
            country = drug.country,
            description = drug.description,
            medKitId = drug.medKit.id
        )
    }

    private fun handleQuantityReduction(drug: Drug, userId: UUID) {
        logger.debug("Handling quantity reduction for drug: {}", drug.id)
        
        val totalPlanned = getPlannedQuantity(drug)
        if (totalPlanned > drug.quantity) {
            logger.warn("Drug {} quantity {} is less than planned {}", drug.id, drug.quantity, totalPlanned)
            // In shared medkit, this would trigger notifications to affected users
            // For now, the Using records remain - client will handle the conflict
        }
    }
}
