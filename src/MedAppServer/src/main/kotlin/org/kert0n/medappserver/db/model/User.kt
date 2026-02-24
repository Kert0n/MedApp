package org.kert0n.medappserver.db.model

import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.userdetails.UserDetails
import java.util.*

class User(
    var id: UUID = UUID.randomUUID(),
    var hashedKey: String
) : UserDetails {

    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as User

        return id == other.id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }

    override fun getAuthorities(): Collection<GrantedAuthority> {
        return emptyList()
    }

    override fun getPassword(): String {
        return hashedKey
    }

    override fun getUsername(): String {
        return id.toString()
    }

}

