package io.github.bchen290.drexelcoursebot.database.entity

import io.github.bchen290.drexelcoursebot.database.table.Schools
import io.github.bchen290.drexelcoursebot.database.table.Subjects
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID

class School(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<School>(Schools)

    var name by Schools.name

    var quarter by Quarter referencedOn Schools.quarter
    val subjects by Subject referrersOn Subjects.school
}