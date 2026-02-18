package org.kert0n.medappserver.controller

import org.kert0n.medappserver.db.model.UserDto
import org.kert0n.medappserver.services.MedKitService
import org.kert0n.medappserver.services.UserService
import org.kert0n.medappserver.services.userId
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

    /**
     * Get all data for the authenticated user (medicine kits with drugs)
     */
    @GetMapping
    fun getAllDataForUser(authentication: Authentication): UserDto {
        val userId = authentication.userId
        val user = userService.findById(userId)
        val medKits = medKitService.getAllMedKitsForUser(userId)
        
        return UserDto(
            id = user.id,
            medKits = medKits.toSet()
        )
    }
}