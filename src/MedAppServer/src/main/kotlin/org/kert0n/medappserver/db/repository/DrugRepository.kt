@file:Suppress("FunctionName")

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
class DrugRepository {
    fun findById(id: UUID): Optional<Drug> = Optional.ofNullable(findByIdOrNull(id))

    fun save(drug: Drug): Drug = transaction {
        val exists = DrugsTable.select(DrugsTable.id).where { DrugsTable.id eq drug.id }.firstOrNull() != null
        if (exists) {
            DrugsTable.update({ DrugsTable.id eq drug.id }) {
                it[name] = drug.name
                it[quantity] = drug.quantity
                it[quantityUnit] = drug.quantityUnit
                it[formType] = drug.formType
                it[category] = drug.category
                it[manufacturer] = drug.manufacturer
                it[country] = drug.country
                it[description] = drug.description
                it[medKitId] = drug.medKit.id
            }
        } else {
            DrugsTable.insert {
                it[id] = drug.id
                it[name] = drug.name
                it[quantity] = drug.quantity
                it[quantityUnit] = drug.quantityUnit
                it[formType] = drug.formType
                it[category] = drug.category
                it[manufacturer] = drug.manufacturer
                it[country] = drug.country
                it[description] = drug.description
                it[medKitId] = drug.medKit.id
            }
        }
        drug.usings.forEach { using ->
            UsingsTable.update({ AndOp(listOf(UsingsTable.userId eq using.user.id, UsingsTable.drugId eq using.drug.id)) }) {
                it[plannedAmount] = using.plannedAmount
                it[lastModified] = using.lastModified
                it[createdAt] = using.createdAt
            }
        }
        drug
    }

    fun delete(drug: Drug) = transaction {
        DrugsTable.deleteWhere { DrugsTable.id eq drug.id }
    }

    fun findByIdOrNull(id: UUID): Drug? = transaction {
        DrugsTable.select(DrugsTable.columns).where { DrugsTable.id eq id }.firstOrNull()?.let(::mapDrug)
    }

    fun findAllByMedKitId(medKitId: UUID): List<Drug> = transaction {
        DrugsTable.select(DrugsTable.columns).where { DrugsTable.medKitId eq medKitId }.map(::mapDrug)
    }

    fun findByUsingsUserId(userId: UUID): List<Drug> = transaction {
        (DrugsTable innerJoin UsingsTable)
            .select(DrugsTable.columns)
            .where { UsingsTable.userId eq userId }
            .withDistinct()
            .map(::mapDrug)
    }

    fun findByIdAndMedKitUsersId(drugId: UUID, userId: UUID): Drug? = transaction {
        val drugRow = DrugsTable
            .select(DrugsTable.columns)
            .where { DrugsTable.id eq drugId }
            .firstOrNull() ?: return@transaction null
        val hasAccess = UserMedKitsTable
            .select(UserMedKitsTable.userId)
            .where { AndOp(listOf(UserMedKitsTable.userId eq userId, UserMedKitsTable.medKitId eq drugRow[DrugsTable.medKitId])) }
            .firstOrNull() != null
        if (!hasAccess) {
            return@transaction null
        }
        val drug = mapDrug(drugRow)
        val usings = (UsingsTable innerJoin UsersTable)
            .select(UsingsTable.columns + UsersTable.columns)
            .where { UsingsTable.drugId eq drugId }
            .map { row ->
                Using(
                    usingKey = UsingKey(row[UsingsTable.userId], row[UsingsTable.drugId]),
                    user = User(id = row[UsersTable.id], hashedKey = row[UsersTable.hashedKey]),
                    drug = drug,
                    plannedAmount = row[UsingsTable.plannedAmount],
                    lastModified = row[UsingsTable.lastModified],
                    createdAt = row[UsingsTable.createdAt]
                )
            }
        drug.usings.addAll(usings)
        drug
    }

    fun sumPlannedAmount(drugId: UUID): Double = transaction {
        UsingsTable.select(UsingsTable.plannedAmount).where { UsingsTable.drugId eq drugId }.sumOf { it[UsingsTable.plannedAmount] }
    }

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
}
