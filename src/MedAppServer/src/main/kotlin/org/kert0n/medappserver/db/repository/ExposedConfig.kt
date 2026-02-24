package org.kert0n.medappserver.db.repository

import jakarta.annotation.PostConstruct
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.springframework.context.annotation.Configuration
import javax.sql.DataSource

@Configuration
class ExposedConfig(
    private val dataSource: DataSource
) {
    @PostConstruct
    fun init() {
        Database.connect(dataSource)
        transaction {
            SchemaUtils.createMissingTablesAndColumns(
                UsersTable,
                MedKitsTable,
                UserMedKitsTable,
                DrugsTable,
                UsingsTable,
                FormTypesTable,
                QuantityUnitsTable,
                ParsedDrugsTable
            )
        }
    }
}
