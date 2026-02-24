package org.kert0n.medappserver.services

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.transaction
import org.kert0n.medappserver.db.model.parsed.VidalDrug
import org.kert0n.medappserver.db.repository.VidalDrugRepository
import org.springframework.stereotype.Service
import java.util.*

@Service
class VidalDrugService(
    private val vidalDrugRepository: VidalDrugRepository,
    private val database: Database
) {
    
    fun fuzzySearchByName(searchTerm: String, limit: Int = 10): List<VidalDrug> {
        if (searchTerm.isBlank()) {
            return emptyList()
        }
        // Escape LIKE wildcards and backslashes to keep fuzzy search predictable and safe.
        val sanitized = searchTerm.trim()
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_")
        return transaction(database) {
            vidalDrugRepository.fuzzySearchByName(sanitized, limit)
        }
    }
    
    fun findById(id: UUID): VidalDrug? {
        return transaction(database) {
            vidalDrugRepository.findById(id).orElse(null)
        }
    }

}
