package io.github.bchen290.drexelcoursebot.database.table

import org.jetbrains.exposed.dao.id.IntIdTable

object Subjects : IntIdTable() {
    val subjectName = text("subjectName")
    val subjectCode = text("subjectCode")
}