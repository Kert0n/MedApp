package org.kert0n.medappserver.integration

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.kert0n.medappserver.db.model.*
import org.kert0n.medappserver.db.repository.*
import org.kert0n.medappserver.db.table.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.util.*
import kotlin.test.*

@SpringBootTest
@ActiveProfiles("test")
class RepositoryIntegrationTests {

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var medKitRepository: MedKitRepository

    @Autowired
    private lateinit var drugRepository: DrugRepository

    @Autowired
    private lateinit var usingRepository: UsingRepository

    @Autowired
    private lateinit var db: Database

    @BeforeEach
    fun cleanup() {
        transaction(db) {
            Usings.deleteAll()
            UserDrugs.deleteAll()
            UserMedKits.deleteAll()
            MedKits.deleteAll()
            Users.deleteAll()
        }
    }

    private fun createUser(key: String = "key_${UUID.randomUUID()}"): User {
        return transaction(db) {
            userRepository.save(User(id = UUID.randomUUID(), hashedKey = key))
        }
    }

    private fun createMedKitForUser(user: User): MedKit {
        return transaction(db) {
            val medKit = medKitRepository.save(MedKit())
            medKitRepository.addUserToMedKit(user.id, medKit.id)
            medKit
        }
    }

    private fun createDrug(medKit: MedKit, name: String = "Drug", quantity: Double = 100.0): Drug {
        return transaction(db) {
            drugRepository.save(
                Drug(
                    name = name,
                    quantity = quantity,
                    quantityUnit = "tablets",
                    formType = null,
                    category = null,
                    manufacturer = null,
                    country = null,
                    description = null,
                    medKitId = medKit.id
                )
            )
        }
    }

    // === DrugRepository Tests ===

    @Test
    fun `DrugRepository - findAllByMedKitId returns drugs in medkit`() {
        val user = createUser()
        val medKit = createMedKitForUser(user)
        createDrug(medKit, "Drug A")
        createDrug(medKit, "Drug B")

        val drugs = transaction(db) { drugRepository.findAllByMedKitId(medKit.id) }
        assertEquals(2, drugs.size)
        assertTrue(drugs.any { it.name == "Drug A" })
        assertTrue(drugs.any { it.name == "Drug B" })
    }

    @Test
    fun `DrugRepository - findAllByMedKitId returns empty list for empty medkit`() {
        val user = createUser()
        val medKit = createMedKitForUser(user)

        val drugs = transaction(db) { drugRepository.findAllByMedKitId(medKit.id) }
        assertTrue(drugs.isEmpty())
    }

    @Test
    fun `DrugRepository - findByIdAndMedKitUsersId returns drug for authorized user`() {
        val user = createUser()
        val medKit = createMedKitForUser(user)
        val drug = createDrug(medKit)

        val found = transaction(db) { drugRepository.findByIdAndMedKitUsersId(drug.id, user.id) }
        assertNotNull(found)
        assertEquals(drug.id, found.id)
    }

    @Test
    fun `DrugRepository - findByIdAndMedKitUsersId returns null for unauthorized user`() {
        val user1 = createUser()
        val user2 = createUser()
        val medKit = createMedKitForUser(user1)
        val drug = createDrug(medKit)

        val found = transaction(db) { drugRepository.findByIdAndMedKitUsersId(drug.id, user2.id) }
        assertNull(found)
    }

    @Test
    fun `DrugRepository - findByUsingsUserId returns drugs user has treatment plans for`() {
        val user = createUser()
        val medKit = createMedKitForUser(user)
        val drug1 = createDrug(medKit, "Drug A")
        createDrug(medKit, "Drug B")

        transaction(db) {
            usingRepository.save(Using(userId = user.id, drugId = drug1.id, plannedAmount = 10.0))
        }

        val drugs = transaction(db) { drugRepository.findByUsingsUserId(user.id) }
        assertEquals(1, drugs.size)
        assertEquals(drug1.id, drugs[0].id)
    }

    @Test
    fun `DrugRepository - sumPlannedAmount returns sum of planned amounts`() {
        val user1 = createUser()
        val user2 = createUser()
        val medKit = createMedKitForUser(user1)
        transaction(db) { medKitRepository.addUserToMedKit(user2.id, medKit.id) }
        val drug = createDrug(medKit)

        transaction(db) {
            usingRepository.save(Using(userId = user1.id, drugId = drug.id, plannedAmount = 20.0))
            usingRepository.save(Using(userId = user2.id, drugId = drug.id, plannedAmount = 30.0))
        }

        val sum = transaction(db) { drugRepository.sumPlannedAmount(drug.id) }
        assertEquals(50.0, sum)
    }

    @Test
    fun `DrugRepository - sumPlannedAmount returns 0 when no usings exist`() {
        val user = createUser()
        val medKit = createMedKitForUser(user)
        val drug = createDrug(medKit)

        val sum = transaction(db) { drugRepository.sumPlannedAmount(drug.id) }
        assertEquals(0.0, sum)
    }

    // === MedKitRepository Tests ===

    @Test
    fun `MedKitRepository - findByUsersId returns medkits for user`() {
        val user = createUser()
        createMedKitForUser(user)
        createMedKitForUser(user)

        val medKits = transaction(db) { medKitRepository.findByUsersId(user.id) }
        assertEquals(2, medKits.size)
    }

    @Test
    fun `MedKitRepository - findByUsersId returns empty for user with no medkits`() {
        val user = createUser()

        val medKits = transaction(db) { medKitRepository.findByUsersId(user.id) }
        assertTrue(medKits.isEmpty())
    }

    @Test
    fun `MedKitRepository - findByIdAndUserId returns medkit for authorized user`() {
        val user = createUser()
        val medKit = createMedKitForUser(user)

        val found = transaction(db) { medKitRepository.findByIdAndUserId(medKit.id, user.id) }
        assertNotNull(found)
        assertEquals(medKit.id, found.id)
    }

    @Test
    fun `MedKitRepository - findByIdAndUserId returns null for unauthorized user`() {
        val user1 = createUser()
        val user2 = createUser()
        val medKit = createMedKitForUser(user1)

        val found = transaction(db) { medKitRepository.findByIdAndUserId(medKit.id, user2.id) }
        assertNull(found)
    }

    @Test
    fun `MedKitRepository - countUsersInMedKit returns correct count`() {
        val user1 = createUser()
        val user2 = createUser()
        val medKit = createMedKitForUser(user1)
        transaction(db) { medKitRepository.addUserToMedKit(user2.id, medKit.id) }

        val count = transaction(db) { medKitRepository.countUsersInMedKit(medKit.id) }
        assertEquals(2L, count)
    }

    // === UserRepository Tests ===

    @Test
    fun `UserRepository - findByMedKitsId returns users in medkit`() {
        val user1 = createUser()
        val user2 = createUser()
        val medKit = createMedKitForUser(user1)
        transaction(db) { medKitRepository.addUserToMedKit(user2.id, medKit.id) }

        val users = transaction(db) { userRepository.findByMedKitsId(medKit.id) }
        assertEquals(2, users.size)
    }

    @Test
    fun `UserRepository - findByUsingsDrugId returns users with treatment plans for drug`() {
        val user1 = createUser()
        val user2 = createUser()
        val medKit = createMedKitForUser(user1)
        transaction(db) { medKitRepository.addUserToMedKit(user2.id, medKit.id) }
        val drug = createDrug(medKit)

        transaction(db) {
            usingRepository.save(Using(userId = user1.id, drugId = drug.id, plannedAmount = 10.0))
        }

        val users = transaction(db) { userRepository.findByUsingsDrugId(drug.id) }
        assertEquals(1, users.size)
        assertTrue(users.any { it.id == user1.id })
    }

    // === UsingRepository Tests ===

    @Test
    fun `UsingRepository - findAllByUserId returns usings for user`() {
        val user = createUser()
        val medKit = createMedKitForUser(user)
        val drug1 = createDrug(medKit, "Drug A")
        val drug2 = createDrug(medKit, "Drug B")

        transaction(db) {
            usingRepository.save(Using(userId = user.id, drugId = drug1.id, plannedAmount = 10.0))
            usingRepository.save(Using(userId = user.id, drugId = drug2.id, plannedAmount = 20.0))
        }

        val usings = transaction(db) { usingRepository.findAllByUserId(user.id) }
        assertEquals(2, usings.size)
    }

    @Test
    fun `UsingRepository - findAllByDrugId returns usings for drug`() {
        val user1 = createUser()
        val user2 = createUser()
        val medKit = createMedKitForUser(user1)
        transaction(db) { medKitRepository.addUserToMedKit(user2.id, medKit.id) }
        val drug = createDrug(medKit)

        transaction(db) {
            usingRepository.save(Using(userId = user1.id, drugId = drug.id, plannedAmount = 10.0))
            usingRepository.save(Using(userId = user2.id, drugId = drug.id, plannedAmount = 20.0))
        }

        val usings = transaction(db) { usingRepository.findAllByDrugId(drug.id) }
        assertEquals(2, usings.size)
    }

    @Test
    fun `UsingRepository - findByUserIdAndDrugId returns specific using`() {
        val user = createUser()
        val medKit = createMedKitForUser(user)
        val drug = createDrug(medKit)

        transaction(db) {
            usingRepository.save(Using(userId = user.id, drugId = drug.id, plannedAmount = 15.0))
        }

        val using = transaction(db) { usingRepository.findByUserIdAndDrugId(user.id, drug.id) }
        assertNotNull(using)
        assertEquals(15.0, using.plannedAmount)
    }

    @Test
    fun `UsingRepository - findByUserIdAndDrugId returns null when not found`() {
        val user = createUser()
        val medKit = createMedKitForUser(user)
        val drug = createDrug(medKit)

        val using = transaction(db) { usingRepository.findByUserIdAndDrugId(user.id, drug.id) }
        assertNull(using)
    }
}
