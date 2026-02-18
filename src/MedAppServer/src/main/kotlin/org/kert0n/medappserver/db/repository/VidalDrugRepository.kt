package org.kert0n.medappserver.db.repository

import org.kert0n.medappserver.db.model.parsed.VidalDrug
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface VidalDrugRepository : JpaRepository<VidalDrug, UUID> {
    
    @Query("""
        SELECT vd FROM VidalDrug vd 
        LEFT JOIN FETCH vd.formType 
        LEFT JOIN FETCH vd.quantityUnit
        WHERE LOWER(vd.name) LIKE LOWER(CONCAT('%', :searchTerm, '%'))
        ORDER BY 
            CASE 
                WHEN LOWER(vd.name) = LOWER(:searchTerm) THEN 0
                WHEN LOWER(vd.name) LIKE LOWER(CONCAT(:searchTerm, '%')) THEN 1
                ELSE 2
            END,
            vd.name
    """)
    fun fuzzySearchByName(@Param("searchTerm") searchTerm: String): List<VidalDrug>
    
    @Query("""
        SELECT vd FROM VidalDrug vd 
        LEFT JOIN FETCH vd.formType 
        LEFT JOIN FETCH vd.quantityUnit
        WHERE vd.id = :id
    """)
    fun findByIdWithDetails(@Param("id") id: UUID): VidalDrug?
}
