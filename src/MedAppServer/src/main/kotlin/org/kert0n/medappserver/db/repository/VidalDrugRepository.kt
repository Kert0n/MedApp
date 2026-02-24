package org.kert0n.medappserver.db.repository

import org.kert0n.medappserver.db.model.parsed.FormType
import org.kert0n.medappserver.db.model.parsed.QuantityUnit
import org.kert0n.medappserver.db.model.parsed.VidalDrug
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.springframework.stereotype.Repository
import java.util.*

@Repository
class VidalDrugRepository {
    fun saveAll(drugs: List<VidalDrug>): List<VidalDrug> = transaction {
        drugs.forEach { saveInternal(it) }
        drugs
    }

    fun deleteAll() = transaction {
        ParsedDrugsTable.deleteAll()
    }

    fun findById(id: UUID): Optional<VidalDrug> = Optional.ofNullable(transaction {
        (ParsedDrugsTable leftJoin FormTypesTable leftJoin QuantityUnitsTable)
            .select(ParsedDrugsTable.columns + FormTypesTable.columns + QuantityUnitsTable.columns)
            .where { ParsedDrugsTable.id eq id }
            .firstOrNull()
            ?.let(::mapVidalDrug)
    })

    fun fuzzySearchByName(searchTerm: String, limit: Int = 10): List<VidalDrug> = transaction {
        if (searchTerm.isBlank()) {
            return@transaction emptyList()
        }
        val escaped = searchTerm
            .replace("\\", "\\\\")
            .replace("'", "''")
        val sql = """
            SELECT pd.*, ft.id AS ft_id, ft.name AS ft_name, qu.id AS qu_id, qu.name AS qu_name
            FROM parsed_drugs pd
            LEFT JOIN form_types ft ON pd.form_type_id = ft.id
            LEFT JOIN quantity_units qu ON pd.quantity_unit_id = qu.id
            WHERE pd.name ILIKE '%$escaped%'
               OR similarity(LOWER(pd.name), LOWER('$escaped')) > 0.3
            ORDER BY
                CASE
                    WHEN LOWER(pd.name) = LOWER('$escaped') THEN 0
                    WHEN pd.name ILIKE '$escaped%' THEN 1
                    WHEN pd.name ILIKE '%$escaped%' THEN 2
                    ELSE 3
                END,
                similarity(LOWER(pd.name), LOWER('$escaped')) DESC,
                pd.name
            LIMIT $limit
        """.trimIndent()
        val result = mutableListOf<VidalDrug>()
        exec(sql) { rs ->
            while (rs.next()) {
                val formTypeId = rs.getObject("ft_id", UUID::class.java)
                val quantityUnitId = rs.getObject("qu_id", UUID::class.java)
                result += VidalDrug(
                    id = rs.getObject("id", UUID::class.java),
                    name = rs.getString("name"),
                    formType = formTypeId?.let { FormType(id = it, name = rs.getString("ft_name")) },
                    quantity = rs.getInt("quantity").takeIf { !rs.wasNull() },
                    quantityUnit = quantityUnitId?.let { QuantityUnit(id = it, name = rs.getString("qu_name")) },
                    activeSubstance = rs.getString("active_substance"),
                    category = rs.getString("category"),
                    manufacturer = rs.getString("manufacturer"),
                    country = rs.getString("country"),
                    description = rs.getString("description"),
                    otc = rs.getBoolean("otc")
                )
            }
            null
        }
        result
    }

    private fun saveInternal(drug: VidalDrug) {
        val exists = ParsedDrugsTable.select(ParsedDrugsTable.id).where { ParsedDrugsTable.id eq drug.id }.firstOrNull() != null
        if (drug.formType != null) {
            FormTypesTable.insertIgnore {
                it[id] = drug.formType!!.id
                it[name] = drug.formType!!.name
            }
        }
        if (drug.quantityUnit != null) {
            QuantityUnitsTable.insertIgnore {
                it[id] = drug.quantityUnit!!.id
                it[name] = drug.quantityUnit!!.name
            }
        }
        if (exists) {
            ParsedDrugsTable.update({ ParsedDrugsTable.id eq drug.id }) {
                it[name] = drug.name
                it[formTypeId] = drug.formType?.id
                it[quantity] = drug.quantity
                it[quantityUnitId] = drug.quantityUnit?.id
                it[activeSubstance] = drug.activeSubstance
                it[category] = drug.category
                it[manufacturer] = drug.manufacturer
                it[country] = drug.country
                it[description] = drug.description
                it[otc] = drug.otc
            }
        } else {
            ParsedDrugsTable.insert {
                it[id] = drug.id
                it[name] = drug.name
                it[formTypeId] = drug.formType?.id
                it[quantity] = drug.quantity
                it[quantityUnitId] = drug.quantityUnit?.id
                it[activeSubstance] = drug.activeSubstance
                it[category] = drug.category
                it[manufacturer] = drug.manufacturer
                it[country] = drug.country
                it[description] = drug.description
                it[otc] = drug.otc
            }
        }
    }

    private fun mapVidalDrug(row: ResultRow): VidalDrug {
        val formTypeId = row.getOrNull(FormTypesTable.id)
        val quantityUnitId = row.getOrNull(QuantityUnitsTable.id)
        return VidalDrug(
            id = row[ParsedDrugsTable.id],
            name = row[ParsedDrugsTable.name],
            formType = formTypeId?.let { FormType(id = it, name = row[FormTypesTable.name]) },
            quantity = row[ParsedDrugsTable.quantity],
            quantityUnit = quantityUnitId?.let { QuantityUnit(id = it, name = row[QuantityUnitsTable.name]) },
            activeSubstance = row[ParsedDrugsTable.activeSubstance],
            category = row[ParsedDrugsTable.category],
            manufacturer = row[ParsedDrugsTable.manufacturer],
            country = row[ParsedDrugsTable.country],
            description = row[ParsedDrugsTable.description],
            otc = row[ParsedDrugsTable.otc]
        )
    }
}
