package com.mogumogu.momogo.global.config

import org.springframework.core.env.Environment
import org.springframework.stereotype.Component

@Component
class ApplicationPhase(environment: Environment) {

    val value: String = environment.activeProfiles.first()
}
