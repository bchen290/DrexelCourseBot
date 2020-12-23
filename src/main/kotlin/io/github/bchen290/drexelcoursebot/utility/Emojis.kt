package io.github.bchen290.drexelcoursebot.utility

import discord4j.core.`object`.reaction.ReactionEmoji

class Emojis {
    companion object {
        val emojijMap = mutableMapOf<String, ReactionEmoji.Unicode>().apply {
            this[":arrow_up:"] = ReactionEmoji.unicode("⬆️")
            this[":arrow_double_up:"] = ReactionEmoji.unicode("⏫")
            this[":arrow_down:"] = ReactionEmoji.unicode("⬇️")
            this[":arrow_double_down:"] = ReactionEmoji.unicode("⏬")
        }
    }
}