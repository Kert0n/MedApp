package org.kert0n.medappserver.services

import com.sksamuel.aedile.core.Cache
import org.kert0n.medappserver.controller.MedKitDTO
import org.kert0n.medappserver.db.model.MedKit
import org.kert0n.medappserver.db.model.User
import org.kert0n.medappserver.db.repository.MedKitRepository
import org.kert0n.medappserver.services.security.SecurityService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.util.*

@Service
open class MedKitService(
    private val medKitRepository: MedKitRepository,
    private val securityService: SecurityService,
    private val logger: Logger = LoggerFactory.getLogger(MedKitService::class.java),
    private val medKitTokenCache: Cache<String, UUID>,
    private val userService: UserService,
    private val drugService: DrugService
) {
    @Transactional
    fun createNew(userId: UUID): MedKit {
        logger.debug("Creating new medkit for user: {}", userId)
        val user: User = userService.findById(userId)
        val medKit = medKitRepository.save(MedKit())
        user.medKits.add(medKit)
        medKit.users.add(user)
        return medKit
    }

    @Transactional(readOnly = true)
    fun findById(medKitId: UUID): MedKit {
        logger.debug("Finding medkit by ID: {}", medKitId)
        return medKitRepository.findByIdOrNull(medKitId) ?: throw ResponseStatusException(
            HttpStatus.NOT_FOUND,
            "MedKit not found"
        )
    }

    @Transactional(readOnly = true)
    fun findByIdForUser(medKitId: UUID, userId: UUID): MedKit {
        logger.debug("Finding medkit {} for user {}", medKitId, userId)
        return medKitRepository.findByIdAndUserId(medKitId, userId) ?: throw ResponseStatusException(
            HttpStatus.NOT_FOUND,
            "Medkit not found or user has insufficient privileges"
        )

    }

    @Transactional(readOnly = true)
    fun findAllByUser(userId: UUID): List<MedKit> {
        logger.debug("Finding all medkits for user: {}", userId)
        return medKitRepository.findByUsersId(userId)
    }

    fun generateMedKitShareKey(medKitId: UUID, userId: UUID): String {
        val key = securityService.generateKey(16)
        medKitTokenCache[securityService.hashToken(key)] = medKitId
        return key
    }

    @Transactional
    fun addUserToMedKit(medKitId: UUID, userId: UUID): MedKit {
        logger.debug("Adding user {} to medkit {}", userId, medKitId)
        val medKit = findById(medKitId)
        val user = userService.findById(userId)
        medKit.users.add(user)
        user.medKits.add(medKit)
        return medKitRepository.save(medKit)
    }

    @Transactional
    fun joinMedKitByKey(key: String, userId: UUID): MedKit {
        val medKitId = medKitTokenCache.getOrNull(securityService.hashToken(key)) ?: throw ResponseStatusException(
            HttpStatus.NOT_FOUND, "Your token has expired or didnt exist in first place"
        )
        return addUserToMedKit(medKitId, userId)
    }

    @Transactional
    fun removeUserFromMedKit(medKitId: UUID, userId: UUID, deleteAllDrugs: Boolean = false) {
        logger.debug("Removing user {} from medkit {}, deleteAllDrugs: {}", userId, medKitId, deleteAllDrugs)
        
        val medKit = findByIdForUser(medKitId, userId)
        val user = userService.findById(userId)
        
        // Remove user's treatment plans for drugs in this medkit
        val drugsInMedKit = drugService.findAllByMedKit(medKitId)
        drugsInMedKit.forEach { drug ->
            drug.usings.removeIf { it.user.id == userId }
        }
        user.medKits.remove(medKit)
        if (medKit.users.size == 1) {
            // This user was the last
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
            drugs = (drugs.map{drugService.toDrugDTO(it)}).toSet()
        )
    }
}
