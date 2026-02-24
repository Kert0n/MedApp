package org.kert0n.medappserver.db.repository

import org.kert0n.medappserver.db.model.Drug
import org.kert0n.medappserver.db.model.MedKit
import org.kert0n.medappserver.db.model.User
import org.jetbrains.exposed.v1.core.AndOp
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.springframework.stereotype.Repository
import java.util.*

@Repository
class MedKitRepository {
    fun findById(id: UUID): Optional<MedKit> = Optional.ofNullable(findByIdOrNull(id))

    fun save(medKit: MedKit): MedKit = transaction {
        val exists = MedKitsTable.select(MedKitsTable.id).where { MedKitsTable.id eq medKit.id }.firstOrNull() != null
        if (!exists) {
            MedKitsTable.insert { it[id] = medKit.id }
        }
        val targetUserIds = medKit.users.map { it.id }.toSet()
        val existingUserIds = UserMedKitsTable
            .select(UserMedKitsTable.userId)
            .where { UserMedKitsTable.medKitId eq medKit.id }
            .map { it[UserMedKitsTable.userId] }
            .toSet()
        if (existingUserIds.isNotEmpty()) {
            val staleIds = existingUserIds - targetUserIds
            if (staleIds.isNotEmpty()) {
                UserMedKitsTable.deleteWhere {
                    AndOp(
                        listOf(
                            UserMedKitsTable.medKitId eq medKit.id,
                            UserMedKitsTable.userId inList staleIds
                        )
                    )
                }
            }
        }
        targetUserIds.forEach { uid ->
            UserMedKitsTable.insertIgnore {
                it[userId] = uid
                it[medKitId] = medKit.id
            }
        }
        medKit
    }

    fun delete(medKit: MedKit) = transaction {
        MedKitsTable.deleteWhere { MedKitsTable.id eq medKit.id }
    }

    fun findByIdOrNull(id: UUID): MedKit? = transaction {
        MedKitsTable.select(MedKitsTable.columns).where { MedKitsTable.id eq id }.firstOrNull()?.let(::mapMedKit)?.also(::loadUsers)
    }

    fun findByUsersId(userId: UUID): List<MedKit> = transaction {
        (MedKitsTable innerJoin UserMedKitsTable)
            .select(MedKitsTable.columns)
            .where { UserMedKitsTable.userId eq userId }
            .map(::mapMedKit)
    }

    fun findByIdWithDrugs(id: UUID): MedKit? = transaction {
        findByIdOrNull(id)?.also { medKit ->
            val drugs = DrugsTable.select(DrugsTable.columns).where { DrugsTable.medKitId eq id }.map(::mapDrug)
            medKit.drugs.addAll(drugs)
        }
    }

    fun findByIdWithUsers(id: UUID): MedKit? = transaction {
        findByIdOrNull(id)
    }

    fun findByIdAndUserId(id: UUID, userId: UUID): MedKit? = transaction {
        (MedKitsTable innerJoin UserMedKitsTable)
            .select(MedKitsTable.columns)
            .where { AndOp(listOf(MedKitsTable.id eq id, UserMedKitsTable.userId eq userId)) }
            .firstOrNull()
            ?.let(::mapMedKit)
            ?.also { medKit ->
                val users = (UsersTable innerJoin UserMedKitsTable)
                    .select(UsersTable.columns)
                    .where { UserMedKitsTable.medKitId eq id }
                    .map(::mapUser)
                medKit.users.addAll(users)
            }
    }

    fun findMedKitSummariesByUserId(userId: UUID): List<Array<Any>> = transaction {
        findByUsersId(userId).map { medKit ->
            val usersCount = UserMedKitsTable.select(UserMedKitsTable.userId).where { UserMedKitsTable.medKitId eq medKit.id }.count().toInt()
            val drugsCount = DrugsTable.select(DrugsTable.id).where { DrugsTable.medKitId eq medKit.id }.count().toInt()
            arrayOf(medKit.id, usersCount, drugsCount)
        }
    }

    private fun mapMedKit(row: ResultRow): MedKit = MedKit(id = row[MedKitsTable.id])

    private fun mapUser(row: ResultRow): User = User(
        id = row[UsersTable.id],
        hashedKey = row[UsersTable.hashedKey]
    )

    private fun mapDrug(row: ResultRow): Drug = Drug(
        id = row[DrugsTable.id],
        name = row[DrugsTable.name],
        quantity = row[DrugsTable.quantity],
        quantityUnit = row[DrugsTable.quantityUnit],
        formType = row[DrugsTable.formType],
        category = row[DrugsTable.category],
        manufacturer = row[DrugsTable.manufacturer],
        country = row[DrugsTable.country],
        description = row[DrugsTable.description],
        medKit = MedKit(id = row[DrugsTable.medKitId])
    )

    private fun loadUsers(medKit: MedKit): MedKit {
        val users = (UsersTable innerJoin UserMedKitsTable)
            .select(UsersTable.columns)
            .where { UserMedKitsTable.medKitId eq medKit.id }
            .map(::mapUser)
        medKit.users.addAll(users)
        return medKit
    }
}
