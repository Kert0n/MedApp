package org.kert0n.medappserver.services

import org.kert0n.medappserver.db.model.parsed.VidalDrug
import org.kert0n.medappserver.db.repository.VidalDrugRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.util.*

@Service
class VidalDrugService(
    private val vidalDrugRepository: VidalDrugRepository
) {

    /**
     * Fuzzy search for drugs by name using PostgreSQL trigram similarity
     */
    fun fuzzySearchByName(searchTerm: String): List<VidalDrug> {
        if (searchTerm.isBlank()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Search term cannot be empty")
        }
        return vidalDrugRepository.fuzzySearchByName(searchTerm)
    }

    /**
     * Get a specific drug template by ID
     */
    fun getDrugById(drugId: UUID): VidalDrug {
        return vidalDrugRepository.findByIdOrNull(drugId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Drug template not found")
    }
}
