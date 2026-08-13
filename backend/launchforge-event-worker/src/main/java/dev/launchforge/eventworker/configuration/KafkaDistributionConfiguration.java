package dev.launchforge.eventworker.configuration;

import dev.launchforge.eventworker.observability.DistributionMetrics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

@Configuration
public class KafkaDistributionConfiguration {
  @Bean
  NewTopic configRevisionTopic(DistributionProperties properties) {
    return TopicBuilder.name(properties.topic())
        .partitions(properties.topicPartitions())
        .replicas(properties.topicReplicas())
        .build();
  }

  @Bean
  @ConditionalOnProperty(name = "launchforge.analytics.worker.enabled", havingValue = "true")
  NewTopic analyticsEvaluationTopic(AnalyticsWorkerProperties properties) {
    return TopicBuilder.name(properties.topic())
        .partitions(properties.topicPartitions())
        .replicas(properties.topicReplicas())
        .build();
  }

  @Bean
  DefaultErrorHandler kafkaErrorHandler(DistributionMetrics metrics) {
    ExponentialBackOff backOff = new ExponentialBackOff(500L, 2.0);
    backOff.setMaxInterval(5000L);
    backOff.setMaxElapsedTime(30_000L);
    DefaultErrorHandler handler =
        new DefaultErrorHandler(
            (record, exception) -> {
              metrics.projectionError();
              throw new IllegalStateException("Kafka projection retries exhausted", exception);
            },
            backOff);
    handler.setAckAfterHandle(false);
    handler.setCommitRecovered(false);
    return handler;
  }
}
