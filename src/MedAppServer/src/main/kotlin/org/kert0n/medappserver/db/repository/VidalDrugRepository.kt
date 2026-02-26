package org.kert0n.medappserver.db.repository

import org.kert0n.medappserver.db.model.parsed.VidalDrug
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.*

interface VidalDrugRepository : JpaRepository<VidalDrug, UUID> {

    // Native PostgreSQL fuzzy search using pg_trgm trigram similarity + ILIKE
    // Similarity threshold 0.3 is the pg_trgm default for the % operator
    @Query(
        value = """
        SELECT * FROM parsed_drugs 
        WHERE name ILIKE CONCAT('%', :searchTerm, '%')
           OR similarity(LOWER(name), LOWER(:searchTerm)) > 0.3
        ORDER BY 
            CASE 
                WHEN LOWER(name) = LOWER(:searchTerm) THEN 0
                WHEN name ILIKE CONCAT(:searchTerm, '%') THEN 1
                WHEN name ILIKE CONCAT('%', :searchTerm, '%') THEN 2
                ELSE 3
            END,
            similarity(LOWER(name), LOWER(:searchTerm)) DESC,
            name
        LIMIT :limit
        """,
        nativeQuery = true
    )
    fun fuzzySearchByName(@Param("searchTerm") searchTerm: String, @Param("limit") limit: Int = 10): List<VidalDrug>
}
