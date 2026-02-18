package org.kert0n.medappserver.controller

import org.kert0n.medappserver.db.model.MedKit
import org.kert0n.medappserver.db.model.MedKitDTO
import org.kert0n.medappserver.services.MedKitService
import org.kert0n.medappserver.services.UserService
import org.kert0n.medappserver.services.userId
import org.springframework.security.core.Authentication
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.util.*

@RestController
@RequestMapping("/med-kit")
class MedBoxController(
    private val medKitService: MedKitService,
    private val userService: UserService
) {




    @PostMapping
    fun createNew(authentication: Authentication): UUID {
        return medKitService.createNew(authentication.userId).id
    }

    @GetMapping("/{id}")
    fun getConcrete(authentication: Authentication, @PathVariable id: UUID): MedKitDTO {
        userService.findById(authentication.userId).medKits.find { med -> med.id == id }.ToDto()
    }


}