package org.kert0n.medappserver.services

import org.kert0n.medappserver.db.model.parsed.VidalDrug
import org.kert0n.medappserver.db.repository.VidalDrugRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class VidalDrugService(
    private val vidalDrugRepository: VidalDrugRepository
) {
    
    fun fuzzySearchByName(searchTerm: String, limit: Int = 10): List<VidalDrug> {
        if (searchTerm.isBlank()) {
            return emptyList()
        }
        return vidalDrugRepository.fuzzySearchByName(searchTerm.trim())
            .take(limit)
    }
    
    fun findById(id: UUID): VidalDrug? {
        return vidalDrugRepository.findByIdWithDetails(id)
    }
    
    fun getAll(): List<VidalDrug> {
        return vidalDrugRepository.findAll()
    }
}
