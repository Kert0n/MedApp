package org.kert0n.medappserver.db.model

import jakarta.persistence.*
import java.util.*

@Entity
@Table(name = "medboxes")
class Medbox(
    @Id
    @Column(name = "id", nullable = false)
    var id: UUID = UUID.randomUUID(),
    @ManyToMany(mappedBy = "medboxes", fetch = FetchType.LAZY)
    var users: MutableSet<User> = mutableSetOf()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Medbox

        return id == other.id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }
}