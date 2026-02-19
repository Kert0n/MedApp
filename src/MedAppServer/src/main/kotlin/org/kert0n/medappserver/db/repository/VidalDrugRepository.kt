package org.kert0n.medappserver.db.repository

import org.kert0n.medappserver.db.model.parsed.VidalDrug
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.*

interface VidalDrugRepository : JpaRepository<VidalDrug, UUID> {
    
    // Native PostgreSQL fuzzy search using pg_trgm trigram similarity (supports Cyrillic and Unicode).
    // The threshold 0.2 for word_similarity is intentionally low to catch partial/prefix matches like "аспир" -> "аспирин".
    @Query(
        value = """
        SELECT * FROM parsed_drugs 
        WHERE name ILIKE CONCAT('%', :searchTerm, '%')
           OR word_similarity(:searchTerm, name) > 0.2
        ORDER BY 
            word_similarity(:searchTerm, name) DESC,
            name
        LIMIT :limit
        """,
        nativeQuery = true
    )
    fun fuzzySearchByName(@Param("searchTerm") searchTerm: String, @Param("limit") limit: Int = 10): List<VidalDrug>
}
