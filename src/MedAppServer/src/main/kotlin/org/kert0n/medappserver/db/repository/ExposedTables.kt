package org.kert0n.medappserver.db.repository

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.timestamp

object UsersTable : Table("users") {
    val id = uuid("id")
    val hashedKey = varchar("hashed_key", 255)
    override val primaryKey = PrimaryKey(id)
}

object MedKitsTable : Table("med_kits") {
    val id = uuid("id")
    override val primaryKey = PrimaryKey(id)
}

object UserMedKitsTable : Table("user_med_kits") {
    val userId = uuid("user_id").references(UsersTable.id, onDelete = org.jetbrains.exposed.v1.core.ReferenceOption.CASCADE)
    val medKitId = uuid("med_kit_id").references(MedKitsTable.id, onDelete = org.jetbrains.exposed.v1.core.ReferenceOption.CASCADE)
    override val primaryKey = PrimaryKey(userId, medKitId)
}

object DrugsTable : Table("user_drugs") {
    val id = uuid("id")
    val name = varchar("name", 300)
    val quantity = double("quantity")
    val quantityUnit = varchar("quantity_unit", 50)
    val formType = varchar("form_type", 100).nullable()
    val category = varchar("category", 200).nullable()
    val manufacturer = varchar("manufacturer", 300).nullable()
    val country = varchar("country", 100).nullable()
    val description = text("description").nullable()
    val medKitId = uuid("med_kit_id").references(MedKitsTable.id, onDelete = org.jetbrains.exposed.v1.core.ReferenceOption.CASCADE)
    override val primaryKey = PrimaryKey(id)
}

object UsingsTable : Table("usings") {
    val userId = uuid("user_id").references(UsersTable.id, onDelete = org.jetbrains.exposed.v1.core.ReferenceOption.CASCADE)
    val drugId = uuid("drug_id").references(DrugsTable.id, onDelete = org.jetbrains.exposed.v1.core.ReferenceOption.CASCADE)
    val plannedAmount = double("planned_amount")
    val lastModified = timestamp("last_modified")
    val createdAt = timestamp("created_at")
    override val primaryKey = PrimaryKey(userId, drugId)
}

object FormTypesTable : Table("form_types") {
    val id = uuid("id")
    val name = varchar("name", 100).uniqueIndex("form_types_name_key")
    override val primaryKey = PrimaryKey(id)
}

object QuantityUnitsTable : Table("quantity_units") {
    val id = uuid("id")
    val name = varchar("name", 30).uniqueIndex("quantity_units_name_key")
    override val primaryKey = PrimaryKey(id)
}

object ParsedDrugsTable : Table("parsed_drugs") {
    val id = uuid("id")
    val name = varchar("name", 300)
    val formTypeId = optReference("form_type_id", FormTypesTable.id, onDelete = org.jetbrains.exposed.v1.core.ReferenceOption.SET_NULL)
    val quantity = integer("quantity").nullable()
    val quantityUnitId = optReference("quantity_unit_id", QuantityUnitsTable.id, onDelete = org.jetbrains.exposed.v1.core.ReferenceOption.SET_NULL)
    val activeSubstance = varchar("active_substance", 300).nullable()
    val category = varchar("category", 300).nullable()
    val manufacturer = varchar("manufacturer", 300)
    val country = varchar("country", 100).nullable()
    val description = text("description").nullable()
    val otc = bool("otc")
    override val primaryKey = PrimaryKey(id)
}
