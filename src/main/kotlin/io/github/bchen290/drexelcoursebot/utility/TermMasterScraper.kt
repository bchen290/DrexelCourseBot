package io.github.bchen290.drexelcoursebot.utility
import io.github.bchen290.drexelcoursebot.database.entity.Course
import io.github.bchen290.drexelcoursebot.database.entity.Quarter
import io.github.bchen290.drexelcoursebot.database.entity.School
import io.github.bchen290.drexelcoursebot.database.entity.Subject
import io.github.bchen290.drexelcoursebot.database.table.Courses
import io.github.bchen290.drexelcoursebot.database.table.Quarters
import io.github.bchen290.drexelcoursebot.database.table.Schools
import io.github.bchen290.drexelcoursebot.database.table.Subjects
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import org.jsoup.Jsoup

object TermMasterScraper {
    private const val BASE_URL = "https://termmasterschedule.drexel.edu"
    private const val HOMEPAGE_URL = "/webtms_du/app"

    init {
        transaction {
            SchemaUtils.drop(Courses, Subjects, Schools, Quarters)
            SchemaUtils.create(Courses, Subjects, Schools, Quarters)

            val homepageDocument = Jsoup.connect(BASE_URL + HOMEPAGE_URL).timeout(0).get()
            val term = homepageDocument.getElementsByClass("term").take(4)
            term.forEach { termElement ->
                val element = termElement.child(1)

                val currentQuarter = Quarter.new {
                    name = element.text()
                }

                println("Scraping ${element.text()}")

                val quarterDocument = Jsoup.connect(BASE_URL + element.attr("href")).timeout(0).get()

                quarterDocument.getElementById("sideLeft").select("a").forEach { schoolLink ->
                    val currentSchool = School.new {
                        quarter = currentQuarter
                        name = schoolLink.text()
                    }

                    println("\tScraping ${schoolLink.text()}")

                    val subjectsDocument = Jsoup.connect(BASE_URL + schoolLink.attr("href")).timeout(0).get()

                    val collegePanel = subjectsDocument.getElementsByClass("collegePanel")[0]
                    val subjects = collegePanel.select("div a")

                    subjects.forEach { subjectLink ->
                        val subjectSplit = subjectLink.text().trim().split("""\s(?=\()""".toRegex())
                        val currentSubjectName = subjectSplit[0]
                        val currentSubjectCode = subjectSplit[1].removeSurrounding("(", ")")

                        val currentSubject = Subject.new {
                            school = currentSchool
                            name = currentSubjectName
                            subjectCode = currentSubjectCode
                        }

                        println("\t\tScraping $currentSubjectName")

                        val subjectDocument = Jsoup.connect(BASE_URL + subjectLink.attr("href")).timeout(0).get()
                        val crnIndex = subjectDocument.select(".tableHeader td").indexOfFirst { it.text()!!.contentEquals("CRN") }
                        val daysTimeIndex = subjectDocument.select(".tableHeader td").indexOfFirst { it.text()!!.startsWith("Days") }

                        val courseRows = subjectDocument.getElementsByClass("tableHeader").first().siblingElements().dropLast(1)

                        courseRows.forEach { rows ->
                            val courseURL = BASE_URL + rows.child(crnIndex).selectFirst("a").attr("href")
                            val courseDocument = Jsoup.connect(courseURL).timeout(0).get()

                            val tableHeaders = courseDocument.getElementsByClass("tableHeader")

                            val maxEnroll = tableHeaders.first { it.text()!!.contentEquals("Max Enroll") }.lastElementSibling().text().toIntOrNull() ?: 0
                            val enroll = tableHeaders.first { it.text()!!.contentEquals("Enroll") }.lastElementSibling().text().toIntOrNull() ?: maxEnroll

                            val courseTitle = tableHeaders.first { it.text()!!.contentEquals("Title") }.lastElementSibling().text()
                            println("\t\t\tScraping $courseTitle")

                            Course.new {
                                subject = currentSubject

                                title = courseTitle
                                number = tableHeaders.first { it.text()!!.contentEquals("Course Number") }.lastElementSibling().text()
                                description = courseDocument.getElementsByClass("courseDesc").first().text()
                                prerequisite = courseDocument.getElementsByClass("subpoint").first { it.text().contains("Pre-Requisites:") }.select("span").joinToString(separator = " ") { it.text() }
                                restrictions = courseDocument.select(".subpoint1,.subpoint2").filter { it.text().isNotEmpty() }.joinToString(separator = " ") { it.text() + "\n" }
                                corequisites = courseDocument.getElementsByClass("subpoint").first { it.text().contains("Co-Requisites:") }.select("span").joinToString(separator = " ") { it.text() }

                                instructorType = tableHeaders.first { it.text()!!.contentEquals("Instruction Type") }.lastElementSibling().text()
                                instructorMethod = tableHeaders.first { it.text()!!.contentEquals("Instruction Method") }.lastElementSibling().text()
                                section = tableHeaders.first { it.text()!!.contentEquals("Section") }.lastElementSibling().text()
                                crn = tableHeaders.first { it.text()!!.contentEquals("CRN") }.lastElementSibling().text()
                                time = rows.child(daysTimeIndex).selectFirst("tr").children().joinToString(separator = " ") { it.text() }
                                instructor = tableHeaders.first { it.text()!!.contentEquals("Instructor(s)") }.lastElementSibling().text()
                                credit = tableHeaders.first { it.text()!!.contentEquals("Credits") }.lastElementSibling().text()
                                seatsAvailable = (maxEnroll - enroll).toString()
                                sectionComment = tableHeaders.first { it.text()!!.contentEquals("Section Comments") }.lastElementSibling().text()

                                url = courseURL
                            }

                            Thread.sleep(100)
                        }
                    }
                }
            }
        }
    }
}