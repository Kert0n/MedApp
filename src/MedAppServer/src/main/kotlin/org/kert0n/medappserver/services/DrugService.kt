package org.kert0n.medappserver.services

import org.kert0n.medappserver.controller.DrugDTO
import org.kert0n.medappserver.controller.DrugCreateDTO
import org.kert0n.medappserver.controller.DrugUpdateDTO
import org.kert0n.medappserver.db.model.Drug
import org.kert0n.medappserver.db.repository.DrugRepository
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Lazy
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.util.*

@Service
class DrugService(
    private val drugRepository: DrugRepository,
    @Lazy private val medKitService: MedKitService,
    private val userService: UserService
) {

    private val logger = LoggerFactory.getLogger(DrugService::class.java)

    @Transactional(readOnly = true)
    fun findById(drugId: UUID): Drug {
        logger.debug("Finding drug by ID: {}", drugId)
        return drugRepository.findByIdOrNull(drugId) ?: throw ResponseStatusException(
            HttpStatus.NOT_FOUND,
            "Drug not found: $drugId"
        )
    }

    @Transactional(readOnly = true)
    fun findByIdForUser(drugId: UUID, userId: UUID): Drug {
        logger.debug("Finding drug {} for user {}", drugId, userId)
        return drugRepository.findByIdAndMedKitUsersId(drugId, userId)
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

        val medKit = medKitService.findByIdForUser(createDTO.medKitId, userId)
        
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
        val targetMedKit = medKitService.findByIdForUser(targetMedKitId, userId)
        drug.medKit = targetMedKit
        return drugRepository.save(drug)
    }

    @Transactional
    fun consumeDrug(drugId: UUID, quantity: Double, userId: UUID): Drug {
        logger.debug("Consuming {} of drug {}", quantity, drugId)
        
        val drug = findByIdForUser(drugId, userId)

        if (quantity > drug.quantity) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Insufficient quantity available")
        }

        drug.quantity -= quantity
        handleQuantityReduction(drug, userId)
        return drugRepository.save(drug)
    }

    @Transactional(readOnly = true)
    fun getPlannedQuantity(drugId: UUID): Double {
        return drugRepository.sumPlannedAmount(drugId)
    }

    @Transactional(readOnly = true)
    fun toDrugDTO(drug: Drug): DrugDTO {
        val plannedQuantity = getPlannedQuantity(drug.id)
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

        val totalPlanned = getPlannedQuantity(drug.id)
        if (totalPlanned <= drug.quantity) return
        logger.warn("Drug {} quantity {} is less than planned {}", drug.id, drug.quantity, totalPlanned)
        val reduceFactor = drug.quantity / totalPlanned
        drug.usings.forEach { it.plannedAmount *= reduceFactor }
        drugRepository.save(drug)
        // TODO FIREBASE NOTIFICATION

    }

}
