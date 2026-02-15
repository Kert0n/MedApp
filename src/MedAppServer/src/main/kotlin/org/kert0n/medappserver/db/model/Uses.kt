package org.kert0n.medappserver.db.model

import jakarta.persistence.*
import jakarta.validation.constraints.NotNull
import java.io.Serializable
import java.util.*


@Entity
@Table(name = "uses")
class Uses(

    @EmbeddedId
    var usesKey: UsesKey = UsesKey(),
    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("userId")
    @JoinColumn(name = "user_id")
    var user: User,
    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("drugId")
    @JoinColumn(name = "drug_id")
    var drug: Drug,
    @NotNull
    @Column(name = "pattern", nullable = false)
    var pattern: String
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Uses

        return usesKey == other.usesKey
    }

    override fun hashCode(): Int {
        return usesKey.hashCode()
    }
}

@Embeddable
class UsesKey(
    @Column(name = "user_id")
    var userId: UUID = UUID(0, 0),
    @Column(name = "drug_id")
    var drugId: UUID = UUID(0, 0)
) : Serializable {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as UsesKey

        return userId == other.userId && drugId == other.drugId
    }

    override fun hashCode(): Int {
        return Objects.hash(userId, drugId)
    }
}
