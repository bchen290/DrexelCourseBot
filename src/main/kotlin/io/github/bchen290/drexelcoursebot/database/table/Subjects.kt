package io.github.bchen290.drexelcoursebot.database.table

import org.jetbrains.exposed.dao.id.IntIdTable

object Subjects : IntIdTable() {
    val school = reference("school", Schools)

    val name = text("name")
    val code = text("code")
}