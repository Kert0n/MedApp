package org.kert0n.medappserver.services.orchestrators

import org.kert0n.medappserver.controller.DrugCreateDTO
import org.kert0n.medappserver.controller.MedKitDTO
import org.kert0n.medappserver.db.model.Drug
import org.kert0n.medappserver.db.model.MedKit
import org.kert0n.medappserver.db.repository.DrugRepository
import org.kert0n.medappserver.db.repository.MedKitRepository
import org.kert0n.medappserver.db.repository.UsingRepository
import org.kert0n.medappserver.services.models.DrugService
import org.kert0n.medappserver.services.models.MedKitService
import org.kert0n.medappserver.services.models.UserService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.util.*

@Service
class MedKitDrugServices(
    private val drugService: DrugService,
    private val medKitService: MedKitService,
    private val userService: UserService,
    private val logger: Logger = LoggerFactory.getLogger(DrugService::class.java),
    private val medKitRepository: MedKitRepository,
    private val drugRepository: DrugRepository
) {
    @Transactional
    fun createDrugInMedkit(createDTO: DrugCreateDTO, userId: UUID): Drug {
        logger.debug("Creating drug: {} for user: {}", createDTO.name, userId)
        val medKit = medKitService.findByIdForUser(createDTO.medKitId, userId)
        return drugService.create(createDTO, medKit, userId)
    }

    @Transactional
    fun moveDrug(drugId: UUID, targetMedKitId: UUID, userId: UUID): Drug {
        logger.debug("Moving drug {} to medkit {}", drugId, targetMedKitId)
        val targetMedKit =
            medKitRepository.findByIdAndUsersIdWithUsers(targetMedKitId, userId) ?: throw ResponseStatusException(
                HttpStatus.NOT_FOUND
            )
        val drug = drugRepository.findByIdAndUsingsUserIdWithUsing(drugId, userId) ?: throw ResponseStatusException(
            HttpStatus.NOT_FOUND
        )

        val targetUserIds = targetMedKit.users.map { it.id }.toSet()
        val usingsToRemove = drug.usings.filter { it.user.id !in targetUserIds }.toSet()
        if (usingsToRemove.isNotEmpty()) {
            drug.usings.removeAll(usingsToRemove)
        }

        drug.medKit = targetMedKit
        return drugRepository.save(drug)
    }

    fun findAllDrugsInMedkit(medKitId: UUID): List<Drug> = drugService.findAllByMedKit(medKitId)

    @Transactional
    fun removeUserFromMedKit(medKitId: UUID, userId: UUID) {
        val medKit = medKitService.findByIdForUser(medKitId, userId)
        val user = userService.findById(userId)
        val drugs = drugRepository.findAllWithUsingsByMedKitId(medKitId)
        drugs.forEach { drug ->
            drug.usings.removeIf { it.usingKey.userId == userId }
        }
        medKitService.removeUserFromMedKit(medKit, user)
    }

    @Transactional
    fun delete(medKitId: UUID, userId: UUID, transferToMedKitId: UUID? = null) {
        val medKit = medKitRepository.findByIdAndUserIdForDeletion(medKitId, userId) ?: throw ResponseStatusException(
            HttpStatus.NOT_FOUND, "Cant find deletion target"
        )

        if (transferToMedKitId != null) {
            val targetMedKit = medKitService.findByIdForUser(transferToMedKitId, userId)

            // 1. Get the IDs of everyone who has access to the new MedKit
            val usersWithAccess = targetMedKit.users.map { it.id }.toSet()
            val usingsToRemove = medKit.drugs.flatMap { drug ->
                drug.usings.filter { it.user.id !in usersWithAccess }
            }.toSet()
            medKit.drugs.forEach { drug ->
                drug.usings.removeAll(usingsToRemove)
                drug.medKit = targetMedKit
                targetMedKit.drugs.add(drug)
            }
            medKit.drugs.clear()
        }
        medKit.users.forEach { user ->
            user.medKits.remove(medKit)
        }

        medKitRepository.delete(medKit)
    }

    @Transactional(readOnly = true)
    fun toMedKitDTO(medKit: MedKit): MedKitDTO {
        val drugs = drugRepository.findAllWithUsingsByMedKitId(medKit.id)
        return MedKitDTO(
            id = medKit.id,
            drugs = (drugs.map { drugService.toDrugDTO(it) }).toSet()
        )
    }
}