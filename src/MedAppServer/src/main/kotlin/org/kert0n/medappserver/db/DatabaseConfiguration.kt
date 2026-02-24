package org.kert0n.medappserver.db

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import org.kert0n.medappserver.db.table.*
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import javax.sql.DataSource

@Configuration
class DatabaseConfiguration(private val dataSource: DataSource) {

    @Bean
    fun database(): Database {
        val db = Database.connect(dataSource)
        transaction(db) {
            SchemaUtils.createMissingTablesAndColumns(
                Users, MedKits, UserMedKits, UserDrugs, Usings,
                ParsedFormTypes, ParsedQuantityUnits, ParsedDrugs
            )
        }
        return db
    }
}
