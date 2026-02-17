package org.kert0n.medappserver.services.security

import org.kert0n.medappserver.db.model.User
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*


@Service
class TokenService(
    private val encoder: JwtEncoder,
    private val decoder: JwtDecoder,
) {


    fun generateToken(user: User, termInMinute: Long): String {
        val now = Instant.now()
        return encoder.encode(
            JwtEncoderParameters.from(
                JwtClaimsSet.builder().run {
                    issuedAt(now)
                    expiresAt(now.plus(termInMinute, ChronoUnit.MINUTES))
                    subject(user.id.toString())
                    build()
                }
            )
        ).tokenValue
    }


}