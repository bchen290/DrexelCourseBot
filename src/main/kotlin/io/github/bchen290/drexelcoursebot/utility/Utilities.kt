package io.github.bchen290.drexelcoursebot.utility

import discord4j.core.`object`.entity.User
import io.github.bchen290.drexelcoursebot.database.entity.Course
import org.jetbrains.exposed.sql.SizedIterable

class Utilities {
    companion object {
        fun courseIterableToStringCSV(courses: List<Course>): String {
            return courses.map { course ->
                listOf(course.title, course.subject.subjectCode, course.number, course.description, course.prerequisite, course.restrictions, course.corequisites, course.instructorType, course.section, course.crn, course.time, course.instructor, course.credit, course.seatsAvailable, course.sectionComment).map {
                    "\"${it.replace("\n", " ")}\""
                }
            }.joinToString("\n") { courseDescription -> courseDescription.joinToString(",") { it } }
        }

        fun appendHeaderCSV(coursesString: String): String {
            return "Title, Subject Code, Course Number, Description, Prerequisite, Restriction, Corequisites, Instructor type, Section Number, CRN, Time, Instructor, Credit, Seats Available, Section Comments\n$coursesString"
        }

        fun courseIterableToStringTable(courses: SizedIterable<Course>): String {
            return courses.map { course ->
                listOf(course.subject.subjectCode + course.number, course.crn, course.title, course.time, course.instructor, course.seatsAvailable).map {
                    "\"${it.replace("\n", " ")}\""
                }
            }.joinToString("\n") { courseDescription -> courseDescription.joinToString(",") { it } }
        }

        fun appendHeaderTable(coursesString: String): String {
            return "Course Number,CRN,Title,Days/Time,Instructor,Seats Available\n$coursesString"
        }
    }
}

fun User.isNotBot(): Boolean {
    return !this.isBot
}