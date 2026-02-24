package org.kert0n.medappserver.services

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.transaction
import org.kert0n.medappserver.controller.DrugDTO
import org.kert0n.medappserver.controller.DrugCreateDTO
import org.kert0n.medappserver.controller.DrugUpdateDTO
import org.kert0n.medappserver.db.model.Drug
import org.kert0n.medappserver.db.repository.DrugRepository
import org.kert0n.medappserver.db.repository.UsingRepository
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.util.*

@Service
class DrugService(
    private val drugRepository: DrugRepository,
    private val usingRepository: UsingRepository,
    private val database: Database
) {

    private val logger = LoggerFactory.getLogger(DrugService::class.java)

    fun findById(drugId: UUID): Drug {
        logger.debug("Finding drug by ID: {}", drugId)
        return transaction(database) {
            drugRepository.findById(drugId)
        } ?: throw ResponseStatusException(
            HttpStatus.NOT_FOUND,
            "Drug not found: $drugId"
        )
    }

    fun findByIdForUser(drugId: UUID, userId: UUID): Drug {
        logger.debug("Finding drug {} for user {}", drugId, userId)
        return transaction(database) {
            drugRepository.findByIdAndMedKitUsersId(drugId, userId)
        } ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Drug not found or access denied")
    }

    fun findAllByMedKit(medKitId: UUID): List<Drug> {
        logger.debug("Finding all drugs for medkit: {}", medKitId)
        return transaction(database) {
            drugRepository.findAllByMedKitId(medKitId)
        }
    }

    fun findAllByUser(userId: UUID): List<Drug> {
        logger.debug("Finding all drugs for user: {}", userId)
        return transaction(database) {
            drugRepository.findByUsingsUserId(userId)
        }
    }

    fun create(createDTO: DrugCreateDTO, medKitId: UUID, userId: UUID): Drug {
        logger.debug("Creating drug: {} for user: {}", createDTO.name, userId)

        val drug = Drug(
            name = createDTO.name,
            quantity = createDTO.quantity,
            quantityUnit = createDTO.quantityUnit,
            formType = createDTO.formType,
            category = createDTO.category,
            manufacturer = createDTO.manufacturer,
            country = createDTO.country,
            description = createDTO.description,
            medKitId = medKitId
        )
        
        return transaction(database) {
            drugRepository.save(drug)
        }
    }

    fun update(drugId: UUID, updateDTO: DrugUpdateDTO, userId: UUID): Drug {
        logger.debug("Updating drug: {}", drugId)
        
        return transaction(database) {
            val drug = drugRepository.findByIdAndMedKitUsersId(drugId, userId)
                ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Drug not found or access denied")
            
            updateDTO.name?.let { drug.name = it }
            updateDTO.quantity?.let { 
                val oldQuantity = drug.quantity
                drug.quantity = it
                // Handle quantity reduction - may need to adjust treatment plans
                if (it < oldQuantity) {
                    handleQuantityReduction(drug)
                }
            }
            updateDTO.quantityUnit?.let { drug.quantityUnit = it }
            updateDTO.formType?.let { drug.formType = it }
            updateDTO.category?.let { drug.category = it }
            updateDTO.manufacturer?.let { drug.manufacturer = it }
            updateDTO.country?.let { drug.country = it }
            updateDTO.description?.let { drug.description = it }
            
            drugRepository.save(drug)
        }
    }

    fun delete(drugId: UUID, userId: UUID) {
        logger.debug("Deleting drug: {}", drugId)
        
        transaction(database) {
            val drug = drugRepository.findByIdAndMedKitUsersId(drugId, userId)
                ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Drug not found or access denied")
            drugRepository.delete(drug)
        }
    }

    fun moveDrug(drugId: UUID, targetMedKitId: UUID, userId: UUID): Drug {
        logger.debug("Moving drug {} to medkit {}", drugId, targetMedKitId)
        
        return transaction(database) {
            val drug = drugRepository.findByIdAndMedKitUsersId(drugId, userId)
                ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Drug not found or access denied")
            drug.medKitId = targetMedKitId
            drugRepository.save(drug)
        }
    }

    fun consumeDrug(drugId: UUID, quantity: Double, userId: UUID): Drug {
        logger.debug("Consuming {} of drug {}", quantity, drugId)
        
        return transaction(database) {
            val drug = drugRepository.findByIdAndMedKitUsersId(drugId, userId)
                ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Drug not found or access denied")

            if (quantity > drug.quantity) {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Insufficient quantity available")
            }

            drug.quantity -= quantity
            handleQuantityReduction(drug)
            drugRepository.save(drug)
        }
    }

    fun getPlannedQuantity(drugId: UUID): Double {
        return transaction(database) {
            drugRepository.sumPlannedAmount(drugId)
        }
    }

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
            medKitId = drug.medKitId
        )
    }

    private fun handleQuantityReduction(drug: Drug) {
        logger.debug("Handling quantity reduction for drug: {}", drug.id)

        // Reduce planned amounts proportionally when stock drops below reserved quantity.
        val totalPlanned = drugRepository.sumPlannedAmount(drug.id)
        if (totalPlanned <= drug.quantity) return
        logger.warn("Drug {} quantity {} is less than planned {}", drug.id, drug.quantity, totalPlanned)
        val reduceFactor = drug.quantity / totalPlanned
        val usings = usingRepository.findAllByDrugId(drug.id)
        usings.forEach { using ->
            using.plannedAmount *= reduceFactor
            usingRepository.save(using)
        }
        // TODO FIREBASE NOTIFICATION

    }

}
