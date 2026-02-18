package org.kert0n.medappserver.services

import org.kert0n.medappserver.db.model.Drug
import org.kert0n.medappserver.db.model.User
import org.kert0n.medappserver.db.repository.DrugRepository
import org.springframework.stereotype.Service

@Service
class DrugService( private val drugRepository: DrugRepository) {

    fun getMyDrugs(user: User): List<Drug> = drugRepository.findByUsingsUserId(user.id)
}