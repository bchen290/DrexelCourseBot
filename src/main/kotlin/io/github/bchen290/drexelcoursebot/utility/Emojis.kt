package io.github.bchen290.drexelcoursebot.utility

import discord4j.core.`object`.reaction.ReactionEmoji

enum class Emojis(val reactionEmoji: ReactionEmoji) {
    ARROW_DOUBLE_UP(ReactionEmoji.unicode("⏫")),
    ARROW_UP(ReactionEmoji.unicode("⬆️")),
    ARROW_DOWN(ReactionEmoji.unicode("⬇️")),
    ARROW_DOUBLE_DOWN(ReactionEmoji.unicode("⏬"))
}