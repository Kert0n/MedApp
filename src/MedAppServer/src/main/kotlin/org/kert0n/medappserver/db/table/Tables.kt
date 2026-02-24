package org.kert0n.medappserver.db.table

import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp

object Users : Table("users") {
    val id = uuid("id")
    val hashedKey = varchar("hashed_key", 255)
    override val primaryKey = PrimaryKey(id)
}

object MedKits : Table("med_kits") {
    val id = uuid("id")
    override val primaryKey = PrimaryKey(id)
}

object UserMedKits : Table("user_med_kits") {
    val userId = uuid("user_id").references(Users.id, onDelete = ReferenceOption.CASCADE)
    val medKitId = uuid("med_kit_id").references(MedKits.id, onDelete = ReferenceOption.CASCADE)
    override val primaryKey = PrimaryKey(userId, medKitId)
}

object UserDrugs : Table("user_drugs") {
    val id = uuid("id")
    val name = varchar("name", 300)
    val quantity = double("quantity")
    val quantityUnit = varchar("quantity_unit", 50)
    val formType = varchar("form_type", 100).nullable()
    val category = varchar("category", 200).nullable()
    val manufacturer = varchar("manufacturer", 300).nullable()
    val country = varchar("country", 100).nullable()
    val description = text("description").nullable()
    val medKitId = uuid("med_kit_id").references(MedKits.id, onDelete = ReferenceOption.CASCADE)
    override val primaryKey = PrimaryKey(id)
}

object Usings : Table("usings") {
    val userId = uuid("user_id").references(Users.id, onDelete = ReferenceOption.CASCADE)
    val drugId = uuid("drug_id").references(UserDrugs.id, onDelete = ReferenceOption.CASCADE)
    val plannedAmount = double("planned_amount")
    val lastModified = timestamp("last_modified")
    val createdAt = timestamp("created_at")
    override val primaryKey = PrimaryKey(userId, drugId)
}

object ParsedFormTypes : Table("form_types") {
    val id = uuid("id")
    val name = varchar("name", 100).uniqueIndex("form_types_name_key")
    override val primaryKey = PrimaryKey(id)
}

object ParsedQuantityUnits : Table("quantity_units") {
    val id = uuid("id")
    val name = varchar("name", 30).uniqueIndex("quantity_units_name_key")
    override val primaryKey = PrimaryKey(id)
}

object ParsedDrugs : Table("parsed_drugs") {
    val id = uuid("id")
    val name = varchar("name", 300)
    val formTypeId = uuid("form_type_id").references(ParsedFormTypes.id).nullable()
    val quantity = integer("quantity").nullable()
    val quantityUnitId = uuid("quantity_unit_id").references(ParsedQuantityUnits.id).nullable()
    val activeSubstance = varchar("active_substance", 300).nullable()
    val category = varchar("category", 300).nullable()
    val manufacturer = varchar("manufacturer", 300)
    val country = varchar("country", 100).nullable()
    val description = text("description").nullable()
    val otc = bool("otc")
    override val primaryKey = PrimaryKey(id)
}
