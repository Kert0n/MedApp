package org.kert0n.medappserver.services

import org.kert0n.medappserver.controller.DrugCreateDTO
import org.kert0n.medappserver.controller.MedKitDTO
import org.kert0n.medappserver.db.model.Drug
import org.kert0n.medappserver.db.model.MedKit
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

@Service
class MedKitDrugServices(
    private val drugService: DrugService,
    private val medKitService: MedKitService,
    private val userService: UserService,
    private val logger: Logger = LoggerFactory.getLogger(DrugService::class.java)
) {
    @Transactional
    fun createDrugInMedkit(createDTO: DrugCreateDTO, userId: UUID): Drug {
        logger.debug("Creating drug: {} for user: {}", createDTO.name, userId)
        val medKit = medKitService.findByIdForUser(createDTO.medKitId, userId)
        return drugService.create(createDTO, medKit, userId)
    }

    fun moveDrug(drugId: UUID, targetMedKitId: UUID, userId: UUID): Drug {
        logger.debug("Moving drug {} to medkit {}", drugId, targetMedKitId)
        val targetMedKit = medKitService.findByIdForUser(targetMedKitId, userId)
        return drugService.moveDrug(drugId, targetMedKit, userId)
    }

    fun findAllDrugsInMedkit(medKitId: UUID): List<Drug> = drugService.findAllByMedKit(medKitId)

    @Transactional
    fun removeUserFromMedKit(medKitId: UUID, userId: UUID) {
        val medKit = medKitService.findByIdForUser(medKitId, userId)
        val user = userService.findById(userId)
        val drugs = drugService.findAllByMedKit(medKitId)
        drugs.forEach { drug ->
            drug.usings.removeIf { it.usingKey.userId == userId }
        }
        medKitService.removeUserFromMedKit(medKit, user)
    }

    @Transactional
    fun delete(medKitId: UUID, userId: UUID, transferToMedKitId: UUID? = null) {
        logger.debug("Deleting medkit {} by user {}, transfer to: {}", medKitId, userId, transferToMedKitId)

        val medKit = medKitService.findByIdForUser(medKitId, userId)

        if (transferToMedKitId != null) {
            val targetMedKit = medKitService.findByIdForUser(transferToMedKitId, userId)

            val drugs = findAllDrugsInMedkit(medKitId)
            drugs.forEach { drug ->
                moveDrug(drug.id, transferToMedKitId, userId)
            }
        }

        removeUserFromMedKit(medKitId, userId)
    }

    @Transactional(readOnly = true)
    fun toMedKitDTO(medKit: MedKit): MedKitDTO {
        val drugs = if (medKit.drugs.isNotEmpty()) medKit.drugs else drugService.findAllByMedKit(medKit.id)
        return MedKitDTO(
            id = medKit.id,
            drugs = (drugs.map { drugService.toDrugDTO(it) }).toSet()
        )
    }
}
