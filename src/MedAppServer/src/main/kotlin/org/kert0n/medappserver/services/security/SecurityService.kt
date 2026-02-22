package org.kert0n.medappserver.services.security

import com.sksamuel.aedile.core.Cache
import org.kert0n.medappserver.db.model.User
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.stereotype.Service
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.io.encoding.Base64

@Service
class SecurityService(
    private val passwordEncoder: PasswordEncoder,
    private val encoder: JwtEncoder,
    private val decoder: JwtDecoder,
    @Value($$"${authentication.termInMinutes}") private val authenticationTerm: Long,
    @Value($$"${registration.timeout.BanNumber}") private val registrationNumber: Long,
    private val successfulRegistrationsCache: Cache<String, Int>
    ) {

    fun generateKey(size: Int) = Base64.encode(ByteArray(size).also { SecureRandom().nextBytes(it) })
    fun check(raw: String, hashedPassword: String): Boolean = passwordEncoder.matches(raw, hashedPassword)
    fun hashPassword(rawPassword: String): String = passwordEncoder.encode(rawPassword)!!
    fun hashToken(token: String): String =
        Base64.encode(MessageDigest.getInstance("SHA-256").digest(token.toByteArray()))


    fun generateToken(user: User, termInMinutes: Long = authenticationTerm): String {
        val now = Instant.now()
        return encoder.encode(
            JwtEncoderParameters.from(
                JwtClaimsSet.builder().run {
                    issuedAt(now)
                    expiresAt(now.plus(termInMinutes, ChronoUnit.MINUTES))
                    subject(user.id.toString())
                    build()
                }
            )
        ).tokenValue
    }

    // Track successful registrations per IP to throttle automated registrations.
    fun validateRequest(ip: String): Boolean =
        (successfulRegistrationsCache.getOrNull(ip) ?: 0) <= registrationNumber

    fun registerIncrease(ip: String) {
        val current = successfulRegistrationsCache.getOrNull(ip)
        if (current == null) {
            successfulRegistrationsCache.put(ip, 1)
        } else {
            successfulRegistrationsCache[ip] = current + 1
        }
    }
}
