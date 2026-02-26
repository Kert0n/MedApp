package org.kert0n.medappserver.services.models

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.kert0n.medappserver.db.repository.MedKitRepository
import org.kert0n.medappserver.testutil.DatabaseTestHelper
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MedKitServiceTest {

    @Autowired private lateinit var medKitService: MedKitService
    @Autowired private lateinit var medKitRepository: MedKitRepository
    @Autowired private lateinit var userService: UserService
    @Autowired private lateinit var dbHelper: DatabaseTestHelper

    // ── createNew ──

    @Test
    fun `createNew creates medkit with user`() {
        val alice = dbHelper.freshUser("alice")
        val medKit = medKitService.createNew(alice.id)
        dbHelper.flushAndClear()

        assertNotNull(medKit.id)
        assertTrue(medKit.users.any { it.id == alice.id })
    }

    // ── findById ──

    @Test
    fun `findById throws NOT_FOUND for non-existent medkit`() {
        assertThrows<ResponseStatusException> {
            medKitService.findById(UUID.randomUUID())
        }
    }

    // ── findByIdForUser ──

    @Test
    fun `findByIdForUser throws when user has no access`() {
        val alice = dbHelper.freshUser("alice")
        val eve = dbHelper.freshUser("eve")
        val kit = medKitService.createNew(alice.id)
        dbHelper.flushAndClear()

        assertFailsWith<ResponseStatusException> {
            medKitService.findByIdForUser(kit.id, eve.id)
        }
    }

    // ── findAllByUser ──

    @Test
    fun `findAllByUser returns medkits for user`() {
        val alice = dbHelper.freshUser("alice")
        medKitService.createNew(alice.id)
        medKitService.createNew(alice.id)
        dbHelper.flushAndClear()

        assertEquals(2, medKitService.findAllByUser(alice.id).size)
    }

    // ── findMedKitSummaries ──

    @Test
    fun `findMedKitSummaries returns summaries for user`() {
        val alice = dbHelper.freshUser("alice")
        medKitService.createNew(alice.id)
        dbHelper.flushAndClear()

        val summaries = medKitService.findMedKitSummaries(alice.id)
        assertEquals(1, summaries.size)
    }

    // ── generateMedKitShareKey / joinMedKitByKey ──

    @Test
    fun `joinMedKitByKey adds user and invalidates key`() {
        val owner = dbHelper.freshUser("owner")
        val joiner = dbHelper.freshUser("joiner")
        val kit = medKitService.createNew(owner.id)
        dbHelper.flushAndClear()

        val key = medKitService.generateMedKitShareKey(kit.id, owner.id)
        medKitService.joinMedKitByKey(key, joiner.id)
        dbHelper.flushAndClear()

        val joinerKits = medKitService.findAllByUser(joiner.id)
        assertEquals(1, joinerKits.size)
        assertEquals(kit.id, joinerKits.first().id)

        // Key should be invalidated after use
        assertFailsWith<ResponseStatusException> {
            medKitService.joinMedKitByKey(key, joiner.id)
        }
    }

    @Test
    fun `joinMedKitByKey fails for missing key`() {
        val user = dbHelper.freshUser("user")

        assertFailsWith<ResponseStatusException> {
            medKitService.joinMedKitByKey("missing-key", user.id)
        }
    }

    // ── addUserToMedKit ──

    @Test
    fun `addUserToMedKit adds second user`() {
        val alice = dbHelper.freshUser("alice")
        val bob = dbHelper.freshUser("bob")
        val kit = medKitService.createNew(alice.id)
        dbHelper.flushAndClear()

        medKitService.addUserToMedKit(kit.id, bob.id)
        dbHelper.flushAndClear()

        assertEquals(1, medKitService.findAllByUser(bob.id).size)
    }

    @Test
    fun `addUserToMedKit throws when user already exists`() {
        val alice = dbHelper.freshUser("alice")
        val kit = medKitService.createNew(alice.id)
        dbHelper.flushAndClear()

        assertFailsWith<ResponseStatusException> {
            medKitService.addUserToMedKit(kit.id, alice.id)
        }
    }

    // ── removeUserFromMedKit ──

    @Test
    fun `removeUserFromMedKit keeps medkit when other users remain`() {
        val alice = dbHelper.freshUser("alice")
        val bob = dbHelper.freshUser("bob")
        val kit = medKitService.createNew(alice.id)
        medKitService.addUserToMedKit(kit.id, bob.id)
        dbHelper.flushAndClear()

        val loadedKit = medKitService.findById(kit.id)
        val loadedBob = userService.findById(bob.id)
        medKitService.removeUserFromMedKit(loadedKit, loadedBob)
        dbHelper.flushAndClear()

        assertNotNull(medKitService.findByIdForUser(kit.id, alice.id))
        assertFailsWith<ResponseStatusException> {
            medKitService.findByIdForUser(kit.id, bob.id)
        }
    }

    @Test
    fun `removeUserFromMedKit deletes medkit when last user leaves`() {
        val alice = dbHelper.freshUser("alice")
        val kit = medKitService.createNew(alice.id)
        dbHelper.flushAndClear()

        val loadedKit = medKitService.findById(kit.id)
        val loadedAlice = userService.findById(alice.id)
        medKitService.removeUserFromMedKit(loadedKit, loadedAlice)
        dbHelper.flushAndClear()

        assertNull(medKitRepository.findById(kit.id).orElse(null))
    }
}
