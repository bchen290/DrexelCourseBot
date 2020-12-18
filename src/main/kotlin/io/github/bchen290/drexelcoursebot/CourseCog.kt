import discord4j.core.GatewayDiscordClient
import discord4j.core.event.domain.message.MessageCreateEvent
import reactor.core.publisher.Mono

class CourseCog(client: GatewayDiscordClient) {
    init {
        client.on(MessageCreateEvent::class.java)
            .map(MessageCreateEvent::getMessage)
            .filter { it.author.map { user -> !user.isBot }.orElse(false) }
            .filter { it.content.equals("!ping", true) }
            .flatMap { it.channel }
            .flatMap { it.createMessage("Pong!").onErrorResume { Mono.empty() } }
            .subscribe()

    }
}