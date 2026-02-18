package org.kert0n.medappserver.services

import org.kert0n.medappserver.db.model.*
import org.kert0n.medappserver.db.repository.DrugRepository
import org.kert0n.medappserver.db.repository.UserRepository
import org.kert0n.medappserver.db.repository.UsingRepository
import org.slf4j.LoggerFactory
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.UUID

@Service
class UsingService(
    private val usingRepository: UsingRepository,
    private val drugRepository: DrugRepository,
    private val userRepository: UserRepository
) {

    private val logger = LoggerFactory.getLogger(UsingService::class.java)

    @Transactional(readOnly = true)
    fun findAllByUser(userId: UUID): List<Using> {
        logger.debug("Finding all treatment plans for user: {}", userId)
        return usingRepository.findAllByUserId(userId)
    }

    @Transactional(readOnly = true)
    fun findAllByDrug(drugId: UUID): List<Using> {
        logger.debug("Finding all treatment plans for drug: {}", drugId)
        return usingRepository.findAllByDrugId(drugId)
    }

    @Transactional(readOnly = true)
    fun findByUserAndDrug(userId: UUID, drugId: UUID): Using? {
        logger.debug("Finding treatment plan for user {} and drug {}", userId, drugId)
        return usingRepository.findByUserIdAndDrugId(userId, drugId)
    }

    @Transactional
    fun createTreatmentPlan(userId: UUID, createDTO: UsingCreateDTO): Using {
        logger.debug("Creating treatment plan for user {} and drug {}", userId, createDTO.drugId)
        
        val user = userRepository.findByIdOrNull(userId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "User not found")
        
        val drug = drugRepository.findByIdOrNull(createDTO.drugId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Drug not found")
        
        // Check if user has access to the medkit containing this drug
        if (!drug.medKit.users.any { it.id == userId }) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "User does not have access to this drug")
        }
        
        // Check if treatment plan already exists
        val existing = findByUserAndDrug(userId, createDTO.drugId)
        if (existing != null) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Treatment plan already exists for this user and drug")
        }
        
        // Validate planned quantity
        val currentPlanned = drug.usings.sumOf { it.plannedAmount }
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
        logger.debug("Updating treatment plan for user {} and drug {}", userId, drugId)
        
        val using = findByUserAndDrug(userId, drugId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Treatment plan not found")
        
        // Validate new planned quantity
        val drug = using.drug
        val otherPlanned = drug.usings.filter { it.user.id != userId }.sumOf { it.plannedAmount }
        val availableQuantity = drug.quantity - otherPlanned
        
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
        
        if (quantityConsumed < 0) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Quantity must be positive")
        }
        
        val using = findByUserAndDrug(userId, drugId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Treatment plan not found")
        
        val drug = using.drug
        
        if (quantityConsumed > drug.quantity) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Insufficient drug quantity available")
        }
        
        // Reduce drug quantity
        drug.quantity -= quantityConsumed
        drugRepository.save(drug)
        
        // Update planned amount
        using.plannedAmount = maxOf(0.0, using.plannedAmount - quantityConsumed)
        using.lastModified = Instant.now()
        
        return usingRepository.save(using)
    }

    @Transactional
    fun deleteTreatmentPlan(userId: UUID, drugId: UUID) {
        logger.debug("Deleting treatment plan for user {} and drug {}", userId, drugId)
        
        val using = findByUserAndDrug(userId, drugId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Treatment plan not found")
        
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
