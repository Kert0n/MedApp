package org.kert0n.medappserver.db.repository

import org.kert0n.medappserver.db.model.Drug
import org.kert0n.medappserver.db.model.MedKit
import org.kert0n.medappserver.db.model.Using
import org.kert0n.medappserver.db.model.UsingKey
import org.kert0n.medappserver.db.model.User
import org.jetbrains.exposed.v1.core.AndOp
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.springframework.stereotype.Repository
import java.util.*

@Repository
class UsingRepository {
    fun save(using: Using): Using = transaction {
        val exists = UsingsTable.select(UsingsTable.userId).where {
            AndOp(listOf(UsingsTable.userId eq using.user.id, UsingsTable.drugId eq using.drug.id))
        }.firstOrNull() != null
        if (exists) {
            UsingsTable.update({
                AndOp(listOf(UsingsTable.userId eq using.user.id, UsingsTable.drugId eq using.drug.id))
            }) {
                it[plannedAmount] = using.plannedAmount
                it[lastModified] = using.lastModified
                it[createdAt] = using.createdAt
            }
        } else {
            UsingsTable.insert {
                it[userId] = using.user.id
                it[drugId] = using.drug.id
                it[plannedAmount] = using.plannedAmount
                it[lastModified] = using.lastModified
                it[createdAt] = using.createdAt
            }
        }
        using
    }

    fun delete(using: Using) = transaction {
        UsingsTable.deleteWhere {
            AndOp(listOf(UsingsTable.userId eq using.user.id, UsingsTable.drugId eq using.drug.id))
        }
    }

    fun findAllByUserId(userId: UUID): List<Using> = transaction {
        (UsingsTable innerJoin UsersTable innerJoin DrugsTable)
            .select(UsingsTable.columns + UsersTable.columns + DrugsTable.columns)
            .where { UsingsTable.userId eq userId }
            .map(::mapUsing)
    }

    fun findAllByDrugId(drugId: UUID): List<Using> = transaction {
        (UsingsTable innerJoin UsersTable innerJoin DrugsTable)
            .select(UsingsTable.columns + UsersTable.columns + DrugsTable.columns)
            .where { UsingsTable.drugId eq drugId }
            .map(::mapUsing)
    }

    fun findByUserIdAndDrugId(userId: UUID, drugId: UUID): Using? = transaction {
        (UsingsTable innerJoin UsersTable innerJoin DrugsTable)
            .select(UsingsTable.columns + UsersTable.columns + DrugsTable.columns)
            .where { AndOp(listOf(UsingsTable.userId eq userId, UsingsTable.drugId eq drugId)) }
            .firstOrNull()
            ?.let(::mapUsing)
    }

    fun findAllByUserIdWithDrug(userId: UUID): List<Using> = findAllByUserId(userId)

    private fun mapUsing(row: ResultRow): Using {
        val drug = Drug(
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
        return Using(
            usingKey = UsingKey(row[UsingsTable.userId], row[UsingsTable.drugId]),
            user = User(id = row[UsersTable.id], hashedKey = row[UsersTable.hashedKey]),
            drug = drug,
            plannedAmount = row[UsingsTable.plannedAmount],
            lastModified = row[UsingsTable.lastModified],
            createdAt = row[UsingsTable.createdAt]
        )
    }
}
