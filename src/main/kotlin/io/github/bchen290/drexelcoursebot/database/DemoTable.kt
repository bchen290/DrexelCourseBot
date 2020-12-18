package io.github.bchen290.drexelcoursebot.database

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

object DemoTable : Table() {
    val id = integer("id").autoIncrement()
    val name = varchar("name", 50)

    fun getAll(): List<Any> = transaction {
        DemoTable.selectAll().map { it[name] }
    }
}