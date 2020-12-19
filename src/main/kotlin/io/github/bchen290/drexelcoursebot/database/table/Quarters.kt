package io.github.bchen290.drexelcoursebot.database.table

import org.jetbrains.exposed.dao.id.IntIdTable

object Quarters : IntIdTable() {
    val name = text("name")
}