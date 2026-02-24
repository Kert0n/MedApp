package org.kert0n.medappserver.db.repository

import org.jetbrains.exposed.sql.*
import org.kert0n.medappserver.db.model.parsed.FormType
import org.kert0n.medappserver.db.model.parsed.QuantityUnit
import org.kert0n.medappserver.db.model.parsed.VidalDrug
import org.kert0n.medappserver.db.table.ParsedDrugs
import org.kert0n.medappserver.db.table.ParsedFormTypes
import org.kert0n.medappserver.db.table.ParsedQuantityUnits
import org.springframework.stereotype.Repository
import java.util.*

@Repository
class VidalDrugRepository {

    fun save(drug: VidalDrug): VidalDrug {
        ParsedDrugs.upsert {
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
        return drug
    }

    fun saveAll(drugs: List<VidalDrug>): List<VidalDrug> {
        drugs.forEach { save(it) }
        return drugs
    }

    fun findById(id: UUID): Optional<VidalDrug> {
        val result = ParsedDrugs
            .leftJoin(ParsedFormTypes, { ParsedDrugs.formTypeId }, { ParsedFormTypes.id })
            .leftJoin(ParsedQuantityUnits, { ParsedDrugs.quantityUnitId }, { ParsedQuantityUnits.id })
            .selectAll()
            .where { ParsedDrugs.id eq id }
            .singleOrNull()
            ?.toVidalDrug()
        return Optional.ofNullable(result)
    }

    fun deleteAll() {
        ParsedDrugs.deleteAll()
    }

    // Native PostgreSQL fuzzy search using pg_trgm trigram similarity + ILIKE.
    // This uses raw JDBC because the pg_trgm similarity() function and the complex
    // ORDER BY with CASE expressions cannot be expressed in Exposed's DSL.
    fun fuzzySearchByName(searchTerm: String, limit: Int = 10): List<VidalDrug> {
        val sql = """
            SELECT pd.*, ft.id as ft_id, ft.name as ft_name, qu.id as qu_id, qu.name as qu_name
            FROM parsed_drugs pd
            LEFT JOIN form_types ft ON pd.form_type_id = ft.id
            LEFT JOIN quantity_units qu ON pd.quantity_unit_id = qu.id
            WHERE pd.name ILIKE CONCAT('%', ?, '%')
               OR similarity(LOWER(pd.name), LOWER(?)) > 0.3
            ORDER BY 
                CASE 
                    WHEN LOWER(pd.name) = LOWER(?) THEN 0
                    WHEN pd.name ILIKE CONCAT(?, '%') THEN 1
                    WHEN pd.name ILIKE CONCAT('%', ?, '%') THEN 2
                    ELSE 3
                END,
                similarity(LOWER(pd.name), LOWER(?)) DESC,
                pd.name
            LIMIT ?
        """.trimIndent()

        val results = mutableListOf<VidalDrug>()
        val conn = org.jetbrains.exposed.sql.transactions.TransactionManager.current().connection
        val stmt = conn.prepareStatement(sql, false)
        stmt.set(1, searchTerm)
        stmt.set(2, searchTerm)
        stmt.set(3, searchTerm)
        stmt.set(4, searchTerm)
        stmt.set(5, searchTerm)
        stmt.set(6, searchTerm)
        stmt.set(7, limit)

        val rs = stmt.executeQuery()
        while (rs.next()) {
            val ftId = rs.getObject("ft_id")
            val ftName = rs.getObject("ft_name")
            val formType = if (ftId != null && ftName != null) {
                FormType(id = ftId as UUID, name = ftName as String)
            } else null

            val quId = rs.getObject("qu_id")
            val quName = rs.getObject("qu_name")
            val quantityUnit = if (quId != null && quName != null) {
                QuantityUnit(id = quId as UUID, name = quName as String)
            } else null

            val qty = rs.getObject("quantity")

            results.add(
                VidalDrug(
                    id = rs.getObject("id") as UUID,
                    name = rs.getString("name"),
                    formType = formType,
                    quantity = qty as? Int,
                    quantityUnit = quantityUnit,
                    activeSubstance = rs.getString("active_substance"),
                    category = rs.getString("category"),
                    manufacturer = rs.getString("manufacturer"),
                    country = rs.getString("country"),
                    description = rs.getString("description"),
                    otc = rs.getBoolean("otc")
                )
            )
        }
        return results
    }

    private fun ResultRow.toVidalDrug(): VidalDrug {
        val formType = this.getOrNull(ParsedFormTypes.id)?.let { ftId ->
            FormType(id = ftId, name = this[ParsedFormTypes.name])
        }
        val quantityUnit = this.getOrNull(ParsedQuantityUnits.id)?.let { quId ->
            QuantityUnit(id = quId, name = this[ParsedQuantityUnits.name])
        }

        return VidalDrug(
            id = this[ParsedDrugs.id],
            name = this[ParsedDrugs.name],
            formType = formType,
            quantity = this[ParsedDrugs.quantity],
            quantityUnit = quantityUnit,
            activeSubstance = this[ParsedDrugs.activeSubstance],
            category = this[ParsedDrugs.category],
            manufacturer = this[ParsedDrugs.manufacturer],
            country = this[ParsedDrugs.country],
            description = this[ParsedDrugs.description],
            otc = this[ParsedDrugs.otc]
        )
    }
}
