package org.kert0n.medappserver.controller

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.security.Principal

@RestController
@RequestMapping("/med-kit")
class MedBoxController {
    @GetMapping
    fun getAll(principal: Principal): String {
        return principal.name

    }
}