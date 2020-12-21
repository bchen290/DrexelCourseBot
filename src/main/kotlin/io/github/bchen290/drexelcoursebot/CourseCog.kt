package io.github.bchen290.drexelcoursebot

import discord4j.core.`object`.entity.channel.PrivateChannel
import io.github.bchen290.drexelcoursebot.database.entity.Course
import io.github.bchen290.drexelcoursebot.database.table.Courses
import io.github.bchen290.drexelcoursebot.database.table.Subjects
import io.github.bchen290.drexelcoursebot.utility.states.QuestionStates
import io.github.bchen290.drexelcoursebot.utility.commands.Command
import io.github.bchen290.drexelcoursebot.utility.states.QuarterState
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import reactor.core.publisher.Mono

class CourseCog(commands: MutableMap<String, Command>) {
    init {
        commands["ping"] = Command { event ->
            Mono.justOrEmpty(event.message)
                .map { Pair(it.content, it.channel) }
                .doOnNext { it.second.flatMap { channel -> channel.createMessage("Pong") }.subscribe() }
                .then()
        }

        commands["desc"] = Command { event ->
            Mono.justOrEmpty(event.message)
                .map { message ->
                    transaction {
                        val content = message.content
                        val channel = message.author.get().privateChannel

                        // Add space in between subject and course number
                        val courseTitle = content.replace("""([a-zA-Z](?=\d))""".toRegex(), "$1 ").split(" ")

                        val subjectCode = courseTitle[1].toUpperCase()
                        val courseNumber = courseTitle[2]

                        val result = (Courses innerJoin Subjects).select { (Courses.subject eq Subjects.id).and(Subjects.code eq subjectCode).and(Courses.number eq courseNumber) }
                        val course = Course.wrapRows(result).toList().first()

                        channel.flatMap { currentChannel -> currentChannel.createEmbed { embedCreator ->
                            embedCreator.setTitle(subjectCode + course.number + ": " + course.title)
                                .setUrl(course.url)
                                .setDescription(course.description)
                        } }.subscribe()

                        channel.flatMap { currentChannel -> currentChannel.createEmbed { embedCreator ->
                            embedCreator.addField("Prerequisites", course.prerequisite, false)
                        } }.subscribe()

                        channel.flatMap { currentChannel -> currentChannel.createEmbed { embedCreator ->
                            embedCreator.addField("Co-Requisites", course.corequisites, false)
                        } }.subscribe()

                        channel.flatMap { currentChannel -> currentChannel.createEmbed { embedCreator ->
                            embedCreator.addField("Restrictions", course.restrictions, false)
                        } }.subscribe()
                    }
                }.then()
        }

        commands["courses"] = Command { event ->
            Mono.justOrEmpty(event.message)
                .map { message ->
                    val author = message.author.get()
                    val messageToSend = ""

                    transaction {
                        Course.all()
                    }

                    author.privateChannel.flatMap { channel ->
                        channel.createMessage("Select a quarter:\n${QuarterState.values().withIndex().joinToString(separator = "\n") { "${it.index + 1}: ${it.value.quarter}" }}")
                    }.subscribe()
                }
                .then()
        }
    }
}