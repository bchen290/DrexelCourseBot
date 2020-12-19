package io.github.bchen290.drexelcoursebot.database.table

import org.jetbrains.exposed.dao.id.IntIdTable

object Schools: IntIdTable() {
    val quarter = reference("quarter", Quarters)

    val name = text("name")
}