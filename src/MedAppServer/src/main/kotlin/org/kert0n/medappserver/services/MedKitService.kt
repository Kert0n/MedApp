package org.kert0n.medappserver.services

import org.kert0n.medappserver.db.model.MedKit
import org.kert0n.medappserver.db.model.User
import org.kert0n.medappserver.db.repository.MedKitRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

@Service
class MedKitService(
    private val medKitRepository: MedKitRepository
) {

    fun createNew(user: UUID): MedKit {
        val medKit = MedKit()
        medKit.users.add(user)
        medKitRepository.save(medKit)
        return medKit
    }
}