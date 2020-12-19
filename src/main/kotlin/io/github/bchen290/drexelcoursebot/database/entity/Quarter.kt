package io.github.bchen290.drexelcoursebot.database.entity

import io.github.bchen290.drexelcoursebot.database.table.Quarters
import io.github.bchen290.drexelcoursebot.database.table.Schools
import io.github.bchen290.drexelcoursebot.database.table.Subjects
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID

class Quarter(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<Quarter>(Quarters)

    var name by Quarters.name

    val schools by School referrersOn Schools.quarter
}