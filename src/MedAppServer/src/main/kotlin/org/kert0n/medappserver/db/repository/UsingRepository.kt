package org.kert0n.medappserver.db.repository

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.kert0n.medappserver.db.model.Using
import org.kert0n.medappserver.db.table.Usings
import org.springframework.stereotype.Repository
import java.util.*

@Repository
class UsingRepository {

    fun save(using: Using): Using {
        val exists = Usings.selectAll()
            .where { (Usings.userId eq using.userId) and (Usings.drugId eq using.drugId) }
            .count() > 0

        if (exists) {
            Usings.update({ (Usings.userId eq using.userId) and (Usings.drugId eq using.drugId) }) {
                it[plannedAmount] = using.plannedAmount
                it[lastModified] = using.lastModified
                it[createdAt] = using.createdAt
            }
        } else {
            Usings.insert {
                it[userId] = using.userId
                it[drugId] = using.drugId
                it[plannedAmount] = using.plannedAmount
                it[lastModified] = using.lastModified
                it[createdAt] = using.createdAt
            }
        }
        return using
    }

    fun delete(using: Using) {
        Usings.deleteWhere {
            (userId eq using.userId) and (drugId eq using.drugId)
        }
    }

    fun deleteByUserIdAndDrugId(userId: UUID, drugId: UUID) {
        Usings.deleteWhere {
            (Usings.userId eq userId) and (Usings.drugId eq drugId)
        }
    }

    fun findAllByUserId(userId: UUID): List<Using> {
        return Usings.selectAll().where { Usings.userId eq userId }
            .map { it.toUsing() }
    }

    fun findAllByDrugId(drugId: UUID): List<Using> {
        return Usings.selectAll().where { Usings.drugId eq drugId }
            .map { it.toUsing() }
    }

    fun findByUserIdAndDrugId(userId: UUID, drugId: UUID): Using? {
        return Usings.selectAll()
            .where { (Usings.userId eq userId) and (Usings.drugId eq drugId) }
            .singleOrNull()
            ?.toUsing()
    }

    private fun ResultRow.toUsing(): Using {
        return Using(
            userId = this[Usings.userId],
            drugId = this[Usings.drugId],
            plannedAmount = this[Usings.plannedAmount],
            lastModified = this[Usings.lastModified],
            createdAt = this[Usings.createdAt]
        )
    }
}