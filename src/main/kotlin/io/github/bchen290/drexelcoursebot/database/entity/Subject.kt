package io.github.bchen290.drexelcoursebot.database.entity

import io.github.bchen290.drexelcoursebot.database.table.Courses
import io.github.bchen290.drexelcoursebot.database.table.Subjects
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID

class Subject(id: EntityID<Int>):  IntEntity(id) {
    companion object : IntEntityClass<Subject>(Subjects)

    var name by Subjects.subjectName
    var subjectCode by Subjects.subjectCode

    val courses by Course referrersOn Courses.subject
}