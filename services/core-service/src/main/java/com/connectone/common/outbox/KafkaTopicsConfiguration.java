package com.connectone.common.outbox;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/** The producer owns its topic. 3 partitions: ordering is per key (customer), parallelism is across customers. */
@Configuration
class KafkaTopicsConfiguration {

    @Bean
    NewTopic domainEventsTopic(@Value("${connectone.events.topic:connectone.events.v1}") String topic) {
        return TopicBuilder.name(topic).partitions(3).replicas(1).build();
    }
}
