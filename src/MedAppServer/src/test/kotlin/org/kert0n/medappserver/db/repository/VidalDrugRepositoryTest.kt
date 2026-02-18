package org.kert0n.medappserver.db.repository

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.kert0n.medappserver.db.model.parsed.FormType
import org.kert0n.medappserver.db.model.parsed.QuantityUnit
import org.kert0n.medappserver.db.model.parsed.VidalDrug
import jakarta.persistence.EntityManager
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import java.util.*

/**
 * Repository tests for VidalDrugRepository fuzzy search
 * Tests verify fuzzy search functionality with various edge cases
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class VidalDrugRepositoryTest {

    @Autowired
    private lateinit var vidalDrugRepository: VidalDrugRepository

    @Autowired
    private lateinit var entityManager: EntityManager

    private lateinit var formType: FormType
    private lateinit var quantityUnit: QuantityUnit

    @BeforeEach
    fun setup() {
        // Create and persist form type and quantity unit for testing
        formType = FormType(name = "tablet")
        quantityUnit = QuantityUnit(name = "mg")
        
        entityManager.persist(formType)
        entityManager.persist(quantityUnit)

        // Create test drugs with various names
        createVidalDrug("Aspirin", "Bayer", true)
        createVidalDrug("Aspirin Plus", "Generic Co", true)
        createVidalDrug("Ibuprofen", "PharmaCorp", false)
        createVidalDrug("Paracetamol", "MedLab", true)
        createVidalDrug("Acetaminophen", "HealthCare Inc", true)
        createVidalDrug("Aspro", "MediPharm", false)
        createVidalDrug("Special-Aspirin", "Special Pharma", true)
        createVidalDrug("ASPIRIN FORTE", "StrongMeds", true)

        entityManager.flush()
        entityManager.clear()
    }

    private fun createVidalDrug(name: String, manufacturer: String, otc: Boolean) {
        val drug = VidalDrug(
            id = UUID.randomUUID(),
            name = name,
            manufacturer = manufacturer,
            otc = otc,
            formType = formType,
            quantity = 500,
            quantityUnit = quantityUnit,
            activeSubstance = "test-substance",
            category = "painkiller",
            country = "TestLand",
            description = "Test description for $name"
        )
        entityManager.persist(drug)
    }

    // fuzzySearchByName - basic functionality tests
    @Test
    fun `fuzzySearchByName - finds exact match`() {
        val results = vidalDrugRepository.fuzzySearchByName("Aspirin", 10)

        assertFalse(results.isEmpty())
        assertTrue(results.any { it.name == "Aspirin" })
    }

    @Test
    fun `fuzzySearchByName - case insensitive search`() {
        val results = vidalDrugRepository.fuzzySearchByName("aspirin", 10)

        assertFalse(results.isEmpty())
        assertTrue(results.any { it.name.equals("Aspirin", ignoreCase = true) })
    }

    @Test
    fun `fuzzySearchByName - uppercase search`() {
        val results = vidalDrugRepository.fuzzySearchByName("ASPIRIN", 10)

        assertFalse(results.isEmpty())
        assertTrue(results.any { it.name.equals("Aspirin", ignoreCase = true) })
    }

    @Test
    fun `fuzzySearchByName - partial match from beginning`() {
        val results = vidalDrugRepository.fuzzySearchByName("Asp", 10)

        assertFalse(results.isEmpty())
        assertTrue(results.any { it.name.startsWith("Asp", ignoreCase = true) })
        assertTrue(results.any { it.name == "Aspirin" })
        assertTrue(results.any { it.name == "Aspro" })
    }

    @Test
    fun `fuzzySearchByName - partial match from middle`() {
        val results = vidalDrugRepository.fuzzySearchByName("pirin", 10)

        assertFalse(results.isEmpty())
        assertTrue(results.any { it.name.contains("pirin", ignoreCase = true) })
    }

    @Test
    fun `fuzzySearchByName - partial match from end`() {
        val results = vidalDrugRepository.fuzzySearchByName("rin", 10)

        assertFalse(results.isEmpty())
        assertTrue(results.any { it.name.contains("rin", ignoreCase = true) })
    }

    // Sorting and ranking tests
    @Test
    fun `fuzzySearchByName - exact match ranked first`() {
        val results = vidalDrugRepository.fuzzySearchByName("Aspirin", 10)

        assertFalse(results.isEmpty())
        assertEquals("Aspirin", results[0].name, "Exact match should be first")
    }

    @Test
    fun `fuzzySearchByName - prefix match ranked before contains match`() {
        val results = vidalDrugRepository.fuzzySearchByName("Asp", 10)

        assertFalse(results.isEmpty())
        val firstResult = results[0].name
        assertTrue(
            firstResult.startsWith("Asp", ignoreCase = true),
            "Prefix match should be ranked higher"
        )
    }

    @Test
    fun `fuzzySearchByName - alphabetical sorting for same rank`() {
        val results = vidalDrugRepository.fuzzySearchByName("Aspirin", 10)

        val aspirinMatches = results.filter { it.name.contains("Aspirin", ignoreCase = true) }
        assertTrue(aspirinMatches.size >= 2)
        
        // Check if sorted alphabetically among same ranking
        for (i in 0 until aspirinMatches.size - 1) {
            assertTrue(
                aspirinMatches[i].name <= aspirinMatches[i + 1].name,
                "Results should be sorted alphabetically within same rank"
            )
        }
    }

    // Limit parameter tests
    @Test
    fun `fuzzySearchByName - respects limit parameter`() {
        val results = vidalDrugRepository.fuzzySearchByName("a", 3)

        assertTrue(results.size <= 3, "Should respect limit parameter")
    }

    @Test
    fun `fuzzySearchByName - default limit is 10`() {
        val results = vidalDrugRepository.fuzzySearchByName("a")

        assertTrue(results.size <= 10, "Default limit should be 10")
    }

    @Test
    fun `fuzzySearchByName - limit 1 returns only best match`() {
        val results = vidalDrugRepository.fuzzySearchByName("Aspirin", 1)

        assertEquals(1, results.size)
        assertEquals("Aspirin", results[0].name)
    }

    @Test
    fun `fuzzySearchByName - large limit returns all matches`() {
        val results = vidalDrugRepository.fuzzySearchByName("a", 100)

        assertTrue(results.isNotEmpty())
        assertTrue(results.size <= 100)
    }

    // Special characters and edge cases
    @Test
    fun `fuzzySearchByName - handles special characters in search`() {
        val results = vidalDrugRepository.fuzzySearchByName("Special-", 10)

        assertFalse(results.isEmpty())
        assertTrue(results.any { it.name.contains("Special-") })
    }

    @Test
    fun `fuzzySearchByName - handles hyphenated names`() {
        val results = vidalDrugRepository.fuzzySearchByName("Special-Aspirin", 10)

        assertFalse(results.isEmpty())
        assertTrue(results.any { it.name == "Special-Aspirin" })
    }

    @Test
    fun `fuzzySearchByName - empty search returns results starting with any character`() {
        val results = vidalDrugRepository.fuzzySearchByName("", 10)

        // Empty string matches everything, should return up to limit
        assertTrue(results.size <= 10)
    }

    @Test
    fun `fuzzySearchByName - whitespace is trimmed implicitly by query`() {
        val results = vidalDrugRepository.fuzzySearchByName("  Aspirin  ", 10)

        assertFalse(results.isEmpty())
        assertTrue(results.any { it.name.contains("Aspirin") })
    }

    @Test
    fun `fuzzySearchByName - no results for non-matching search`() {
        val results = vidalDrugRepository.fuzzySearchByName("NonExistentDrug123456", 10)

        assertTrue(results.isEmpty())
    }

    // Complete drug data verification
    @Test
    fun `fuzzySearchByName - returns complete drug data`() {
        val results = vidalDrugRepository.fuzzySearchByName("Aspirin", 1)

        assertFalse(results.isEmpty())
        val drug = results[0]
        assertNotNull(drug.id)
        assertEquals("Aspirin", drug.name)
        assertEquals("Bayer", drug.manufacturer)
        assertTrue(drug.otc)
        assertNotNull(drug.formType)
        assertNotNull(drug.quantityUnit)
        assertEquals(500, drug.quantity)
    }

    @Test
    fun `fuzzySearchByName - handles drugs with null optional fields`() {
        val drugWithNulls = VidalDrug(
            id = UUID.randomUUID(),
            name = "MinimalDrug",
            manufacturer = "MinimalPharma",
            otc = true,
            formType = null,
            quantity = null,
            quantityUnit = null,
            activeSubstance = null,
            category = null,
            country = null,
            description = null
        )
        entityManager.persist(drugWithNulls)
        entityManager.flush()

        val results = vidalDrugRepository.fuzzySearchByName("MinimalDrug", 10)

        assertFalse(results.isEmpty())
        val found = results.find { it.name == "MinimalDrug" }
        assertNotNull(found)
        assertNull(found?.formType)
        assertNull(found?.quantity)
    }

    // Multiple match scenarios
    @Test
    fun `fuzzySearchByName - returns multiple matches for broad search`() {
        val results = vidalDrugRepository.fuzzySearchByName("Asp", 10)

        assertTrue(results.size >= 3, "Should find multiple Asp* drugs")
        assertTrue(results.any { it.name == "Aspirin" })
        assertTrue(results.any { it.name == "Aspro" })
    }

    @Test
    fun `fuzzySearchByName - distinguishes similar names`() {
        val results = vidalDrugRepository.fuzzySearchByName("Aspirin", 10)

        assertTrue(results.any { it.name == "Aspirin" })
        assertTrue(results.any { it.name == "Aspirin Plus" })
        assertTrue(results.any { it.name == "ASPIRIN FORTE" })
        // Should not include "Aspro" as exact match for "Aspirin"
        val exactMatch = results[0]
        assertEquals("Aspirin", exactMatch.name)
    }
}
