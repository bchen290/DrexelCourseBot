package io.github.bchen290.drexelcoursebot

import io.github.bchen290.drexelcoursebot.utility.commands.Command
import reactor.core.publisher.Mono

class CourseCog(commands: MutableMap<String, Command>) {
    init {
        commands["ping"] = Command { event ->
            Mono.justOrEmpty(event.message.content)
                .map { content -> content.split(" ") }
                .doOnNext { command -> event.message.channel.flatMap { channel -> channel.createMessage(command.joinToString { it }) }.subscribe() }
                .then()
        }
    }
}