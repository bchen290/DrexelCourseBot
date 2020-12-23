package io.github.bchen290.drexelcoursebot.utility

import java.lang.Integer.max

class TableCreator {
    companion object {
        fun csvToTable(csv: String): String {
            val lines = csv.split("\n").filter { it.isNotBlank() }.map { it.trim() }
            val words = lines.map { line -> line.split(""",(?=([^"]*"[^"]*")*[^"]*${'$'})""".toRegex()) }.filter { it.isNotEmpty() }

            val maxWordLength = IntArray(words[0].size)
            words.forEach { row ->
                row.forEachIndexed { index, _ ->
                    maxWordLength[index] = max(maxWordLength[index], row[index].length)
                }
            }

            val formatBuilder = StringBuilder()
            maxWordLength.forEach {
                formatBuilder.append("%-${it + 2}s")
            }
            val format = formatBuilder.toString()

            val resultBuilder = StringBuilder()
            resultBuilder.append("```")

            words.forEach {
                if (it.isNotEmpty()) {
                    resultBuilder.append(String.format(format, *it.map { str -> str.removeSurrounding("\"") }.toTypedArray())).append("\n")
                }
            }
            resultBuilder.append("```")

            return resultBuilder.toString()
        }
    }
}