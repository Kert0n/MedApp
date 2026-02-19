package org.kert0n.medappserver.services

import org.kert0n.medappserver.controller.UsingCreateDTO
import org.kert0n.medappserver.controller.UsingDTO
import org.kert0n.medappserver.controller.UsingUpdateDTO
import org.kert0n.medappserver.db.model.Using
import org.kert0n.medappserver.db.model.UsingKey
import org.kert0n.medappserver.db.repository.UsingRepository
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.*

@Service
class UsingService(
    private val usingRepository: UsingRepository,
    val logger: Logger = LoggerFactory.getLogger(UsingService::class.java),
    private val userService: UserService,
    private val drugService: DrugService
) {


    @Transactional(readOnly = true)
    fun findAllByUser(userId: UUID): List<Using> {
        logger.debug("Finding all usings for user: {}", userId)
        return usingRepository.findAllByUserId(userId)
    }

    @Transactional(readOnly = true)
    fun findAllByDrug(drugId: UUID): List<Using> {
        logger.debug("Finding all usings for drug: {}", drugId)
        return usingRepository.findAllByDrugId(drugId)
    }

    @Transactional(readOnly = true)
    fun findByUserAndDrug(userId: UUID, drugId: UUID): Using {
        logger.debug("Finding using for user {} and drug {}", userId, drugId)
        return usingRepository.findByUserIdAndDrugId(userId, drugId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "There is no such using")
    }

    @Transactional
    fun createTreatmentPlan(userId: UUID, createDTO: UsingCreateDTO): Using {
        logger.debug("Creating using for user {} and drug {}", userId, createDTO.drugId)


        val user = userService.findById(userId)
        val drug = drugService.findByIdForUser(createDTO.drugId, userId)

        if (usingRepository.findByUserIdAndDrugId(userId, createDTO.drugId) != null) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "using already exists for this user and drug")
        }
        
        // Validate planned quantity
        val currentPlanned = drugService.getPlannedQuantity(createDTO.drugId)
        val availableQuantity = drug.quantity - currentPlanned
        
        if (createDTO.plannedAmount > availableQuantity) {
            logger.warn("Requested planned amount {} exceeds available quantity {}", createDTO.plannedAmount, availableQuantity)
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST, 
                "Insufficient quantity available. Available: $availableQuantity, Requested: ${createDTO.plannedAmount}"
            )
        }
        
        val using = Using(
            usingKey = UsingKey(userId, createDTO.drugId),
            user = user,
            drug = drug,
            plannedAmount = createDTO.plannedAmount,
            lastModified = Instant.now(),
            createdAt = Instant.now()
        )
        
        return usingRepository.save(using)
    }

    @Transactional
    fun updateTreatmentPlan(userId: UUID, drugId: UUID, updateDTO: UsingUpdateDTO): Using {
        logger.debug("Updating using for user {} and drug {}", userId, drugId)
        
        // Validate planned amount is positive
        if (updateDTO.plannedAmount <= 0) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Planned amount must be positive")
        }
        val using = findByUserAndDrug(userId, drugId)
        val totalPlanned = drugService.getPlannedQuantity(using.drug.id)
        val otherPlanned = totalPlanned - using.plannedAmount
        val availableQuantity = using.drug.quantity - otherPlanned
        
        if (updateDTO.plannedAmount > availableQuantity) {
            logger.warn("Updated planned amount {} exceeds available quantity {}", updateDTO.plannedAmount, availableQuantity)
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Insufficient quantity available. Available: $availableQuantity, Requested: ${updateDTO.plannedAmount}"
            )
        }
        
        using.plannedAmount = updateDTO.plannedAmount
        using.lastModified = Instant.now()
        
        return usingRepository.save(using)
    }

    @Transactional
    fun recordIntake(userId: UUID, drugId: UUID, quantityConsumed: Double): Using {
        logger.debug("Recording intake for user {} and drug {}, quantity: {}", userId, drugId, quantityConsumed)
        val using = findByUserAndDrug(userId, drugId)
        val drug = using.drug
        // Check if consumed quantity exceeds planned amount
        if (quantityConsumed > using.plannedAmount) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST, 
                "Consumed quantity exceeds planned amount. Planned: ${using.plannedAmount}, Consumed: $quantityConsumed"
            )
        }
        
        if (quantityConsumed > drug.quantity) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Insufficient drug quantity available")
        }
        
        // Reduce drug quantity
        drugService.consumeDrug(drugId, quantityConsumed, userId)
        
        // Update planned amount
        using.plannedAmount = maxOf(0.0, using.plannedAmount - quantityConsumed)
        using.lastModified = Instant.now()
        
        return usingRepository.save(using)
    }

    @Transactional
    fun deleteTreatmentPlan(userId: UUID, drugId: UUID) {
        logger.debug("Deleting using for user {} and drug {}", userId, drugId)
        val using = findByUserAndDrug(userId, drugId)
        usingRepository.delete(using)
    }

    @Transactional(readOnly = true)
    fun toUsingDTO(using: Using): UsingDTO {
        return UsingDTO(
            userId = using.user.id,
            drugId = using.drug.id,
            plannedAmount = using.plannedAmount,
            createdAt = using.createdAt,
            lastModified = using.lastModified
        )
    }
}
