package org.kert0n.medappserver.services

import org.kert0n.medappserver.db.model.User
import org.kert0n.medappserver.db.repository.UserRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.util.*

@Service
class UserService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder
) : UserDetailsService {

    fun registerNewUser(login: UUID, password: String): User =
        userRepository.save(User(login, passwordEncoder.encode(password)!!))

    override fun loadUserByUsername(username: String): UserDetails =
        userRepository.findByIdOrNull(UUID.fromString(username)) ?: throw UsernameNotFoundException(username)

    fun findById(id: UUID): User = userRepository.findByIdOrNull(id)?:throw ResponseStatusException(HttpStatus.NOT_FOUND,"User with ID $id not found")


}

val Authentication.userId: UUID
    get() = UUID.fromString(this.name)