package dev.launchforge.configedge.configuration;

import dev.launchforge.configedge.stream.RevisionSignalBus;
import java.nio.charset.StandardCharsets;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

@Configuration
@ConditionalOnProperty(
    prefix = "launchforge.config-edge",
    name = "redis-enabled",
    havingValue = "true",
    matchIfMissing = true)
public class RedisInvalidationConfiguration {
  @Bean
  RedisMessageListenerContainer redisMessageListenerContainer(
      RedisConnectionFactory connectionFactory,
      RevisionSignalBus signalBus,
      ConfigEdgeProperties properties) {
    RedisMessageListenerContainer container = new RedisMessageListenerContainer();
    container.setConnectionFactory(connectionFactory);
    container.setRecoveryInterval(2000L);
    MessageListener listener =
        (message, pattern) ->
            signalBus.accept(new String(message.getBody(), StandardCharsets.UTF_8));
    container.addMessageListener(listener, new ChannelTopic(properties.invalidationChannel()));
    return container;
  }
}
