package org.kert0n.medappserver.db.repository

import org.kert0n.medappserver.db.model.MedKit
import org.kert0n.medappserver.db.model.User
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface UserRepository: JpaRepository<User, UUID> {
    fun findByMedKitsId(medId: UUID): List<User>
    fun findByUsingsDrugId(drugId: UUID): List<User>

}