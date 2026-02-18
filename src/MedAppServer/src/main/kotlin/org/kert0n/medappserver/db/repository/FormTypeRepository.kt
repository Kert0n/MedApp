package org.kert0n.medappserver.db.repository

import org.kert0n.medappserver.db.model.parsed.FormType
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface FormTypeRepository : JpaRepository<FormType, UUID> {
    fun findByName(name: String): FormType?
}
