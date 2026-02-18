package org.kert0n.medappserver.controller

import org.kert0n.medappserver.db.model.UserDto
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/user")
class UserController {

    @GetMapping
    fun getAllDataForUser(authentication: Authentication): UserDto {
        return
    }
}