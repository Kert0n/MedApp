package org.kert0n.medappserver.db.repository

import org.kert0n.medappserver.db.model.parsed.VidalDrug
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface VidalDrugRepository : JpaRepository<VidalDrug, UUID> {
    
    // Native PostgreSQL fuzzy search using trigram similarity
    @Query(
        value = """
        SELECT * FROM parsed_drugs 
        WHERE LOWER(name) LIKE LOWER(CONCAT('%', :searchTerm, '%'))
        ORDER BY 
            CASE 
                WHEN LOWER(name) = LOWER(:searchTerm) THEN 0
                WHEN LOWER(name) LIKE LOWER(CONCAT(:searchTerm, '%')) THEN 1
                ELSE 2
            END,
            name
        LIMIT :limit
        """,
        nativeQuery = true
    )
    fun fuzzySearchByName(@Param("searchTerm") searchTerm: String, @Param("limit") limit: Int = 10): List<VidalDrug>
}
