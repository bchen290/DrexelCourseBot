package io.github.bchen290.drexelcoursebot

import discord4j.core.DiscordClientBuilder
import discord4j.core.event.domain.lifecycle.ReadyEvent
import io.github.bchen290.drexelcoursebot.database.DatabaseHelper
import org.springframework.boot.CommandLineRunner
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import java.io.File

@SpringBootApplication
class DrexelcoursebotApplication {
    @Bean
    fun commandLineRunner(ctx: ApplicationContext) : CommandLineRunner {
        return CommandLineRunner {
            val token = File("bot_token.txt").readText(Charsets.UTF_8)

            val client = DiscordClientBuilder.create(token)
                    .build()
                    .login()
                    .block()!!

            client.on(ReadyEvent::class.java)
                    .subscribe {
                        print("Logged in as %s#%s\n".format(it.self.username, it.self.discriminator))
                    }

            CourseCog(client)
            DatabaseHelper

            client.onDisconnect().block()
        }
    }
}

fun main(args: Array<String>) {
    runApplication<DrexelcoursebotApplication>(*args)
}
