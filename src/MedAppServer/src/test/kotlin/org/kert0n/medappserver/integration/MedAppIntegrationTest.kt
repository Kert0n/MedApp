package org.kert0n.medappserver.integration

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.kert0n.medappserver.controller.DrugCreateDTO
import org.kert0n.medappserver.controller.UsingCreateDTO
import org.kert0n.medappserver.db.model.*
import org.kert0n.medappserver.services.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import java.util.*

@SpringBootTest
@ActiveProfiles("test")  // Use H2 database for integration tests
@Transactional
class MedAppIntegrationTest {

    @Autowired
    private lateinit var userService: UserService

    @Autowired
    private lateinit var medKitService: MedKitService

    @Autowired
    private lateinit var drugService: DrugService

    @Autowired
    private lateinit var usingService: UsingService

    @Test
    fun `complete workflow - create user, medkit, drug, and treatment plan`() {
        // 1. Create user
        val user = userService.registerNewUser(UUID.randomUUID(), "password123", "127.0.0.1")
        assertNotNull(user.id)

        // 2. Create medkit
        val medKit = medKitService.createNew(user.id)
        assertNotNull(medKit.id)
        assertTrue(medKit.users.contains(user))

        // 3. Create drug
        val drugDTO = DrugCreateDTO(
            name = "Aspirin",
            quantity = 100.0,
            quantityUnit = "mg",
            medKitId = medKit.id
        )
        val drug = drugService.create(drugDTO, medKit,user.id)
        assertNotNull(drug.id)
        assertEquals("Aspirin", drug.name)
        assertEquals(100.0, drug.quantity)

        // 4. Create treatment plan
        val usingDTO = UsingCreateDTO(
            drugId = drug.id,
            plannedAmount = 30.0
        )
        val using = usingService.createTreatmentPlan(user.id, usingDTO)
        assertEquals(30.0, using.plannedAmount)

        // 5. Record intake
        val updatedUsing = usingService.recordIntake(user.id, drug.id, 10.0)
        assertEquals(20.0, updatedUsing!!.plannedAmount)

        // 6. Verify drug quantity reduced
        val updatedDrug = drugService.findById(drug.id)
        assertEquals(90.0, updatedDrug.quantity)
    }

    @Test
    fun `shared medkit workflow - multiple users`() {
        // Create first user and medkit
        val user1 = userService.registerNewUser(UUID.randomUUID(), "password1", "127.0.0.1")
        val medKit = medKitService.createNew(user1.id)

        // Create second user and add to medkit
        val user2 = userService.registerNewUser(UUID.randomUUID(), "password2", "127.0.0.2")
        medKitService.addUserToMedKit(medKit.id, user2.id)

        // Verify both users have access
        val medKitsUser1 = medKitService.findAllByUser(user1.id)
        val medKitsUser2 = medKitService.findAllByUser(user2.id)

        assertTrue(medKitsUser1.any { it.id == medKit.id })
        assertTrue(medKitsUser2.any { it.id == medKit.id })
    }

    @Test
    fun `insufficient quantity prevents treatment plan creation`() {
        val user = userService.registerNewUser(UUID.randomUUID(), "password", "127.0.0.3")
        val medKit = medKitService.createNew(user.id)
        
        val drug = drugService.create(
            DrugCreateDTO(
                name = "Limited Medicine",
                quantity = 50.0,
                quantityUnit = "mg",
                medKitId = medKit.id
            ),medKit,
            user.id
        )

        // Try to create treatment plan exceeding available quantity
        assertThrows(org.springframework.web.server.ResponseStatusException::class.java) {
            usingService.createTreatmentPlan(
                user.id,
                UsingCreateDTO(drug.id, 100.0)
            )
        }
    }
}
