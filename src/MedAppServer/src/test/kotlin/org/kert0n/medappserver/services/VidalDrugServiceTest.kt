package org.kert0n.medappserver.services

import org.junit.jupiter.api.Test
import org.kert0n.medappserver.db.model.parsed.VidalDrug
import org.kert0n.medappserver.db.repository.VidalDrugRepository
import org.kert0n.medappserver.services.models.VidalDrugService
import org.mockito.kotlin.*
import java.util.*
import kotlin.test.*

class VidalDrugServiceTest {

    private val vidalDrugRepository: VidalDrugRepository = mock()
    private val vidalDrugService = VidalDrugService(vidalDrugRepository)

    @Test
    fun `fuzzySearchByName returns empty for blank input`() {
        val result = vidalDrugService.fuzzySearchByName("   ", 10)
        assertTrue(result.isEmpty())
        verify(vidalDrugRepository, never()).fuzzySearchByName(any(), any())
    }

    @Test
    fun `fuzzySearchByName returns empty for empty string`() {
        val result = vidalDrugService.fuzzySearchByName("", 10)
        assertTrue(result.isEmpty())
        verify(vidalDrugRepository, never()).fuzzySearchByName(any(), any())
    }

    @Test
    fun `fuzzySearchByName trims input before querying`() {
        whenever(vidalDrugRepository.fuzzySearchByName("аспир", 10)).thenReturn(emptyList())
        vidalDrugService.fuzzySearchByName("  аспир  ", 10)
        verify(vidalDrugRepository).fuzzySearchByName("аспир", 10)
    }

    @Test
    fun `fuzzySearchByName escapes percent character`() {
        whenever(vidalDrugRepository.fuzzySearchByName("test\\%drug", 10)).thenReturn(emptyList())
        vidalDrugService.fuzzySearchByName("test%drug", 10)
        verify(vidalDrugRepository).fuzzySearchByName("test\\%drug", 10)
    }

    @Test
    fun `fuzzySearchByName escapes underscore character`() {
        whenever(vidalDrugRepository.fuzzySearchByName("test\\_drug", 10)).thenReturn(emptyList())
        vidalDrugService.fuzzySearchByName("test_drug", 10)
        verify(vidalDrugRepository).fuzzySearchByName("test\\_drug", 10)
    }

    @Test
    fun `fuzzySearchByName escapes backslash character`() {
        whenever(vidalDrugRepository.fuzzySearchByName("test\\\\drug", 10)).thenReturn(emptyList())
        vidalDrugService.fuzzySearchByName("test\\drug", 10)
        verify(vidalDrugRepository).fuzzySearchByName("test\\\\drug", 10)
    }

    @Test
    fun `fuzzySearchByName returns repository results`() {
        val drug = VidalDrug(name = "Аспирин", manufacturer = "Байер", otc = true)
        whenever(vidalDrugRepository.fuzzySearchByName("аспир", 10)).thenReturn(listOf(drug))

        val result = vidalDrugService.fuzzySearchByName("аспир", 10)
        assertEquals(1, result.size)
        assertEquals("Аспирин", result[0].name)
    }

    @Test
    fun `fuzzySearchByName passes limit to repository`() {
        whenever(vidalDrugRepository.fuzzySearchByName("test", 5)).thenReturn(emptyList())
        vidalDrugService.fuzzySearchByName("test", 5)
        verify(vidalDrugRepository).fuzzySearchByName("test", 5)
    }

    @Test
    fun `findById returns drug when found`() {
        val id = UUID.randomUUID()
        val drug = VidalDrug(id = id, name = "Test", manufacturer = "Pharma", otc = true)
        whenever(vidalDrugRepository.findById(id)).thenReturn(Optional.of(drug))

        val result = vidalDrugService.findById(id)
        assertNotNull(result)
        assertEquals(id, result.id)
    }

    @Test
    fun `findById returns null when not found`() {
        val id = UUID.randomUUID()
        whenever(vidalDrugRepository.findById(id)).thenReturn(Optional.empty())

        val result = vidalDrugService.findById(id)
        assertNull(result)
    }
}
