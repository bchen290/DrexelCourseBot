package io.github.bchen290.drexelcoursebot.database

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

            SchemaUtils.drop(DemoTable)
            SchemaUtils.create(DemoTable)
        }
    }
}