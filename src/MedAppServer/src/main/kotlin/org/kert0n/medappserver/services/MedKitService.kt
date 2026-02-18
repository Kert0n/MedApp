package org.kert0n.medappserver.services

import org.kert0n.medappserver.db.model.MedKit
import org.kert0n.medappserver.db.model.MedKitDTO
import org.kert0n.medappserver.db.model.User
import org.kert0n.medappserver.db.repository.MedKitRepository
import org.kert0n.medappserver.db.repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

@Service
class MedKitService(
    private val medKitRepository: MedKitRepository,
    private val userRepository: UserRepository,
    private val drugService: DrugService
) {

    private val logger = LoggerFactory.getLogger(MedKitService::class.java)

    @Transactional
    fun createNew(userId: UUID): MedKit {
        logger.debug("Creating new medkit for user: {}", userId)
        
        val user = userRepository.findByIdOrNull(userId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "User not found")
        
        val medKit = MedKit()
        medKit.users.add(user)
        user.medKits.add(medKit)
        
        return medKitRepository.save(medKit)
    }

    @Transactional(readOnly = true)
    fun findById(medKitId: UUID): MedKit {
        logger.debug("Finding medkit by ID: {}", medKitId)
        return medKitRepository.findByIdOrNull(medKitId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "MedKit not found")
    }

    @Transactional(readOnly = true)
    fun findByIdForUser(medKitId: UUID, userId: UUID): MedKit {
        logger.debug("Finding medkit {} for user {}", medKitId, userId)
        
        val medKit = findById(medKitId)
        if (!medKit.users.any { it.id == userId }) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "User does not have access to this medkit")
        }
        return medKit
    }

    @Transactional(readOnly = true)
    fun findAllByUser(userId: UUID): List<MedKit> {
        logger.debug("Finding all medkits for user: {}", userId)
        return medKitRepository.findByUsersId(userId)
    }

    @Transactional
    fun addUserToMedKit(medKitId: UUID, userId: UUID): MedKit {
        logger.debug("Adding user {} to medkit {}", userId, medKitId)
        
        val medKit = findById(medKitId)
        val user = userRepository.findByIdOrNull(userId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "User not found")
        
        if (medKit.users.contains(user)) {
            logger.warn("User {} already has access to medkit {}", userId, medKitId)
            return medKit
        }
        
        medKit.users.add(user)
        user.medKits.add(medKit)
        
        return medKitRepository.save(medKit)
    }

    @Transactional
    fun removeUserFromMedKit(medKitId: UUID, userId: UUID, deleteAllDrugs: Boolean = false) {
        logger.debug("Removing user {} from medkit {}, deleteAllDrugs: {}", userId, medKitId, deleteAllDrugs)
        
        val medKit = findByIdForUser(medKitId, userId)
        val user = userRepository.findByIdOrNull(userId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "User not found")
        
        // Remove user's treatment plans for drugs in this medkit
        val drugsInMedKit = drugService.findAllByMedKit(medKitId)
        drugsInMedKit.forEach { drug ->
            drug.usings.removeIf { it.user.id == userId }
        }
        
        medKit.users.remove(user)
        user.medKits.remove(medKit)
        
        // If no users left or if last user is leaving, delete the medkit
        if (medKit.users.isEmpty()) {
            logger.debug("No users left in medkit {}, deleting", medKitId)
            medKitRepository.delete(medKit)
        } else {
            medKitRepository.save(medKit)
        }
    }

    @Transactional
    fun delete(medKitId: UUID, userId: UUID, transferToMedKitId: UUID? = null) {
        logger.debug("Deleting medkit {} by user {}, transfer to: {}", medKitId, userId, transferToMedKitId)
        
        val medKit = findByIdForUser(medKitId, userId)
        
        if (transferToMedKitId != null) {
            val targetMedKit = findByIdForUser(transferToMedKitId, userId)
            
            // Transfer all drugs to target medkit
            val drugs = drugService.findAllByMedKit(medKitId)
            drugs.forEach { drug ->
                drugService.moveDrug(drug.id, transferToMedKitId, userId)
            }
        }
        
        // Remove this user from medkit (will delete if last user)
        removeUserFromMedKit(medKitId, userId)
    }

    @Transactional(readOnly = true)
    fun toMedKitDTO(medKit: MedKit): MedKitDTO {
        val drugs = drugService.findAllByMedKit(medKit.id)
        return MedKitDTO(
            id = medKit.id,
            drugs = drugs.map { drugService.toDrugDTO(it) }.toSet()
        )
    }
}
