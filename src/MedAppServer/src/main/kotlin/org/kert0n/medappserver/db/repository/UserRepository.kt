package org.kert0n.medappserver.db.repository

import org.jetbrains.exposed.sql.*
import org.kert0n.medappserver.db.model.User
import org.kert0n.medappserver.db.table.UserMedKits
import org.kert0n.medappserver.db.table.Users
import org.kert0n.medappserver.db.table.Usings
import org.springframework.stereotype.Repository
import java.util.*

@Repository
class UserRepository {

    fun save(user: User): User {
        Users.upsert {
            it[id] = user.id
            it[hashedKey] = user.hashedKey
        }
        return user
    }

    fun findById(id: UUID): User? {
        return Users.selectAll().where { Users.id eq id }
            .singleOrNull()
            ?.toUser()
    }

    fun findByMedKitsId(medId: UUID): List<User> {
        return Users.innerJoin(UserMedKits, { Users.id }, { userId })
            .selectAll().where { UserMedKits.medKitId eq medId }
            .map { it.toUser() }
    }

    fun findByUsingsDrugId(drugId: UUID): Set<User> {
        return Users.innerJoin(Usings, { Users.id }, { userId })
            .selectAll().where { Usings.drugId eq drugId }
            .map { it.toUser() }
            .toSet()
    }

    private fun ResultRow.toUser(): User {
        return User(
            id = this[Users.id],
            hashedKey = this[Users.hashedKey]
        )
    }
}