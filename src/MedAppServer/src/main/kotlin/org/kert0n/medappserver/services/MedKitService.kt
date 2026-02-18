package org.kert0n.medappserver.services

import jakarta.transaction.Transactional
import org.kert0n.medappserver.db.model.*
import org.kert0n.medappserver.db.repository.DrugRepository
import org.kert0n.medappserver.db.repository.MedKitRepository
import org.kert0n.medappserver.db.repository.UserRepository
import org.kert0n.medappserver.db.repository.UsingRepository
import org.slf4j.LoggerFactory
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

@Service
class MedKitService(
    private val medKitRepository: MedKitRepository,
    private val userRepository: UserRepository,
    private val drugRepository: DrugRepository,
    private val usingRepository: UsingRepository
) {
    
    private val logger = LoggerFactory.getLogger(MedKitService::class.java)

    /**
     * Get all medicine kits for a user
     */
    fun getAllMedKitsForUser(userId: UUID): List<MedKitDTO> {
        logger.debug("Getting all medicine kits for user")
        val medKits = medKitRepository.findByUsersId(userId)
        logger.debug("Found {} medicine kits for user", medKits.size)
        return medKits.map { it.toDTO() }
    }

    /**
     * Get a specific medicine kit by ID (with access check)
     */
    fun getMedKitById(userId: UUID, medKitId: UUID): MedKitDTO {
        logger.debug("Getting medicine kit by ID")
        val medKit = medKitRepository.findByIdAndUserId(medKitId, userId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Medicine kit not found or access denied")
        return medKit.toDTO()
    }

    /**
     * Create a new medicine kit for a user
     */
    @Transactional
    fun createMedKit(userId: UUID): MedKitDTO {
        logger.debug("Creating new medicine kit for user")
        // User is already authenticated, so we can safely get a reference
        val user = userRepository.getReferenceById(userId)
        
        val medKit = MedKit()
        medKit.users.add(user)
        
        val savedMedKit = medKitRepository.save(medKit)
        logger.info("Medicine kit created successfully")
        
        return savedMedKit.toDTO()
    }

    /**
     * Delete a medicine kit (with option to move drugs to another kit)
     */
    @Transactional
    fun deleteMedKit(userId: UUID, medKitId: UUID, targetMedKitId: UUID? = null) {
        logger.debug("Deleting medicine kit")
        val medKit = medKitRepository.findByIdAndUserId(medKitId, userId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Medicine kit not found or access denied")
        
        val drugs = drugRepository.findAllByMedKitId(medKitId)
        
        if (targetMedKitId != null) {
            logger.debug("Moving {} drugs to target medicine kit", drugs.size)
            // Move drugs to target medicine kit
            val targetMedKit = medKitRepository.findByIdAndUserId(targetMedKitId, userId)
                ?: throw ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied to target medicine kit")
            
            // Simply update the medKit reference for each drug
            drugs.forEach { drug ->
                drug.medKit = targetMedKit
            }
            drugRepository.saveAll(drugs)
        } else {
            // Delete all drugs and their usings
            drugs.forEach { drug ->
                usingRepository.deleteAllByDrugId(drug.id)
                drugRepository.delete(drug)
            }
        }
        
        // Remove user from medicine kit
        // User is already authenticated, so we can safely get a reference
        val user = userRepository.getReferenceById(userId)
        
        medKit.users.remove(user)
        
        // If no users left, delete the medicine kit
        if (medKit.users.isEmpty()) {
            medKitRepository.delete(medKit)
        } else {
            medKitRepository.save(medKit)
        }
    }

    /**
     * Add a user to a shared medicine kit (for QR code sharing)
     */
    @Transactional
    fun addUserToMedKit(userId: UUID, medKitId: UUID) {
        // User is already authenticated, so we can safely get a reference
        val user = userRepository.getReferenceById(userId)
        
        val medKit = medKitRepository.findByIdOrNull(medKitId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Medicine kit not found")
        
        if (medKit.users.contains(user)) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "User already has access to this medicine kit")
        }
        
        medKit.users.add(user)
        medKitRepository.save(medKit)
    }

    /**
     * Convert MedKit entity to DTO
     */
    private fun MedKit.toDTO(): MedKitDTO {
        val drugs = drugRepository.findAllByMedKitId(this.id)
        return MedKitDTO(
            drugs = drugs.map { drug ->
                DrugDTO(
                    id = drug.id,
                    name = drug.name,
                    quantity = drug.quantity,
                    plannedQuantity = drug.usings.sumOf { it.plannedAmount },
                    quantityUnit = drug.quantityUnit,
                    formType = drug.formType ?: "",
                    category = drug.category ?: "",
                    manufacturer = drug.manufacturer ?: "",
                    country = drug.country ?: "",
                    description = drug.description ?: "",
                    medKit = this.id
                )
            }.toSet()
        )
    }
}