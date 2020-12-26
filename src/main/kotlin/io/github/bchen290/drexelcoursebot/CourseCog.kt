package io.github.bchen290.drexelcoursebot

import discord4j.core.event.domain.message.ReactionAddEvent
import io.github.bchen290.drexelcoursebot.database.entity.ChannelMessage
import io.github.bchen290.drexelcoursebot.database.entity.Course
import io.github.bchen290.drexelcoursebot.database.table.ChannelMessages
import io.github.bchen290.drexelcoursebot.database.table.Courses
import io.github.bchen290.drexelcoursebot.database.table.Subjects
import io.github.bchen290.drexelcoursebot.utility.Emojis
import io.github.bchen290.drexelcoursebot.utility.TableCreator
import io.github.bchen290.drexelcoursebot.utility.Utilities
import io.github.bchen290.drexelcoursebot.utility.commands.Command
import io.github.bchen290.drexelcoursebot.utility.states.CoursesState
import io.github.bchen290.drexelcoursebot.utility.states.FilterOptions
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.like
import org.jetbrains.exposed.sql.transactions.transaction
import reactor.core.publisher.Mono
import java.io.ByteArrayInputStream
import kotlin.IndexOutOfBoundsException

class CourseCog(messageCommands: MutableMap<String, Command>) {
    private val rowsPerPage = 10

    init {
        messageCommands["ping"] = Command { event ->
            event.message.channel.flatMap {
                it.createMessage("Pong!")
            }.then()
        }

        messageCommands["help"] = Command { event ->
            event.message.channel.flatMap {
                it.createMessage("""
                    ```
                    help - show this message
                    course - show course detail for a single course (ex. !course CS270 or !course CS 270)
                    courses - show all courses which can be filtered with the following filters (ex. !courses sub cs prof Char)
                        - sub, subject: filter by subject code
                        - crn: filter by crn
                        - prof, professor: filter by professor
                        - cred, credit: filter by credits
                        - prereq, pre-req, prerequisite, pre-requisite: filter by no prerequisites
                    ```
                """.trimIndent())
            }.then()
        }

        messageCommands["courses"] = Command { event ->
            var messageContent = event.message.content.split(" ")
            messageContent = messageContent.drop(1)

            val filterMap = mutableMapOf<FilterOptions, String>()

            val coursesState = if (messageContent.isNotEmpty()) {
                convertMessageToFilterMap(messageContent, filterMap)
            } else {
                CoursesState.ALL_COURSES
            }

            val (messageToSend, fileMessage, fileByteArray) = when(coursesState) {
                CoursesState.ALL_COURSES -> {
                    transaction {
                        val courses = Utilities.courseIterableToStringCSV(Course.all().toList())
                        Triple("Generating CSV...", null, Utilities.appendHeaderCSV(courses).toByteArray(Charsets.UTF_8))
                    }
                }
                CoursesState.FILTER -> {
                    transaction {
                        val query: Op<Boolean>? = filterMapToQuery(filterMap)

                        val coursesList = query?.let { Course.wrapRows(Courses.innerJoin(Subjects).select { it }).toList() }
                        val courses = coursesList?.let { Utilities.courseIterableToStringCSV(it) }

                        val coursesLimit = query?.let { Utilities.courseIterableToStringTable(Course.wrapRows(Courses.innerJoin(Subjects).select { it }.limit(rowsPerPage, 0))) }

                        val table = TableCreator.csvToTable(Utilities.appendHeaderTable(coursesLimit ?: ""))
                        Triple("Generating CSV...", "${coursesList?.size ?: 0}|$table", Utilities.appendHeaderCSV(courses ?: "An error has occurred").toByteArray())
                    }
                }
                CoursesState.ILLEGAL_ARGUMENT, CoursesState.NOT_ENOUGH_ARGUMENT -> {
                    Triple("Invalid usage of command", null, null)
                }
            }

            event.message.channel.flatMap { channel ->
                if (fileByteArray == null) {
                    channel.createMessage(messageToSend)
                } else {
                    Mono.`when`(channel.createMessage(messageToSend), channel.createMessage {
                        if (fileMessage != null) {
                            it.setContent(fileMessage.split("|")[1])
                        }
                        it.addFile("courses.csv", ByteArrayInputStream(fileByteArray))
                    }.flatMap { message ->
                        if (fileMessage != null) {
                            transaction {
                                val messageChannelID = message.channelId.asString()
                                val messageMessageID = message.id.asString()

                                val maxPages = (fileMessage.split("|")[0].toInt() / rowsPerPage) + 1

                                ChannelMessages.insertIgnore {
                                    it[channelID] = messageChannelID
                                    it[messageID] = messageMessageID
                                    it[content] = event.message.content
                                    it[offset] = 0
                                    it[maxOffset] = maxPages
                                }
                            }

                            Mono.`when`(
                                message.addReaction(Emojis.ARROW_DOUBLE_UP.reactionEmoji),
                                message.addReaction(Emojis.ARROW_UP.reactionEmoji),
                                message.addReaction(Emojis.ARROW_DOWN.reactionEmoji),
                                message.addReaction(Emojis.ARROW_DOUBLE_DOWN.reactionEmoji)
                            )
                        } else {
                            Mono.empty()
                        }
                    })
                }
            }.then()
        }

        messageCommands["course"] = Command { event ->
            val content = event.message.content

            // Add space in between subject and course number
            val courseTitle = content.replace("""([a-zA-Z](?=\d))""".toRegex(), "$1 ").split(" ")

            val subjectCode = courseTitle[1].toUpperCase()
            val courseNumber = courseTitle[2]

            try {
                val course = transaction {
                    val result = (Courses innerJoin Subjects).select {
                        (Courses.subject eq Subjects.id).and(Subjects.code eq subjectCode)
                            .and(Courses.number eq courseNumber)
                    }
                    Course.wrapRows(result).toList().first()
                }

                event.message.channel.flatMap { currentChannel ->
                    Mono.`when`(currentChannel.createEmbed { embedCreator ->
                        embedCreator.setTitle(subjectCode + course.number + ": " + course.title)
                            .setUrl(course.url)
                            .setDescription(course.description)
                    }, currentChannel.createEmbed { embedCreator ->
                        embedCreator.addField("Prerequisites", course.prerequisite, false)
                    }, currentChannel.createEmbed { embedCreator ->
                        embedCreator.addField("Co-Requisites", course.corequisites, false)
                    }, currentChannel.createEmbed { embedCreator ->
                        embedCreator.addField("Restrictions", course.restrictions, false)
                    })
                }
            } catch (_: NoSuchElementException) {
                event.message.channel.flatMap { currentChannel ->
                    currentChannel.createMessage("Course Not Found")
                }.then()
            }
        }
    }

    fun onReactionAdded(event: ReactionAddEvent) {
        val channelID = event.channelId.asString()
        val messageID = event.messageId.asString()

        val emoji = try {
            Emojis.values().first { it.reactionEmoji == event.emoji.asUnicodeEmoji().get() }
        } catch (_: NoSuchElementException) {
            return
        }

        val channelMessage = try {
            transaction {
                val query = ChannelMessages.select {
                    (ChannelMessages.channelID eq channelID).and(ChannelMessages.messageID eq messageID)
                }

                ChannelMessage.wrapRows(query).toList().first()
            }
        } catch (_: NoSuchElementException) {
            return
        }

        var messageContent = channelMessage.content.split(" ")
        messageContent = messageContent.drop(1)

        val filterMap = mutableMapOf<FilterOptions, String>()
        convertMessageToFilterMap(messageContent, filterMap)

        val query: Op<Boolean>? = filterMapToQuery(filterMap)

        val newOffset = when(emoji) {
            Emojis.ARROW_DOUBLE_UP -> 0
            Emojis.ARROW_UP -> if (channelMessage.offset - 1 < 0) 0 else channelMessage.offset - 1
            Emojis.ARROW_DOWN -> if (channelMessage.offset + 1 > channelMessage.maxOffset) channelMessage.maxOffset else channelMessage.offset + 1
            Emojis.ARROW_DOUBLE_DOWN -> channelMessage.maxOffset
        }

        val coursesLimit = transaction {
            ChannelMessages.update({ (ChannelMessages.channelID eq channelID).and(ChannelMessages.messageID eq messageID) }) {
                it[offset] = newOffset
            }

            query?.let { Utilities.courseIterableToStringTable(Course.wrapRows(Courses.innerJoin(Subjects).select { it }.limit(rowsPerPage, (newOffset * rowsPerPage).toLong()))) }
        }

        val table = TableCreator.csvToTable(Utilities.appendHeaderTable(coursesLimit ?: ""))

        event.channel.flatMap {
            event.message
        }.flatMap { message ->
            message.edit { messageEditSpec ->
                messageEditSpec.setContent(table)
            }
        }.subscribe()
    }

    private fun convertMessageToFilterMap(messageContent: List<String>, filterMap: MutableMap<FilterOptions, String>): CoursesState {
        var content = messageContent

        try {
            while (content.isNotEmpty()) {
                val filterOptions = when(content[0].toUpperCase()) {
                    "SUBJECT", "SUB" -> FilterOptions.SUBJECT
                    "CRN" -> FilterOptions.CRN
                    "PROFESSOR", "PROF" -> FilterOptions.PROFESSOR
                    "CREDIT" , "CRED" -> FilterOptions.CREDITS
                    "PREREQ", "PRE-REQ", "PREREQUISITE", "PRE-REQUISITE" -> FilterOptions.PREREQUISITES
                    else -> null
                }

                when (filterOptions) {
                    FilterOptions.SUBJECT,
                    FilterOptions.CRN,
                    FilterOptions.PROFESSOR,
                    FilterOptions.CREDITS -> {
                        filterMap[filterOptions] = content[1]
                        content = content.drop(2)
                    }
                    FilterOptions.PREREQUISITES -> {
                        filterMap[filterOptions] = "true"
                        content = content.drop(1)
                    }
                    null -> {
                        throw IllegalArgumentException()
                    }
                }
            }

            return CoursesState.FILTER
        } catch (_: IllegalArgumentException) {
            return CoursesState.ILLEGAL_ARGUMENT
        } catch (_: IndexOutOfBoundsException) {
            return CoursesState.NOT_ENOUGH_ARGUMENT
        }
    }

    private fun filterMapToQuery(filterMap: MutableMap<FilterOptions, String>): Op<Boolean>? {
        var query: Op<Boolean>? = null

        filterMap.forEach { (filterOption, userArgument) ->
            val queryOption = when (filterOption) {
                FilterOptions.SUBJECT -> Subjects.code eq userArgument.toUpperCase()
                FilterOptions.CRN -> Courses.crn like "%$userArgument%"
                FilterOptions.PROFESSOR -> Courses.instructor.lowerCase() like "%$userArgument%".toLowerCase()
                FilterOptions.CREDITS -> Courses.credit like "%$userArgument%"
                FilterOptions.PREREQUISITES -> Courses.prerequisites eq "N/A"
            }

            query = query?.and(queryOption) ?: queryOption
        }

        return query
    }
}