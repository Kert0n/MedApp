package org.kert0n.medappserver.controller

import org.kert0n.medappserver.db.model.UserDto
import org.kert0n.medappserver.services.MedKitService
import org.kert0n.medappserver.services.UsingService
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
    private val medKitService: MedKitService,
    private val usingService: UsingService
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

    /**
     * Get all treatment plans for the authenticated user
     */
    @GetMapping("/plans")
    fun getAllTreatmentPlans(authentication: Authentication): Map<String, Any> {
        val usings = usingService.getAllUsingsForUser(authentication.userId)
        return mapOf(
            "plans" to usings.map { using ->
                mapOf(
                    "drugId" to using.drug.id,
                    "drugName" to using.drug.name,
                    "plannedAmount" to using.plannedAmount,
                    "lastUsed" to using.lastUsed,
                    "createdAt" to using.createdAt
                )
            }
        )
    }
}