package org.kert0n.medappserver.services.security

import org.springframework.security.core.Authentication
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.temporal.ChronoUnit


@Service
class TokenService(private val encoder: JwtEncoder) {


    fun generateToken(authentication: Authentication): String {
        val now = Instant.now()
        val claims = JwtClaimsSet.builder()
            .issuedAt(now)
            .expiresAt(now.plus(1, ChronoUnit.HOURS))
            .subject(authentication.name)
            .build()
        return encoder.encode(JwtEncoderParameters.from(claims)).tokenValue
    }
}