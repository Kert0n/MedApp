package org.kert0n.medappserver.services

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.transaction
import org.kert0n.medappserver.db.model.User
import org.kert0n.medappserver.db.repository.UserRepository
import org.kert0n.medappserver.services.security.SecurityService
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.util.*

@Service
class UserService(
    private val userRepository: UserRepository,
    private val securityService: SecurityService,
    private val database: Database
) : UserDetailsService {

    fun registerNewUser(login: UUID, password: String, ip: String): User {
        val user = transaction(database) {
            userRepository.save(
                User(login, securityService.hashPassword(password))
            )
        }
        securityService.registerIncrease(ip)
        return user
    }

    override fun loadUserByUsername(username: String): UserDetails =
        transaction(database) {
            userRepository.findById(UUID.fromString(username))
        } ?: throw UsernameNotFoundException(username)

    fun findById(id: UUID): User = transaction(database) {
        userRepository.findById(id)
    } ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "User with ID $id not found")

    fun findAllByDrug(drugId: UUID): Set<User> = transaction(database) {
        userRepository.findByUsingsDrugId(drugId)
    }

}

val Authentication.userId: UUID
    get() = UUID.fromString(this.name)