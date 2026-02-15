package org.kert0n.medappserver.db.model

import jakarta.persistence.*
import org.jetbrains.annotations.NotNull
import java.util.*

@Entity
@Table(name = "users")
class User(
    @Id
    var id: UUID = UUID.randomUUID(),
    @NotNull
    @Column(nullable = false)
    var hashedKey: String,
    @ManyToMany(fetch = FetchType.LAZY)
    var users: MutableSet<Medbox> = mutableSetOf(),
    @OneToMany(fetch = FetchType.LAZY)
    var myUses:MutableSet<Uses> = mutableSetOf()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as User

        return id == other.id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }
}
