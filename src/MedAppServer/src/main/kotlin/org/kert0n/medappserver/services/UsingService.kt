package org.kert0n.medappserver.services

import org.kert0n.medappserver.db.model.User
import org.kert0n.medappserver.db.model.Using
import org.kert0n.medappserver.db.repository.UsingRepository
import org.springframework.stereotype.Service

@Service
class UsingService(
    private val usingRepository: UsingRepository
) {

    fun getMyUsings(user: User): List<Using> = usingRepository.findAllByUserId(user.id)

    fun checkInconsistency(using: Using): Boolean {
        to
    }
}