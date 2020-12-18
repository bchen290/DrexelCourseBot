package io.github.bchen290.drexelcoursebot.database

import io.github.bchen290.drexelcoursebot.database.entity.Course
import io.github.bchen290.drexelcoursebot.database.entity.Subject
import io.github.bchen290.drexelcoursebot.database.table.Courses
import io.github.bchen290.drexelcoursebot.database.table.Subjects
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.StdOutSqlLogger
import org.jetbrains.exposed.sql.addLogger
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.FileInputStream
import java.util.*

object DatabaseHelper {
    init {
        val prop = Properties()
        prop.load(FileInputStream("env.properties"))

        val dbURL = "jdbc:postgresql://localhost:5432/drexel"
        val dbUser = prop.getProperty("dbUser")
        val dbPassword = prop.getProperty("dbPassword")

        Database.connect(dbURL, driver = "org.postgresql.Driver", user = dbUser, password = dbPassword)

        transaction {
            addLogger(StdOutSqlLogger)

            SchemaUtils.drop(Courses)
            SchemaUtils.drop(Subjects)

            SchemaUtils.create(Courses)
            SchemaUtils.create(Subjects)

            val animationSubject = Subject.new {
                name = "Animation"
                subjectCode = "ANIM"
            }

            Course.new {
                subject = animationSubject
                title = "ANIM 100 - Foundational Tools for Animation & VFX"
                description = "Students will learn fundamentals of core tools in Digital Animation & Visual Effects related disciplines. Tools introduced include pixel based image manipulation tools (such as Photoshop), vector based graphics tools (such as Illustrator), video and animation compositing tools (such as After Effects and Nuke) and 3D CGI tools (such as Maya). Animation and visual effects related applications introduced include digital image alteration, digital matte painting, three dimensional type creation, and other foundational animation and visual effects tasks."
                prerequisite = ""
                restrictions = "Must be enrolled in one of the following Program Level(s):\n" +
                        "- Undergraduate Quarter\n" +
                        "Must be enrolled in one of the following Major(s):\n" +
                        "- Animation & Visual Effects"
                corequisites = ""

                instructorType = "Lecture"
                instructorMethod = "Remote Synchronous"
                section = "001"
                crn = "12172"
                time = "T\t12:30 pm - 03:20 pm\n" +
                        "Dec 08, 2020\tFinal Exam:\n" +
                        "08:00 am - 10:00 am"
                instructor = "Evan C James"
                credit = "3.00"
                seatsAvailable = "0"
                sectionComment = "Section held synchronously remote"
            }

            println("Subjects: ${Subject.all().joinToString { it.name }}")
            println("Courses: ${Course.all().joinToString { it.title }}")
            println("${animationSubject.courses.map { it.title } }")
        }
    }
}