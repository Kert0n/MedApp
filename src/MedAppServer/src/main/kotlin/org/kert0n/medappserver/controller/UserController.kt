package org.kert0n.medappserver.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
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
@Tag(name = "User Data", description = "APIs for retrieving user data and medicine kits")
class UserController(
    private val userService: UserService,
    private val medKitService: MedKitService
) {

    @Operation(
        summary = "Get all user data",
        description = "Retrieves complete user data including all medicine kits and drugs"
    )
    @ApiResponse(responseCode = "200", description = "User data retrieved successfully")
    @GetMapping
    fun getAllDataForUser(@Parameter(hidden = true) authentication: Authentication): UserDto {
        val userId = authentication.userId
        val user = userService.findById(userId)
        val medKits = medKitService.getAllMedKitsForUser(userId)
        
        return UserDto(
            id = user.id,
            medKits = medKits.toSet()
        )
    }
}