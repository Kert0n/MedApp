package org.kert0n.medappserver.db.model

import jakarta.persistence.*
import jakarta.validation.constraints.NotNull
import java.util.*


@Entity
class Uses(

    @EmbeddedId
    var usesKey: UsesKey = UsesKey(),
    @ManyToOne
    @MapsId("userId")
    var user: User,
    @ManyToOne
    @MapsId("drugId")
    var drug: Drug,
    @NotNull
    @Column(nullable = false)
    var pattern: String
)

@Embeddable
class UsesKey {
            /**
             * This UUID is initialized to (0, 0) temporarily.
             * Hibernate will later initialize it with proper values
             * during the persistence lifecycle.
             *
             * The reason for using UUID(0, 0) as the initial value is
             * that the key is not yet set when the entity is created and
             * Hibernate will manage the actual initialization of the key
             * once the object is persisted or queried from the database.
             *
             * It’s important to understand that (0, 0) is just a placeholder
             * and not a meaningful value for this identifier. The key will
             * be updated automatically once the entity is inserted or retrieved,
             * ensuring a valid primary key is used.
             */
    var userId: UUID = UUID(0, 0)
    var drugId: UUID = UUID(0, 0)
}