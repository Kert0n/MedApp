package org.kert0n.medappserver.db.repository

import org.kert0n.medappserver.db.model.User
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.springframework.stereotype.Repository
import java.util.*

@Repository
class UserRepository {
    fun findById(id: UUID): Optional<User> = Optional.ofNullable(findByIdOrNull(id))

    fun save(user: User): User = transaction {
        val exists = UsersTable.select(UsersTable.id).where { UsersTable.id eq user.id }.firstOrNull() != null
        if (exists) {
            UsersTable.update({ UsersTable.id eq user.id }) {
                it[hashedKey] = user.hashedKey
            }
        } else {
            UsersTable.insert {
                it[id] = user.id
                it[hashedKey] = user.hashedKey
            }
        }

        user.medKits.forEach { medKit ->
            UserMedKitsTable.insertIgnore {
                it[userId] = user.id
                it[medKitId] = medKit.id
            }
        }
        user
    }

    fun findByIdOrNull(id: UUID): User? = transaction {
        UsersTable.select(UsersTable.columns).where { UsersTable.id eq id }.firstOrNull()?.let(::mapUser)
    }

    fun findByMedKitsId(medId: UUID): List<User> = transaction {
        (UsersTable innerJoin UserMedKitsTable)
            .select(UsersTable.columns)
            .where { UserMedKitsTable.medKitId eq medId }
            .map(::mapUser)
    }

    fun findByUsingsDrugId(drugId: UUID): Set<User> = transaction {
        (UsersTable innerJoin UsingsTable)
            .select(UsersTable.columns)
            .where { UsingsTable.drugId eq drugId }
            .map(::mapUser)
            .toSet()
    }

    fun findByIdWithMedKits(id: UUID): User? = transaction {
        UsersTable.select(UsersTable.columns).where { UsersTable.id eq id }.firstOrNull()?.let { row ->
            mapUser(row).also { user ->
                val medKits = (UserMedKitsTable innerJoin MedKitsTable)
                    .select(MedKitsTable.columns)
                    .where { UserMedKitsTable.userId eq id }
                    .map { org.kert0n.medappserver.db.model.MedKit(id = it[MedKitsTable.id]) }
                user.medKits.addAll(medKits)
            }
        }
    }

    private fun mapUser(row: ResultRow): User =
        User(
            id = row[UsersTable.id],
            hashedKey = row[UsersTable.hashedKey]
        )
}
