package org.kert0n.medappserver.controller

import org.kert0n.medappserver.services.MedKitService
import org.kert0n.medappserver.services.userId
import org.slf4j.LoggerFactory
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.*

@RestController
@RequestMapping("/user")
class UserController(
    private val medKitService: MedKitService
) {

    private val logger = LoggerFactory.getLogger(UserController::class.java)

    @GetMapping
    fun getAllDataForUser(authentication: Authentication): UserDto {
        logger.debug("GET /user by user {}", authentication.userId)
        val medKitDTOs = medKitService.findAllByUser(authentication.userId).map { medKitService.toMedKitDTO(it) }.toSet()
        return UserDto(
            id = authentication.userId,
            medKits = medKitDTOs
        )
    }
}
data class UserDto(
    val id: UUID,
    val medKits: Set<MedKitDTO>
)