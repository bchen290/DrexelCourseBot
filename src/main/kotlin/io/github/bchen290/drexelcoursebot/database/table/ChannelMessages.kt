package io.github.bchen290.drexelcoursebot.database.table

import org.jetbrains.exposed.dao.id.IntIdTable

object ChannelMessages : IntIdTable() {
    val channelID = text("channelID")
    val messageID = text("messageID")
    val offset = integer("offset")
}