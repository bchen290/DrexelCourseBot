package io.github.bchen290.drexelcoursebot

import discord4j.core.DiscordClientBuilder
import discord4j.core.`object`.entity.Message
import discord4j.core.`object`.entity.User
import discord4j.core.event.domain.lifecycle.ReadyEvent
import discord4j.core.event.domain.message.MessageCreateEvent
import discord4j.core.event.domain.message.ReactionAddEvent
import io.github.bchen290.drexelcoursebot.database.DatabaseHelper
import io.github.bchen290.drexelcoursebot.database.table.*
import io.github.bchen290.drexelcoursebot.utility.TermMasterScraper
import io.github.bchen290.drexelcoursebot.utility.commands.Command
import io.github.bchen290.drexelcoursebot.utility.isNotBot
import org.jetbrains.exposed.sql.SchemaUtils
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
import java.util.*

@SpringBootApplication
class DrexelcoursebotApplication {
    @Bean
    fun commandLineRunner(ctx: ApplicationContext) : CommandLineRunner {
        return CommandLineRunner {
            val prop = Properties()
            prop.load(FileInputStream("env.properties"))
            val prefix = prop.getProperty("prefix")

            val messageCommands = mutableMapOf<String, Command>()

            val client = DiscordClientBuilder.create(prop.getProperty("botToken"))
                    .build()
                    .login()
                    .block()!!

            client.on(ReadyEvent::class.java)
                    .subscribe {
                        print("Logged in as %s#%s\n".format(it.self.username, it.self.discriminator))
                    }

            val courseCog = CourseCog(messageCommands)
            DatabaseHelper.setupDB(prop)

            if (prop.getProperty("shouldScrape")?.toBoolean() == true) {
                TermMasterScraper()
                prop.setProperty("shouldScrape", "false")
            }

            if (prop.getProperty("recreateDatabase")?.toBoolean() == true) {
                transaction {
                    SchemaUtils.drop(ChannelMessages)
                    SchemaUtils.create(ChannelMessages)
                }

                prop.setProperty("recreateDatabase", "false")
            }

            prop.store(FileOutputStream("env.properties"), null)

            client.eventDispatcher.on(MessageCreateEvent::class.java)
                .flatMap { event -> Mono.just(event.message)
                    .filter { message -> message.author.map(User::isNotBot).orElse(false) }
                    .flatMap { message ->
                        Flux.fromIterable(messageCommands.entries)
                            .filter { entry -> message.content.split(" ")[0].contentEquals(prefix + entry.key) }
                            .flatMap { entry -> entry.value.execute(event) }
                            .next()
                    }
                }.subscribe()

            client.eventDispatcher.on(ReactionAddEvent::class.java)
                .flatMap { event -> Mono.justOrEmpty(event)
                    .filterWhen { event.user.map(User::isNotBot) }
                    .filterWhen { event.message.map(Message::getAuthor).flatMap { Mono.justOrEmpty(it).map { user -> user.isBot } }}
                    .map { courseCog.onReactionAdded(event) }
                }.subscribe()

            client.onDisconnect().block()
        }
    }
}

fun main(args: Array<String>) {
    runApplication<DrexelcoursebotApplication>(*args)
}
