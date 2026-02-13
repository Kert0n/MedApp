package org.kert0n.medappserver

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class MedAppServerApplication

fun main(args: Array<String>) {
    runApplication<MedAppServerApplication>(*args)
}
