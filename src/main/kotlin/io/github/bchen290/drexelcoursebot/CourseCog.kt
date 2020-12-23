package io.github.bchen290.drexelcoursebot

import io.github.bchen290.drexelcoursebot.database.entity.Course
import io.github.bchen290.drexelcoursebot.database.table.Courses
import io.github.bchen290.drexelcoursebot.database.table.Subjects
import io.github.bchen290.drexelcoursebot.utility.TableCreator
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

class CourseCog(commands: MutableMap<String, Command>) {
    init {
        commands["ping"] = Command { event ->
            event.message.channel.flatMap {
                it.createMessage("Pong!")
            }.then()
        }

        commands["courses"] = Command { event ->
            var content = event.message.content.split(" ")
            content = content.drop(1)

            val filterMap = mutableMapOf<FilterOptions, String>()

            val coursesState = if (content.isNotEmpty()) {
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

                    CoursesState.FILTER
                } catch (_: IllegalArgumentException) {
                    CoursesState.ILLEGAL_ARGUMENT
                } catch (_: IndexOutOfBoundsException) {
                    CoursesState.NOT_ENOUGH_ARGUMENT
                }
            } else {
                CoursesState.ALL_COURSES
            }

            val (messageToSend, fileMessage, fileByteArray) = when(coursesState) {
                CoursesState.ALL_COURSES -> {
                    transaction {
                        val courses = courseIterableToStringCSV(Course.all())
                        Triple("Generating CSV...", null, appendHeaderCSV(courses).toByteArray(Charsets.UTF_8))
                    }
                }
                CoursesState.FILTER -> {
                    transaction {
                        var query: Op<Boolean>? = null

                        filterMap.forEach { (filterOption, userArgument) ->
                            val queryOption = when (filterOption) {
                                FilterOptions.SUBJECT -> Subjects.code eq userArgument.toUpperCase()
                                FilterOptions.CRN -> Courses.crn like "%$userArgument%"
                                FilterOptions.PROFESSOR -> Courses.instructor like "%$userArgument%"
                                FilterOptions.CREDITS -> Courses.credit like "%$userArgument%"
                                FilterOptions.PREREQUISITES -> Courses.prerequisites eq "N/A"
                            }

                            query = query?.and(queryOption) ?: queryOption
                        }

                        val courses = query?.let { courseIterableToStringCSV(Course.wrapRows(Courses.innerJoin(Subjects).select { it })) }
                        val coursesLimit = query?.let { courseIterableToStringTable(Course.wrapRows(Courses.innerJoin(Subjects).select { it }.limit(10, 0))) }

                        val table = TableCreator.csvToTable(appendHeaderTable(coursesLimit ?: ""))
                        Triple("Generating CSV...", table, appendHeaderCSV(courses ?: "An error has occurred").toByteArray())
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
                            it.setContent(fileMessage)
                        }
                        it.addFile("courses.csv", ByteArrayInputStream(fileByteArray))
                    })
                }
            }.then()
        }

        commands["course"] = Command { event ->
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

    private fun courseIterableToStringCSV(courses: SizedIterable<Course>): String {
        return courses.map { course ->
            listOf(course.title, course.subject.subjectCode, course.number, course.description, course.prerequisite, course.restrictions, course.corequisites, course.instructorType, course.section, course.crn, course.time, course.instructor, course.credit, course.seatsAvailable, course.sectionComment).map {
                "\"${it.replace("\n", " ")}\""
            }
        }.joinToString("\n") { courseDescription -> courseDescription.joinToString(",") { it } }
    }

    private fun appendHeaderCSV(coursesString: String): String {
        return "Title, Subject Code, Course Number, Description, Prerequisite, Restriction, Corequisites, Instructor type, Section Number, CRN, Time, Instructor, Credit, Seats Available, Section Comments\n$coursesString"
    }

    private fun courseIterableToStringTable(courses: SizedIterable<Course>): String {
        return courses.map { course ->
            listOf(course.subject.subjectCode + course.number, course.crn, course.title, course.time, course.instructor, course.seatsAvailable).map {
                "\"${it.replace("\n", " ")}\""
            }
        }.joinToString("\n") { courseDescription -> courseDescription.joinToString(",") { it } }
    }

    private fun appendHeaderTable(coursesString: String): String {
        return "Course Number,CRN,Title,Days/Time,Instructor,Seats Available\n$coursesString"
    }
}