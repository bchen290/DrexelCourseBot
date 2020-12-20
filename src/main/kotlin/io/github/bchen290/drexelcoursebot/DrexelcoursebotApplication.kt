package io.github.bchen290.drexelcoursebot

import de.vandermeer.asciitable.AsciiTable
import de.vandermeer.asciitable.CWC_LongestWord
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
                        if (message.content.startsWith(prefix)) {
                            Flux.fromIterable(commands.entries)
                                .filter { entry -> message.content.startsWith(prefix + entry.key) }
                                .flatMap { entry -> entry.value.execute(event) }
                                .next()
                        } else {
                            Flux.just(message.channel)
                                .doOnNext { _ ->
                                    val author = message.author.get()
                                    val content = message.content

                                    val state = courseCog.userStateMap[author.tag]

                                    author.privateChannel.flatMap { privateChannel ->
                                        when (state) {
                                            QuestionStates.QUARTER -> {
                                                val quarterStates = try {
                                                    QuarterState.values()[content.toInt() - 1]
                                                } catch (_: IndexOutOfBoundsException) {
                                                    QuarterState.QUIT
                                                }

                                                if (quarterStates == QuarterState.QUIT) {
                                                    privateChannel.createMessage("Quitting")
                                                } else {
                                                    courseCog.userResponseMap[author.tag] = mutableListOf(content)

                                                    courseCog.userStateMap[author.tag] = QuestionStates.FILTER_BY
                                                    privateChannel.createMessage("What do you want to filter by:\n${FilterOptions.values().withIndex().joinToString(separator = "\n") { "${it.index + 1}: ${it.value.str}" }}")
                                                }
                                            }
                                            QuestionStates.FILTER_BY -> {
                                                val filterOptions = try {
                                                    FilterOptions.values()[content.toInt() - 1]
                                                } catch (ex: Exception) {
                                                    when(ex) {
                                                        is IndexOutOfBoundsException, is NumberFormatException -> {
                                                            FilterOptions.QUIT
                                                        }
                                                        else -> throw ex
                                                    }
                                                }

                                                courseCog.userResponseMap[author.tag]?.add(FilterOptions.values()[content.toInt() - 1].toString())
                                                courseCog.userStateMap[author.tag] = QuestionStates.FILTER_BY_MORE

                                                when(filterOptions) {
                                                    FilterOptions.SUBJECT -> {
                                                        privateChannel.createMessage("Enter subject code and course number(e.g. CS164 or CS 164")
                                                    }
                                                    FilterOptions.CRN -> {
                                                        privateChannel.createMessage("Enter CRN")
                                                    }
                                                    FilterOptions.PROFESSOR -> {
                                                        privateChannel.createMessage("Enter Professor Name")
                                                    }
                                                    FilterOptions.CREDITS -> {
                                                        privateChannel.createMessage("Enter number of credits")
                                                    }
                                                    FilterOptions.PREREQUISITES -> {
                                                        courseCog.userResponseMap.remove(author.tag)
                                                        courseCog.userStateMap.remove(author.tag)

                                                        privateChannel.createMessage("Getting courses")
                                                    }
                                                    FilterOptions.QUIT -> {
                                                        privateChannel.createMessage("Quitting")
                                                    }
                                                }
                                            }
                                            QuestionStates.FILTER_BY_MORE -> {
                                                when(FilterOptions.valueOf(courseCog.userResponseMap[author.tag]?.get(1) ?: "Quit")) {
                                                    FilterOptions.SUBJECT -> {
                                                        val courseTitle = content.replace("""([a-zA-Z](?=\d))""".toRegex(), "$1 ").split(" ")

                                                        val subjectCode = courseTitle[0].toUpperCase()
                                                        val courseNumber = courseTitle[1]

                                                        transaction {
                                                            val result = (Courses innerJoin Subjects innerJoin Schools innerJoin Quarters).select {
                                                                (Courses.subject eq Subjects.id).and(Subjects.code eq subjectCode).and(Courses.number eq courseNumber).and(Quarters.name like (courseCog.userResponseMap[author.tag]?.get(0) ?: ""))
                                                            }.limit(1, 0)
                                                            val courses = Course.wrapRows(result).toList()

                                                            Flux.fromIterable(courses).map {
                                                                privateChannel.createMessage("%s %s %s %s %s %s %s %s %s".format(it.instructorType, it.instructorMethod, it.section, it.crn, it.time, it.instructor, it.credit, it.seatsAvailable, it.sectionComment)).then()
                                                            }
                                                        }

                                                        privateChannel.createMessage("Retrieving courses")
                                                    }
                                                    FilterOptions.CRN -> {
                                                        privateChannel.createMessage("")
                                                    }
                                                    FilterOptions.PROFESSOR -> {
                                                        privateChannel.createMessage("")
                                                    }
                                                    FilterOptions.CREDITS -> {
                                                        privateChannel.createMessage("")
                                                    }
                                                    FilterOptions.PREREQUISITES -> {
                                                        privateChannel.createMessage("")
                                                    }
                                                    FilterOptions.QUIT -> {
                                                        privateChannel.createMessage("")
                                                    }
                                                }
                                            }
                                            else -> {
                                                privateChannel.createMessage("Invalid State")
                                            }
                                        }
                                    }.subscribe()
                                }.next()
                        }
                    }
                }.subscribe()

            client.onDisconnect().block()
        }
    }
}

fun main(args: Array<String>) {
    runApplication<DrexelcoursebotApplication>(*args)
}
