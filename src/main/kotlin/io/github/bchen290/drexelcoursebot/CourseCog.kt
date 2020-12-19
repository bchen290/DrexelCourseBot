package io.github.bchen290.drexelcoursebot

import io.github.bchen290.drexelcoursebot.utility.commands.Command
import reactor.core.publisher.Mono

class CourseCog(commands: MutableMap<String, Command>) {
    init {
        commands["ping"] = Command { event ->
            Mono.justOrEmpty(event.message)
                .map { Pair(it.content, it.channel) }
                .doOnNext { it.second.flatMap { channel -> channel.createMessage("Pong") }.subscribe() }
                .then()
        }

        commands["course"] = Command { event ->
            Mono.justOrEmpty(event.message)
                .map { Pair(it.content.split(" "), it.channel) }
                .doOnNext { pair ->
                    val (content, channel) = pair
                    channel.flatMap { it.createMessage(content.joinToString { it }) }.subscribe()
                }
                .then()
        }
    }
}