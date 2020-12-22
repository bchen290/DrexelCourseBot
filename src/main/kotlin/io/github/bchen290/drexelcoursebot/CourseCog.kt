package io.github.bchen290.drexelcoursebot

import discord4j.core.spec.MessageCreateSpec
import io.github.bchen290.drexelcoursebot.database.entity.Course
import io.github.bchen290.drexelcoursebot.database.table.Courses
import io.github.bchen290.drexelcoursebot.database.table.Subjects
import io.github.bchen290.drexelcoursebot.utility.commands.Command
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import reactor.core.publisher.Mono
import java.io.ByteArrayInputStream
import java.io.FileInputStream
import java.io.InputStream
import kotlin.io.path.createTempFile

class CourseCog(commands: MutableMap<String, Command>) {
    init {
        commands["ping"] = Command { event ->
            event.message.channel.flatMap {
                it.createMessage("Pong!")
            }.then()
        }

        commands["courses"] = Command { event ->
            val content = event.message.content.split(" ")
            val messageToSend = when(content.size) {
                1 -> {
                    transaction {
                        val courses = Course.all().map { course ->
                            listOf(course.title, course.subject.subjectCode, course.number, course.description, course.prerequisite, course.restrictions, course.corequisites, course.instructorType, course.section, course.crn, course.time, course.instructor, course.credit, course.seatsAvailable, course.sectionComment).map {
                                "\"${it.replace("\n", " ")}\""
                            }
                        }.joinToString("\n") { courseDescription -> courseDescription.joinToString(",") { it } }

                        "Title, Subject Code, Course Number, Description, Prerequisite, Restriction, Corequisites, Instructor type, Section Number, CRN, Time, Instructor, Credit, Seats Available, Section Comments\n$courses"
                    }
                }
                else -> {
                    "Invalid usage of command"
                }
            }

            event.message.channel.flatMap { channel ->
                channel.createMessage {
                    it.addFile("courses.csv", ByteArrayInputStream(messageToSend.toByteArray(Charsets.UTF_8)))
                }
            }.then()
        }

        commands["course"] = Command { event ->
            val content = event.message.content

            // Add space in between subject and course number
            val courseTitle = content.replace("""([a-zA-Z](?=\d))""".toRegex(), "$1 ").split(" ")

            val subjectCode = courseTitle[1].toUpperCase()
            val courseNumber = courseTitle[2]

            val course = transaction {
                val result = (Courses innerJoin Subjects).select { (Courses.subject eq Subjects.id).and(Subjects.code eq subjectCode).and(Courses.number eq courseNumber) }
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
        }
    }
}