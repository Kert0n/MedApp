package org.kert0n.medappserver.integration

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.kert0n.medappserver.TestcontainersConfiguration
import org.kert0n.medappserver.db.model.parsed.VidalDrug
import org.kert0n.medappserver.db.repository.VidalDrugRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.TestPropertySource
import org.springframework.transaction.annotation.Transactional
import java.util.*
import kotlin.test.*

@SpringBootTest
@Import(TestcontainersConfiguration::class)
@TestPropertySource(
    properties = [
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect"
    ]
)
@Transactional
class VidalDrugFuzzySearchTest {

    @Autowired
    private lateinit var vidalDrugRepository: VidalDrugRepository

    @BeforeEach
    fun setup() {
        vidalDrugRepository.deleteAll()
        val drugs = listOf(
            VidalDrug(name = "Аспирин", manufacturer = "Байер", otc = true),
            VidalDrug(name = "Аспирин Кардио", manufacturer = "Байер", otc = true),
            VidalDrug(name = "Ибупрофен", manufacturer = "Фармстандарт", otc = true),
            VidalDrug(name = "Парацетамол", manufacturer = "Медисорб", otc = true),
            VidalDrug(name = "Aspirin", manufacturer = "Bayer", otc = true),
            VidalDrug(name = "Ibuprofen", manufacturer = "Generic", otc = true)
        )
        vidalDrugRepository.saveAll(drugs)
    }

    @Test
    fun `fuzzySearchByName finds Cyrillic drugs by prefix`() {
        val results = vidalDrugRepository.fuzzySearchByName("аспир", 10)
        assertTrue(results.isNotEmpty(), "Should find drugs matching 'аспир'")
        assertTrue(results.any { it.name == "Аспирин" }, "Should find 'Аспирин'")
        assertTrue(results.any { it.name == "Аспирин Кардио" }, "Should find 'Аспирин Кардио'")
    }

    @Test
    fun `fuzzySearchByName is case-insensitive for Cyrillic`() {
        val results = vidalDrugRepository.fuzzySearchByName("АСПИР", 10)
        assertTrue(results.isNotEmpty(), "Should find drugs matching uppercase 'АСПИР'")
        assertTrue(results.any { it.name == "Аспирин" })
    }

    @Test
    fun `fuzzySearchByName finds Latin drugs by prefix`() {
        val results = vidalDrugRepository.fuzzySearchByName("asp", 10)
        assertTrue(results.isNotEmpty(), "Should find drugs matching 'asp'")
        assertTrue(results.any { it.name == "Aspirin" })
    }

    @Test
    fun `fuzzySearchByName is case-insensitive for Latin`() {
        val results = vidalDrugRepository.fuzzySearchByName("ASP", 10)
        assertTrue(results.isNotEmpty(), "Should find drugs matching uppercase 'ASP'")
        assertTrue(results.any { it.name == "Aspirin" })
    }

    @Test
    fun `fuzzySearchByName finds drugs by substring`() {
        val results = vidalDrugRepository.fuzzySearchByName("профен", 10)
        assertTrue(results.isNotEmpty(), "Should find drugs matching 'профен'")
        assertTrue(results.any { it.name == "Ибупрофен" })
    }

    @Test
    fun `fuzzySearchByName respects limit`() {
        val results = vidalDrugRepository.fuzzySearchByName("а", 2)
        assertTrue(results.size <= 2, "Should return at most 2 results")
    }

    @Test
    fun `fuzzySearchByName returns empty for no match`() {
        val results = vidalDrugRepository.fuzzySearchByName("xyz123", 10)
        assertTrue(results.isEmpty(), "Should return no results for unrelated term")
    }

    @Test
    fun `fuzzySearchByName uses trigram similarity for fuzzy matches`() {
        val results = vidalDrugRepository.fuzzySearchByName("Аспирн", 10)
        assertTrue(results.any { it.name == "Аспирин" }, "Trigram similarity should find 'Аспирин' even with typo 'Аспирн'")
    }

    @Test
    fun `fuzzySearchByName prioritizes exact and prefix matches`() {
        vidalDrugRepository.deleteAll()
        vidalDrugRepository.saveAll(
            listOf(
                VidalDrug(name = "Aspirin", manufacturer = "Bayer", otc = true),
                VidalDrug(name = "Aspirin Cardio", manufacturer = "Bayer", otc = true),
                VidalDrug(name = "Baby Aspirin", manufacturer = "Generic", otc = true)
            )
        )

        val results = vidalDrugRepository.fuzzySearchByName("Aspirin", 10)
        assertTrue(results.isNotEmpty())
        assertEquals("Aspirin", results.first().name, "Exact match should be first")
    }
}
