package org.kert0n.medappserver.db.repository

import org.kert0n.medappserver.db.model.parsed.VidalDrug
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface VidalDrugRepository : JpaRepository<VidalDrug, UUID> {
    
    /**
     * Fuzzy search for drugs by name using PostgreSQL trigram similarity (pg_trgm extension)
     * Requires pg_trgm extension enabled in PostgreSQL
     * Returns drugs ordered by similarity score (most similar first)
     */
    @Query(
        value = """
        SELECT * FROM parsed_drugs 
        WHERE similarity(name, :searchTerm) > 0.3
        ORDER BY similarity(name, :searchTerm) DESC
        LIMIT 20
        """,
        nativeQuery = true
    )
    fun fuzzySearchByName(@Param("searchTerm") searchTerm: String): List<VidalDrug>
}
