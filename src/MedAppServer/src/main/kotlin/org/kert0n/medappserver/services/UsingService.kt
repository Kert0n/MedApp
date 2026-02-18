package org.kert0n.medappserver.services

import jakarta.transaction.Transactional
import org.kert0n.medappserver.db.model.Using
import org.kert0n.medappserver.db.model.UsingKey
import org.kert0n.medappserver.db.repository.DrugRepository
import org.kert0n.medappserver.db.repository.UsingRepository
import org.kert0n.medappserver.db.repository.UserRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.*

@Service
class UsingService(
    private val usingRepository: UsingRepository,
    private val drugRepository: DrugRepository,
    private val userRepository: UserRepository
) {

    /**
     * Get all treatment plans (usings) for a user
     */
    fun getAllUsingsForUser(userId: UUID): List<Using> {
        return usingRepository.findAllByUserId(userId)
    }

    /**
     * Get all usings for a specific drug
     */
    fun getUsingsForDrug(drugId: UUID): List<Using> {
        return usingRepository.findAllByDrugId(drugId)
    }

    /**
     * Create a new treatment plan (using)
     */
    @Transactional
    fun createUsing(userId: UUID, drugId: UUID, plannedAmount: Double): Using {
        if (plannedAmount <= 0) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Planned amount must be positive")
        }
        
        // Verify user has access to this drug
        val drug = drugRepository.findByIdAndUserId(drugId, userId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Drug not found or access denied")
        
        val user = userRepository.findByIdOrNull(userId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "User not found")
        
        // Check if using already exists
        val existingUsing = usingRepository.findByUserIdAndDrugId(userId, drugId)
        if (existingUsing != null) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Treatment plan already exists for this drug")
        }
        
        // Check if there's enough quantity
        val totalPlanned = drug.usings.sumOf { it.plannedAmount } + plannedAmount
        if (totalPlanned > drug.quantity) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST, 
                "Not enough drug quantity available. Available: ${drug.quantity}, Required: $totalPlanned"
            )
        }
        
        val using = Using(
            usingKey = UsingKey(userId, drugId),
            user = user,
            drug = drug,
            plannedAmount = plannedAmount,
            lastUsed = Instant.now(),
            createdAt = Instant.now()
        )
        
        return usingRepository.save(using)
    }

    /**
     * Update planned amount for a treatment plan
     */
    @Transactional
    fun updateUsing(userId: UUID, drugId: UUID, newPlannedAmount: Double): Using {
        if (newPlannedAmount <= 0) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Planned amount must be positive")
        }
        
        val using = usingRepository.findByUserIdAndDrugId(userId, drugId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Treatment plan not found")
        
        val drug = drugRepository.findByIdAndUserId(drugId, userId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Drug not found")
        
        // Calculate new total planned quantity
        val otherPlanned = drug.usings.filter { it.usingKey.userId != userId }.sumOf { it.plannedAmount }
        val totalPlanned = otherPlanned + newPlannedAmount
        
        if (totalPlanned > drug.quantity) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Not enough drug quantity available. Available: ${drug.quantity}, Required: $totalPlanned"
            )
        }
        
        using.plannedAmount = newPlannedAmount
        using.lastUsed = Instant.now()
        
        return usingRepository.save(using)
    }

    /**
     * Record a planned intake (consume from plan)
     */
    @Transactional
    fun recordPlannedIntake(userId: UUID, drugId: UUID, consumedAmount: Double): Double {
        if (consumedAmount <= 0) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Consumed amount must be positive")
        }
        
        val using = usingRepository.findByUserIdAndDrugId(userId, drugId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Treatment plan not found")
        
        val drug = drugRepository.findByIdAndUserId(drugId, userId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Drug not found")
        
        if (consumedAmount > drug.quantity) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Not enough quantity available")
        }
        
        // Update drug quantity
        drug.quantity -= consumedAmount
        drugRepository.save(drug)
        
        // Update last used timestamp
        using.lastUsed = Instant.now()
        usingRepository.save(using)
        
        // Check for conflicts
        val totalPlanned = drug.usings.sumOf { it.plannedAmount }
        if (totalPlanned > drug.quantity) {
            // Conflict: reduce all planned amounts proportionally
            resolveQuantityConflict(drug)
        }
        
        return drug.quantity
    }

    /**
     * Delete a treatment plan
     */
    @Transactional
    fun deleteUsing(userId: UUID, drugId: UUID) {
        val using = usingRepository.findByUserIdAndDrugId(userId, drugId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Treatment plan not found")
        
        usingRepository.delete(using)
    }

    /**
     * Resolve conflicts when actual quantity < planned quantity
     * Reduces planned amounts proportionally across all users
     */
    private fun resolveQuantityConflict(drug: org.kert0n.medappserver.db.model.Drug) {
        val plannedTotal = drug.usings.sumOf { it.plannedAmount }
        val actualQuantity = drug.quantity
        
        if (plannedTotal <= actualQuantity) {
            return // No conflict
        }
        
        // Calculate reduction factor
        val reductionFactor = actualQuantity / plannedTotal
        
        // Reduce all planned amounts proportionally
        val updatedUsings = drug.usings.map { using ->
            using.plannedAmount *= reductionFactor
            using.lastUsed = Instant.now()
            using
        }
        usingRepository.saveAll(updatedUsings)
    }
}