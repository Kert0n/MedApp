package org.kert0n.medappserver.controller

import org.kert0n.medappserver.db.model.User
import org.kert0n.medappserver.services.UserService
import org.kert0n.medappserver.services.security.TokenService
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.security.SecureRandom
import java.util.*
import kotlin.io.encoding.Base64

@RestController
@RequestMapping("/auth")
class AuthController(
    @Value($$"${registration.secret}") private val registrationSecret: String,
    @Value($$"${authentication.term}") private val term: Long,
    private val userService: UserService,
    private val tokenService: TokenService,

    ) {


    data class RegisterResponse(val login: UUID, val key: String)

    @PostMapping("/register")
    fun register(secret: String): RegisterResponse {
        if (secret != registrationSecret) {
            throw BadCredentialsException("Invalid secret")
        }
        val login = UUID.randomUUID()
        val pwd: String = Base64.encode(ByteArray(32).also { SecureRandom().nextBytes(it) })
        userService.registerNewUser(login, pwd)
        return RegisterResponse(login, pwd)
    }

    @GetMapping("/login")
    fun login(authentication: Authentication): String =
        tokenService.generateToken(authentication.principal as User, term)

}