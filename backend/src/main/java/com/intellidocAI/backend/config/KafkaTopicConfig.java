package com.intellidocAI.backend.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {
    // This constant will be shared between producer and consumer
    public static final String REPO_PROCESSING_TOPIC = "repo.processing.topic";

    @Bean
    public NewTopic repoProcessingTopic() {
        return TopicBuilder.name(REPO_PROCESSING_TOPIC)
                .partitions(3)  // 3 partitions for parallel processing
                .replicas(3)    // 3 replicas is the standard for Confluent Cloud
                .build();
    }
}
