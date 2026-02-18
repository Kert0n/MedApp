package org.kert0n.medappserver.controller

import org.kert0n.medappserver.db.model.UserDto
import org.kert0n.medappserver.services.MedKitService
import org.kert0n.medappserver.services.UserService
import org.kert0n.medappserver.services.userId
import org.slf4j.LoggerFactory
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/user")
class UserController(
    private val userService: UserService,
    private val medKitService: MedKitService
) {

    private val logger = LoggerFactory.getLogger(UserController::class.java)

    @GetMapping
    fun getAllDataForUser(authentication: Authentication): UserDto {
        logger.debug("GET /user by user {}", authentication.userId)
        val user = userService.findById(authentication.userId)
        val medKits = medKitService.findAllByUser(user.id)
        val medKitDTOs = medKits.map { medKitService.toMedKitDTO(it) }.toSet()
        return UserDto(
            id = user.id,
            medKits = medKitDTOs
        )
    }
}
