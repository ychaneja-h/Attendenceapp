package com.attendance.server

import com.attendance.server.config.DatabaseConfig
import com.attendance.server.plugins.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*

fun main() {
    embeddedServer(
        Netty,
        environment = applicationEngineEnvironment {
            log = org.slf4j.LoggerFactory.getLogger("ktor.application")
            connector {
                port = System.getenv("PORT")?.toIntOrNull() ?: 8080
                host = "0.0.0.0"
            }
            module(Application::module)
        }
    ).start(wait = true)
}

fun Application.module() {
    DatabaseConfig.init()
    com.attendance.server.services.AuthService.seedDefaultUsers()
    configureSerialization()
    configureSecurity()
    configureStatusPages()
    configureCORS()
    configureRateLimit()
    configureRouting()
}


