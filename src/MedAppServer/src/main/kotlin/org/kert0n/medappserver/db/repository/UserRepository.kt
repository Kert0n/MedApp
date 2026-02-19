package org.kert0n.medappserver.db.repository

import org.kert0n.medappserver.db.model.User
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.*

interface UserRepository: JpaRepository<User, UUID> {

    @Query("""
        SELECT DISTINCT u FROM User u
        JOIN u.medKits mk
        WHERE mk.id = :medId
    """)
    fun findByMedKitsId(@Param("medId") medId: UUID): List<User>
    
    @Query("""
        SELECT DISTINCT u FROM User u
        JOIN u.usings us
        WHERE us.drug.id = :drugId
    """)
    fun findByUsingsDrugId(@Param("drugId") drugId: UUID): Set<User>

    @EntityGraph(attributePaths = ["medKits"])
    @Query("""
        SELECT u FROM User u
        WHERE u.id = :id
    """)
    fun findByIdWithMedKits(@Param("id") id: UUID): User?
}
