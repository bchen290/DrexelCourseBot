package io.github.bchen290.drexelcoursebot.utility.states

enum class FilterOptions(val str: String) {
    SUBJECT("Subject Code & Course #"), CRN("CRN #"), PROFESSOR("Professor"), CREDITS("# of Credits"), PREREQUISITES("No Prerequisites"), QUIT("Quit")
}