package org.kert0n.medappserver.services

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.transaction
import org.kert0n.medappserver.controller.DrugCreateDTO
import org.kert0n.medappserver.controller.MedKitDTO
import org.kert0n.medappserver.db.model.Drug
import org.kert0n.medappserver.db.model.MedKit
import org.kert0n.medappserver.db.repository.UsingRepository
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.*

@Service
class MedKitDrugServices(
    private val drugService: DrugService,
    private val medKitService: MedKitService,
    private val userService: UserService,
    private val usingRepository: UsingRepository,
    private val database: Database,
    private val logger: Logger = LoggerFactory.getLogger(DrugService::class.java)
) {
    fun createDrugInMedkit(createDTO: DrugCreateDTO, userId: UUID): Drug {
        logger.debug("Creating drug: {} for user: {}", createDTO.name, userId)
        val medKit = medKitService.findByIdForUser(createDTO.medKitId, userId)
        return drugService.create(createDTO, medKit.id, userId)
    }

    fun moveDrug(drugId: UUID, targetMedKitId: UUID, userId: UUID): Drug {
        logger.debug("Moving drug {} to medkit {}", drugId, targetMedKitId)
        medKitService.findByIdForUser(targetMedKitId, userId)
        return drugService.moveDrug(drugId, targetMedKitId, userId)
    }

    fun findAllDrugsInMedkit(medKitId: UUID): List<Drug> = drugService.findAllByMedKit(medKitId)

    fun removeUserFromMedKit(medKitId: UUID, userId: UUID) {
        medKitService.findByIdForUser(medKitId, userId)
        // Remove usings for drugs in this medkit for this user
        transaction(database) {
            val drugsInMedKit = drugService.findAllByMedKit(medKitId)
            drugsInMedKit.forEach { drug ->
                usingRepository.deleteByUserIdAndDrugId(userId, drug.id)
            }
        }
        medKitService.removeUserFromMedKit(medKitId, userId)
    }

    fun delete(medKitId: UUID, userId: UUID, transferToMedKitId: UUID? = null) {
        logger.debug("Deleting medkit {} by user {}, transfer to: {}", medKitId, userId, transferToMedKitId)

        medKitService.findByIdForUser(medKitId, userId)

        if (transferToMedKitId != null) {
            medKitService.findByIdForUser(transferToMedKitId, userId)

            val drugs = findAllDrugsInMedkit(medKitId)
            drugs.forEach { drug ->
                moveDrug(drug.id, transferToMedKitId, userId)
            }
        }

        removeUserFromMedKit(medKitId, userId)
    }

    fun toMedKitDTO(medKit: MedKit): MedKitDTO {
        val drugs = drugService.findAllByMedKit(medKit.id)
        return MedKitDTO(
            id = medKit.id,
            drugs = (drugs.map { drugService.toDrugDTO(it) }).toSet()
        )
    }
}