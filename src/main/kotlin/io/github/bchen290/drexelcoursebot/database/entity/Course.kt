package io.github.bchen290.drexelcoursebot.database.entity

import io.github.bchen290.drexelcoursebot.database.table.Courses
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID

class Course(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<Course>(Courses)

    var subject by Subject referencedOn Courses.subject

    var title by Courses.title
    var number by Courses.number
    var description by Courses.descriptions
    var prerequisite by Courses.prerequisites
    var restrictions by Courses.restrictions
    var corequisites by Courses.corequisites

    var instructorType by Courses.instructorType
    var instructorMethod by Courses.instructorMethod
    var section by Courses.section
    var crn by Courses.crn
    var time by Courses.time
    var instructor by Courses.instructor
    var credit by Courses.credit
    var seatsAvailable by Courses.seatsAvailable
    var sectionComment by Courses.sectionComment
}