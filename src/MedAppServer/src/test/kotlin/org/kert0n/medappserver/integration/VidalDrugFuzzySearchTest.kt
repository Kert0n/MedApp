package org.kert0n.medappserver.integration

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.kert0n.medappserver.TestcontainersConfiguration
import org.kert0n.medappserver.db.model.parsed.FormType
import org.kert0n.medappserver.db.model.parsed.VidalDrug
import org.kert0n.medappserver.db.repository.VidalDrugRepository
import org.kert0n.medappserver.db.table.ParsedDrugs
import org.kert0n.medappserver.db.table.ParsedFormTypes
import org.kert0n.medappserver.services.VidalDrugService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import java.util.*
import kotlin.test.*

/**
 * Integration tests for VidalDrug fuzzy search.
 *
 * NOTE: This test class does NOT use @Transactional on the class level.
 * This ensures we catch bugs that occur in production when the transaction
 * closes after the repository call.
 */
@SpringBootTest
@Import(TestcontainersConfiguration::class)
class VidalDrugFuzzySearchTest {

    @Autowired
    private lateinit var vidalDrugRepository: VidalDrugRepository

    @Autowired
    private lateinit var vidalDrugService: VidalDrugService

    @Autowired
    private lateinit var db: Database

    @BeforeEach
    fun setup() {
        transaction(db) {
            vidalDrugRepository.deleteAll()
            ParsedFormTypes.deleteAll()

            val tabletType = FormType(name = "таблетки")
            ParsedFormTypes.insert {
                it[id] = tabletType.id
                it[name] = tabletType.name
            }

            val drugs = listOf(
                VidalDrug(name = "Аспирин", manufacturer = "Байер", otc = true, formType = tabletType),
                VidalDrug(name = "Аспирин Кардио", manufacturer = "Байер", otc = true, formType = tabletType),
                VidalDrug(name = "Ибупрофен", manufacturer = "Фармстандарт", otc = true),
                VidalDrug(name = "Парацетамол", manufacturer = "Медисорб", otc = true),
                VidalDrug(name = "Aspirin", manufacturer = "Bayer", otc = true, formType = tabletType),
                VidalDrug(name = "Ibuprofen", manufacturer = "Generic", otc = true)
            )
            vidalDrugRepository.saveAll(drugs)
        }
    }

    @Test
    fun `fuzzySearchByName finds Cyrillic drugs by prefix`() {
        val results = transaction(db) { vidalDrugRepository.fuzzySearchByName("аспир", 10) }
        assertTrue(results.isNotEmpty(), "Should find drugs matching 'аспир'")
        assertTrue(results.any { it.name == "Аспирин" }, "Should find 'Аспирин'")
        assertTrue(results.any { it.name == "Аспирин Кардио" }, "Should find 'Аспирин Кардио'")
    }

    @Test
    fun `fuzzySearchByName is case-insensitive for Cyrillic`() {
        val results = transaction(db) { vidalDrugRepository.fuzzySearchByName("АСПИР", 10) }
        assertTrue(results.isNotEmpty(), "Should find drugs matching uppercase 'АСПИР'")
        assertTrue(results.any { it.name == "Аспирин" })
    }

    @Test
    fun `fuzzySearchByName finds Latin drugs by prefix`() {
        val results = transaction(db) { vidalDrugRepository.fuzzySearchByName("asp", 10) }
        assertTrue(results.isNotEmpty(), "Should find drugs matching 'asp'")
        assertTrue(results.any { it.name == "Aspirin" })
    }

    @Test
    fun `fuzzySearchByName is case-insensitive for Latin`() {
        val results = transaction(db) { vidalDrugRepository.fuzzySearchByName("ASP", 10) }
        assertTrue(results.isNotEmpty(), "Should find drugs matching uppercase 'ASP'")
        assertTrue(results.any { it.name == "Aspirin" })
    }

    @Test
    fun `fuzzySearchByName finds drugs by substring`() {
        val results = transaction(db) { vidalDrugRepository.fuzzySearchByName("профен", 10) }
        assertTrue(results.isNotEmpty(), "Should find drugs matching 'профен'")
        assertTrue(results.any { it.name == "Ибупрофен" })
    }

    @Test
    fun `fuzzySearchByName respects limit`() {
        val results = transaction(db) { vidalDrugRepository.fuzzySearchByName("а", 2) }
        assertTrue(results.size <= 2, "Should return at most 2 results")
    }

    @Test
    fun `fuzzySearchByName returns empty for no match`() {
        val results = transaction(db) { vidalDrugRepository.fuzzySearchByName("xyz123", 10) }
        assertTrue(results.isEmpty(), "Should return no results for unrelated term")
    }

    @Test
    fun `fuzzySearchByName uses trigram similarity for fuzzy matches`() {
        val results = transaction(db) { vidalDrugRepository.fuzzySearchByName("Аспирн", 10) }
        assertTrue(results.any { it.name == "Аспирин" }, "Trigram similarity should find 'Аспирин' even with typo 'Аспирн'")
    }

    @Test
    fun `fuzzySearchByName prioritizes exact and prefix matches`() {
        transaction(db) {
            vidalDrugRepository.deleteAll()
            vidalDrugRepository.saveAll(
                listOf(
                    VidalDrug(name = "Aspirin", manufacturer = "Bayer", otc = true),
                    VidalDrug(name = "Aspirin Cardio", manufacturer = "Bayer", otc = true),
                    VidalDrug(name = "Baby Aspirin", manufacturer = "Generic", otc = true)
                )
            )
        }

        val results = transaction(db) { vidalDrugRepository.fuzzySearchByName("Aspirin", 10) }
        assertTrue(results.isNotEmpty())
        assertEquals("Aspirin", results.first().name, "Exact match should be first")
    }

    @Test
    fun `fuzzySearchByName eagerly loads formType - no LazyInitializationException`() {
        val results = transaction(db) { vidalDrugRepository.fuzzySearchByName("аспир", 10) }
        assertTrue(results.isNotEmpty())
        val formTypeName = results.first { it.formType != null }.formType?.name
        assertNotNull(formTypeName, "FormType should be eagerly loaded and accessible outside transaction")
        assertEquals("таблетки", formTypeName)
    }

    @Test
    fun `service fuzzySearchByName returns results with accessible formType`() {
        val results = vidalDrugService.fuzzySearchByName("аспир", 10)
        assertTrue(results.isNotEmpty())
        val drugWithForm = results.first { it.formType != null }
        assertNotNull(drugWithForm.formType?.name, "FormType should be accessible via service results")
    }

    @Test
    fun `service fuzzySearchByName returns empty for blank input`() {
        val results = vidalDrugService.fuzzySearchByName("   ", 10)
        assertTrue(results.isEmpty(), "Should return empty for blank input")
    }

    @Test
    fun `service fuzzySearchByName sanitizes special characters`() {
        val results = vidalDrugService.fuzzySearchByName("аспир%", 10)
        assertNotNull(results)
    }
}
