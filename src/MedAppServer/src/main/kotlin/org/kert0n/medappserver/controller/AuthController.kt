package org.kert0n.medappserver.controller

import jakarta.servlet.http.HttpServletRequest
import org.kert0n.medappserver.db.model.User
import org.kert0n.medappserver.services.UserService
import org.kert0n.medappserver.services.security.SecurityService
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.util.*

@RestController
@RequestMapping("/auth")
class AuthController(
    @Value($$"${registration.secret}") private val registrationSecret: String,
    private val userService: UserService,
    private val securityService: SecurityService
    ) {


    data class RegisterResponse(val login: UUID, val key: String)

    @PostMapping("/register")
    fun register(request: HttpServletRequest, secret: String): RegisterResponse {
        if (secret != registrationSecret) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid secret")
        }
        if (!securityService.validateRequest(request.remoteAddr)) {
            throw ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT, "Too many registration request")
        }
        val login = UUID.randomUUID()
        val pwd: String = securityService.generateKey(32)
        userService.registerNewUser(login, pwd, request.remoteAddr)
        return RegisterResponse(login, pwd)
    }

    @GetMapping("/login")
    fun login(authentication: Authentication): String =
        securityService.generateToken(authentication.principal as User)

}