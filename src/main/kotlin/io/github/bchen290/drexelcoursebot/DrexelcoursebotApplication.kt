package io.github.bchen290.drexelcoursebot

import discord4j.core.DiscordClientBuilder
import discord4j.core.event.domain.lifecycle.ReadyEvent
import discord4j.core.event.domain.message.MessageCreateEvent
import io.github.bchen290.drexelcoursebot.database.DatabaseHelper
import io.github.bchen290.drexelcoursebot.database.entity.Course
import io.github.bchen290.drexelcoursebot.database.table.Courses
import io.github.bchen290.drexelcoursebot.database.table.Quarters
import io.github.bchen290.drexelcoursebot.database.table.Schools
import io.github.bchen290.drexelcoursebot.database.table.Subjects
import io.github.bchen290.drexelcoursebot.utility.states.QuestionStates
import io.github.bchen290.drexelcoursebot.utility.TermMasterScraper
import io.github.bchen290.drexelcoursebot.utility.commands.Command
import io.github.bchen290.drexelcoursebot.utility.states.FilterOptions
import io.github.bchen290.drexelcoursebot.utility.states.QuarterState
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.springframework.boot.CommandLineRunner
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.io.FileInputStream
import java.io.FileOutputStream
import java.lang.Exception
import java.lang.NumberFormatException
import java.util.*

@SpringBootApplication
class DrexelcoursebotApplication {
    @Bean
    fun commandLineRunner(ctx: ApplicationContext) : CommandLineRunner {
        return CommandLineRunner {
            val prop = Properties()
            prop.load(FileInputStream("env.properties"))
            val prefix = prop.getProperty("prefix")

            val commands = mutableMapOf<String, Command>()

            val client = DiscordClientBuilder.create(prop.getProperty("botToken"))
                    .build()
                    .login()
                    .block()!!

            client.on(ReadyEvent::class.java)
                    .subscribe {
                        print("Logged in as %s#%s\n".format(it.self.username, it.self.discriminator))
                    }

            val courseCog = CourseCog(commands)
            DatabaseHelper.setupDB(prop)

            if (prop.getProperty("shouldScrape")?.toBoolean() == true) {
                TermMasterScraper()
                prop.setProperty("shouldScrape", "false")
                prop.store(FileOutputStream("env.properties"), null)
            }

            client.eventDispatcher.on(MessageCreateEvent::class.java)
                .flatMap { event -> Mono.just(event.message)
                    .filter { message -> message.author.map { user -> !user.isBot }.orElse(false) }
                    .flatMap { message ->
                        Flux.fromIterable(commands.entries)
                            .filter { entry -> message.content.startsWith(prefix + entry.key) }
                            .flatMap { entry -> entry.value.execute(event) }
                            .next()
                    }
                }.subscribe()

            client.onDisconnect().block()
        }
    }
}

fun main(args: Array<String>) {
    runApplication<DrexelcoursebotApplication>(*args)
}
