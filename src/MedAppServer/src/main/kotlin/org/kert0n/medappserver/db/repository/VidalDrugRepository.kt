package org.kert0n.medappserver.db.repository

import org.kert0n.medappserver.db.model.parsed.VidalDrug
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.*

interface VidalDrugRepository : JpaRepository<VidalDrug, UUID> {
    
    // Native PostgreSQL fuzzy search using trigram similarity
    @Query(
        value = """
        SELECT * FROM parsed_drugs 
        WHERE name ILIKE CONCAT('%', :searchTerm, '%')
        ORDER BY 
            CASE 
                WHEN name ILIKE :searchTerm THEN 0
                WHEN name ILIKE CONCAT(:searchTerm, '%') THEN 1
                ELSE 2
            END,
            name
        LIMIT :limit
        """,
        nativeQuery = true
    )
    fun fuzzySearchByName(@Param("searchTerm") searchTerm: String, @Param("limit") limit: Int = 10): List<VidalDrug>
}
