@file:Suppress("FunctionName")

package org.kert0n.medappserver.db.repository

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.kert0n.medappserver.db.model.Drug
import org.kert0n.medappserver.db.table.UserDrugs
import org.kert0n.medappserver.db.table.UserMedKits
import org.kert0n.medappserver.db.table.Usings
import org.springframework.stereotype.Repository
import java.util.*

@Repository
class DrugRepository {

    fun save(drug: Drug): Drug {
        UserDrugs.upsert {
            it[id] = drug.id
            it[name] = drug.name
            it[quantity] = drug.quantity
            it[quantityUnit] = drug.quantityUnit
            it[formType] = drug.formType
            it[category] = drug.category
            it[manufacturer] = drug.manufacturer
            it[country] = drug.country
            it[description] = drug.description
            it[medKitId] = drug.medKitId
        }
        return drug
    }

    fun findById(drugId: UUID): Drug? {
        return UserDrugs.selectAll().where { UserDrugs.id eq drugId }
            .singleOrNull()
            ?.toDrug()
    }

    fun delete(drug: Drug) {
        UserDrugs.deleteWhere { id eq drug.id }
    }

    fun findAllByMedKitId(medKitId: UUID): List<Drug> {
        return UserDrugs.selectAll().where { UserDrugs.medKitId eq medKitId }
            .map { it.toDrug() }
    }

    fun findByUsingsUserId(userId: UUID): List<Drug> {
        return UserDrugs.innerJoin(Usings, { UserDrugs.id }, { drugId })
            .select(UserDrugs.columns)
            .where { Usings.userId eq userId }
            .withDistinct()
            .map { it.toDrug() }
    }

    fun findByIdAndMedKitUsersId(drugId: UUID, userId: UUID): Drug? {
        return UserDrugs.innerJoin(UserMedKits, { UserDrugs.medKitId }, { medKitId })
            .select(UserDrugs.columns)
            .where { (UserDrugs.id eq drugId) and (UserMedKits.userId eq userId) }
            .singleOrNull()
            ?.toDrug()
    }

    fun sumPlannedAmount(drugId: UUID): Double {
        return Usings.select(Usings.plannedAmount.sum())
            .where { Usings.drugId eq drugId }
            .singleOrNull()
            ?.get(Usings.plannedAmount.sum()) ?: 0.0
    }

    private fun ResultRow.toDrug(): Drug {
        return Drug(
            id = this[UserDrugs.id],
            name = this[UserDrugs.name],
            quantity = this[UserDrugs.quantity],
            quantityUnit = this[UserDrugs.quantityUnit],
            formType = this[UserDrugs.formType],
            category = this[UserDrugs.category],
            manufacturer = this[UserDrugs.manufacturer],
            country = this[UserDrugs.country],
            description = this[UserDrugs.description],
            medKitId = this[UserDrugs.medKitId]
        )
    }
}