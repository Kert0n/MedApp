package org.kert0n.medappserver.services

import com.sksamuel.aedile.core.Cache
import org.kert0n.medappserver.db.model.MedKit
import org.kert0n.medappserver.db.repository.MedKitRepository
import org.kert0n.medappserver.services.security.SecurityService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.*

@Service
open class MedKitService(
    private val medKitRepository: MedKitRepository,
    private val securityService: SecurityService,
    private val logger: Logger = LoggerFactory.getLogger(MedKitService::class.java),
    private val medKitTokenCache: Cache<String, UUID>,
    private val userService: UserService,
    private val database: Database
) {
    fun createNew(userId: UUID): MedKit {
        logger.debug("Creating new medkit for user: {}", userId)
        return transaction(database) {
            userService.findById(userId)
            val medKit = medKitRepository.save(MedKit())
            medKitRepository.addUserToMedKit(userId, medKit.id)
            medKit
        }
    }

    fun findById(medKitId: UUID): MedKit {
        logger.debug("Finding medkit by ID: {}", medKitId)
        return transaction(database) {
            medKitRepository.findById(medKitId)
        } ?: throw ResponseStatusException(
            HttpStatus.NOT_FOUND,
            "MedKit not found"
        )
    }

    fun findByIdForUser(medKitId: UUID, userId: UUID): MedKit {
        logger.debug("Finding medkit {} for user {}", medKitId, userId)
        return transaction(database) {
            medKitRepository.findByIdAndUserId(medKitId, userId)
        } ?: throw ResponseStatusException(
            HttpStatus.NOT_FOUND,
            "Medkit not found or user has insufficient privileges"
        )
    }

    fun findAllByUser(userId: UUID): List<MedKit> {
        logger.debug("Finding all medkits for user: {}", userId)
        return transaction(database) {
            medKitRepository.findByUsersId(userId)
        }
    }

    fun findMedKitSummaries(userId: UUID): List<Triple<UUID, Int, Int>> {
        logger.debug("Finding medkit summaries for user: {}", userId)
        return transaction(database) {
            medKitRepository.findMedKitSummariesByUserId(userId).map { row ->
                Triple(row.first, row.second.toInt(), row.third.toInt())
            }
        }
    }

    fun generateMedKitShareKey(medKitId: UUID, userId: UUID): String {
        // Checking access
        findByIdForUser(medKitId, userId)
        val key = securityService.generateKey(16)
        // Cache only a hash so the raw share key is never stored server-side.
        medKitTokenCache[securityService.hashToken(key)] = medKitId
        return key
    }

    fun addUserToMedKit(medKitId: UUID, userId: UUID): MedKit {
        logger.debug("Adding user {} to medkit {}", userId, medKitId)
        return transaction(database) {
            val medKit = medKitRepository.findById(medKitId)
                ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "MedKit not found")
            userService.findById(userId)
            
            // Check if user is already in medkit
            val existingMedKit = medKitRepository.findByIdAndUserId(medKitId, userId)
            if (existingMedKit != null) {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "User already exists")
            }
            
            medKitRepository.addUserToMedKit(userId, medKitId)
            medKit
        }
    }

    fun joinMedKitByKey(key: String, userId: UUID): MedKit {
        val hashedKey = securityService.hashToken(key)
        val medKitId = medKitTokenCache.getOrNull(hashedKey) ?: throw ResponseStatusException(
            HttpStatus.NOT_FOUND, "Share key has expired or does not exist"
        )
        return addUserToMedKit(medKitId, userId)
    }

    fun removeUserFromMedKit(medKitId: UUID, userId: UUID) {
        logger.debug("Removing user {} from medkit {}", userId, medKitId)

        transaction(database) {
            medKitRepository.removeUserFromMedKit(userId, medKitId)
            val remainingUsers = medKitRepository.countUsersInMedKit(medKitId)
            if (remainingUsers == 0L) {
                // This user was the last
                logger.debug("No users left in medkit {}, deleting", medKitId)
                val medKit = medKitRepository.findById(medKitId)
                if (medKit != null) {
                    medKitRepository.delete(medKit)
                }
            }
        }
    }




}

