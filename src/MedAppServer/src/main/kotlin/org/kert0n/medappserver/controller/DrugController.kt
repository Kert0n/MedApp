package org.kert0n.medappserver.controller

import org.kert0n.medappserver.db.model.DrugDTO
import org.kert0n.medappserver.db.model.DrugPostDTO
import org.kert0n.medappserver.db.model.Using
import org.kert0n.medappserver.db.model.UsingKey
import org.kert0n.medappserver.db.repository.DrugRepository
import org.kert0n.medappserver.db.repository.UserRepository
import org.kert0n.medappserver.services.userId
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import java.util.*

@RestController
@RequestMapping("/drug")
class DrugController(private val userRepository: UserRepository, private val drugRepository: DrugRepository) {

    @GetMapping("/{id}")
    fun getDrug(authentication: Authentication, @PathVariable id: UUID): DrugDTO {
        val find = drugRepository.findByIdAndUsingsUserId(id, authentication.userId) ?: throw ResponseStatusException(
            HttpStatus.NOT_FOUND
        )
    }

    @PostMapping
    fun postDrugs(authentication: Authentication, drugs: Set<DrugPostDTO>): UUID {
        val myMedKits = (userRepository.findByIdOrNull(authentication.userId) ?: throw ResponseStatusException(
            HttpStatus.NOT_FOUND
        )).medKits.map { it.id }.toSet()
        val approved = drugs.filter { myMedKits.contains(it.owner) }

    }

    @GetMapping("/light/{id}")
    fun getState(authentication: Authentication, @PathVariable id: UUID): Pair<Double, Double> {
        val drug = drugRepository.findByIdAndUsingsUserId(id, authentication.userId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
        val plannedQuantity = drug.usings.sumOf { it.plannedAmount }
        return drug.quantity to plannedQuantity
    }

    @PutMapping("/{id}")
    fun consume(authentication: Authentication, @PathVariable id: UUID, quantity: Double): Double {
        val drug = drugRepository.findByIdAndUsingsUserId(id, authentication.userId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
        if (quantity > drug.quantity) throw ResponseStatusException(HttpStatus.BAD_REQUEST)
        drug.quantity -= quantity
        if(getState(authentication,id).second<drug.quantity) {

        }
    }

    @PutMapping("/plan")
    fun initiateConsumePlan(authentication: Authentication, drugId: UUID, quantity: Double): Double {
      val plan = Using(UsingKey(authentication.userId,drugId),plannedAmount = quantity)
    }

    @PutMapping("/plan/{id}")
    fun plannedIntake(authentication: Authentication, @PathVariable id: UUID, quantityConsumed: Double): Double {

    }

    @PutMapping("/change_plan/{id}")
    fun changePlan(authentication: Authentication, @PathVariable id: UUID, quantityConsumed: Double): Double {

    }

}