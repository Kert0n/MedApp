package org.kert0n.medappserver.services.models

import org.kert0n.medappserver.db.model.parsed.VidalDrug
import org.kert0n.medappserver.db.repository.VidalDrugRepository
import org.springframework.stereotype.Service
import java.util.*

@Service
class VidalDrugService(
    private val vidalDrugRepository: VidalDrugRepository
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
        return vidalDrugRepository.fuzzySearchByName(sanitized, limit)
    }

    fun findById(id: UUID): VidalDrug? {
        return vidalDrugRepository.findById(id).orElse(null)
    }

}
