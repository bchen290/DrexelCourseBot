package io.github.bchen290.drexelcoursebot.database.entity

import io.github.bchen290.drexelcoursebot.database.table.ChannelMessages
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID

class ChannelMessage(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<ChannelMessage>(ChannelMessages)

    var channelID by ChannelMessages.channelID
    var messageID by ChannelMessages.messageID
    var offset by ChannelMessages.offset
}