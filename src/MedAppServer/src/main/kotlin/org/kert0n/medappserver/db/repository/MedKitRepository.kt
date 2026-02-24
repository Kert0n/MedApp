package org.kert0n.medappserver.db.repository

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.kert0n.medappserver.db.model.MedKit
import org.kert0n.medappserver.db.table.MedKits
import org.kert0n.medappserver.db.table.UserDrugs
import org.kert0n.medappserver.db.table.UserMedKits
import org.springframework.stereotype.Repository
import java.util.*

@Repository
class MedKitRepository {

    fun save(medKit: MedKit): MedKit {
        MedKits.upsert {
            it[id] = medKit.id
        }
        return medKit
    }

    fun findById(medKitId: UUID): MedKit? {
        return MedKits.selectAll().where { MedKits.id eq medKitId }
            .singleOrNull()
            ?.let { MedKit(id = it[MedKits.id]) }
    }

    fun delete(medKit: MedKit) {
        MedKits.deleteWhere { id eq medKit.id }
    }

    fun findByUsersId(userId: UUID): List<MedKit> {
        return MedKits.innerJoin(UserMedKits, { MedKits.id }, { medKitId })
            .select(MedKits.columns)
            .where { UserMedKits.userId eq userId }
            .map { MedKit(id = it[MedKits.id]) }
    }

    fun findByIdAndUserId(id: UUID, userId: UUID): MedKit? {
        return MedKits.innerJoin(UserMedKits, { MedKits.id }, { medKitId })
            .select(MedKits.columns)
            .where { (MedKits.id eq id) and (UserMedKits.userId eq userId) }
            .singleOrNull()
            ?.let { MedKit(id = it[MedKits.id]) }
    }

    fun findMedKitSummariesByUserId(userId: UUID): List<Triple<UUID, Long, Long>> {
        val userCount = UserMedKits.userId.count()
        val drugCount = UserDrugs.id.count()

        return MedKits
            .innerJoin(UserMedKits, { MedKits.id }, { medKitId })
            .leftJoin(UserDrugs, { MedKits.id }, { medKitId })
            .select(MedKits.id, userCount, drugCount)
            .where {
                MedKits.id inSubQuery (
                    UserMedKits.select(UserMedKits.medKitId)
                        .where { UserMedKits.userId eq userId }
                )
            }
            .groupBy(MedKits.id)
            .map { row ->
                Triple(row[MedKits.id], row[userCount], row[drugCount])
            }
    }

    fun addUserToMedKit(userId: UUID, medKitId: UUID) {
        UserMedKits.insertIgnore {
            it[UserMedKits.userId] = userId
            it[UserMedKits.medKitId] = medKitId
        }
    }

    fun removeUserFromMedKit(userId: UUID, medKitId: UUID) {
        UserMedKits.deleteWhere {
            (UserMedKits.userId eq userId) and (UserMedKits.medKitId eq medKitId)
        }
    }

    fun countUsersInMedKit(medKitId: UUID): Long {
        return UserMedKits.selectAll()
            .where { UserMedKits.medKitId eq medKitId }
            .count()
    }
}