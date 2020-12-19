package io.github.bchen290.drexelcoursebot.database.table

import org.jetbrains.exposed.dao.id.IntIdTable

object Courses : IntIdTable() {
    val subject = reference("subject", Subjects)

    val title = text("title")
    val number = text("number")
    val descriptions = text("descriptions")
    val prerequisites = text("prerequisites")
    val restrictions = text("restrictions")
    val corequisites = text("corequisites")

    val instructorType = text("instructorType")
    val instructorMethod = text("instructorMethod")
    val section = text("section")
    val crn = text("crn")
    val time = text("time")
    val instructor = text("instructor")
    val credit = text("credit")
    val seatsAvailable = text("seatsAvailable")
    val sectionComment = text("sectionComment")
}