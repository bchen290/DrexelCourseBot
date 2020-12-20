package io.github.bchen290.drexelcoursebot.utility
import io.github.bchen290.drexelcoursebot.database.entity.Course
import io.github.bchen290.drexelcoursebot.database.entity.Quarter
import io.github.bchen290.drexelcoursebot.database.entity.School
import io.github.bchen290.drexelcoursebot.database.entity.Subject
import io.github.bchen290.drexelcoursebot.database.table.Courses
import io.github.bchen290.drexelcoursebot.database.table.Quarters
import io.github.bchen290.drexelcoursebot.database.table.Schools
import io.github.bchen290.drexelcoursebot.database.table.Subjects
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import kotlin.system.exitProcess

class TermMasterScraper(var shouldPrint: Boolean = false, var useCoroutine: Boolean = false){
    private val BASE_URL = "https://termmasterschedule.drexel.edu"
    private val HOMEPAGE_URL = "/webtms_du/app"
    private var DELAY = 100L

    init {
        if (useCoroutine) {
            DELAY = 500L
        }

        transaction {
            SchemaUtils.drop(Courses, Subjects, Schools, Quarters)
            SchemaUtils.create(Courses, Subjects, Schools, Quarters)

            scrapeTMS()
        }
    }

    private fun scrapeTMS() {
        val homepageDocument = Jsoup.connect(BASE_URL + HOMEPAGE_URL).timeout(0).get()
        val term = homepageDocument.getElementsByClass("term").take(4)
        term.forEach { termElement ->
            if (useCoroutine) {
                GlobalScope.launch {
                    newSuspendedTransaction(Dispatchers.Default) {
                        scrapeQuarter(termElement)
                    }
                }
            } else {
                scrapeQuarter(termElement)
            }
        }
    }

    private fun scrapeQuarter(termElement: Element) {
        val element = termElement.child(1)

        val currentQuarter = Quarter.new {
            name = element.text().convertBlankStringToNA()
        }

        if (shouldPrint) {
            println("Scraping ${element.text()}")
        }

        val quarterDocument = Jsoup.connect(BASE_URL + element.attr("href")).timeout(0).get()

        quarterDocument.getElementById("sideLeft").select("a").forEach { schoolLink ->
            scrapeSchool(schoolLink, currentQuarter)
        }
    }

    private fun scrapeSchool(schoolLink: Element, currentQuarter: Quarter) {
        val currentSchool = School.new {
            quarter = currentQuarter
            name = schoolLink.text().convertBlankStringToNA()
        }

        if (shouldPrint) {
            println("\tScraping ${schoolLink.text()}")
        }

        val subjectsDocument = Jsoup.connect(BASE_URL + schoolLink.attr("href")).timeout(0).get()

        val collegePanel = subjectsDocument.getElementsByClass("collegePanel")[0]
        val subjects = collegePanel.select("div a")

        subjects.forEach { subjectLink ->
            scrapeSubject(subjectLink, currentSchool)
        }
    }

    private fun scrapeSubject(subjectLink: Element, currentSchool: School) {
        val subjectSplit = subjectLink.text().trim().split("""\s(?=\()""".toRegex())
        val currentSubjectName = subjectSplit[0]
        val currentSubjectCode = subjectSplit[1].removeSurrounding("(", ")")

        val currentSubject = Subject.new {
            school = currentSchool
            name = currentSubjectName.convertBlankStringToNA()
            subjectCode = currentSubjectCode.convertBlankStringToNA()
        }

        if (shouldPrint) {
            println("\t\tScraping $currentSubjectName")
        }

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

            if(shouldPrint) {
                println("\t\t\tScraping $courseTitle")
            }

            Course.new {
                subject = currentSubject

                title = courseTitle.convertBlankStringToNA()
                number = tableHeaders.first { it.text()!!.contentEquals("Course Number") }.lastElementSibling().text().convertBlankStringToNA()
                description = courseDocument.getElementsByClass("courseDesc").first().text().convertBlankStringToNA()
                prerequisite = courseDocument.getElementsByClass("subpoint").first { it.text().contains("Pre-Requisites:") }.select("span").joinToString(separator = " ") { it.text() }.convertBlankStringToNA()
                restrictions = courseDocument.select(".subpoint1,.subpoint2").filter { it.text().isNotEmpty() }.joinToString(separator = " ") { it.text() + "\n" }.convertBlankStringToNA()
                corequisites = courseDocument.getElementsByClass("subpoint").first { it.text().contains("Co-Requisites:") }.select("span").joinToString(separator = " ") { it.text() }.convertBlankStringToNA()

                instructorType = tableHeaders.first { it.text()!!.contentEquals("Instruction Type") }.lastElementSibling().text().convertBlankStringToNA()
                instructorMethod = tableHeaders.first { it.text()!!.contentEquals("Instruction Method") }.lastElementSibling().text().convertBlankStringToNA()
                section = tableHeaders.first { it.text()!!.contentEquals("Section") }.lastElementSibling().text().convertBlankStringToNA()
                crn = tableHeaders.first { it.text()!!.contentEquals("CRN") }.lastElementSibling().text().convertBlankStringToNA()
                time = rows.child(daysTimeIndex).selectFirst("tr").children().joinToString(separator = " ") { it.text() }.convertBlankStringToNA()
                instructor = tableHeaders.first { it.text()!!.contentEquals("Instructor(s)") }.lastElementSibling().text().convertBlankStringToNA()
                credit = tableHeaders.first { it.text()!!.contentEquals("Credits") }.lastElementSibling().text().convertBlankStringToNA()
                seatsAvailable = (maxEnroll - enroll).toString().convertBlankStringToNA()
                sectionComment = tableHeaders.first { it.text()!!.contentEquals("Section Comments") }.lastElementSibling().text().convertBlankStringToNA()

                url = courseURL.convertBlankStringToNA()
            }

            Thread.sleep(DELAY)
        }
    }

    private fun String.convertBlankStringToNA(): String {
        return if(this.isBlank()) "N/A" else this
    }
}