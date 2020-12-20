package io.github.bchen290.drexelcoursebot.database

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.StdOutSqlLogger
import org.jetbrains.exposed.sql.addLogger
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.*

object DatabaseHelper {
    fun setupDB(prop: Properties) {
        val dbURL = "jdbc:postgresql://localhost:5432/drexel"
        val dbUser = prop.getProperty("dbUser")
        val dbPassword = prop.getProperty("dbPassword")

        val db = Database.connect(dbURL, driver = "org.postgresql.Driver", user = dbUser, password = dbPassword)
        db.useNestedTransactions = true

        transaction {
            addLogger(StdOutSqlLogger)
        }
    }
}