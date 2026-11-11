package com.connectone.notification.messaging;

import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

import tools.jackson.core.JacksonException;

/**
 * Failure handling for the consumer: a failing record is retried 3 times with exponential back-off (1s, 2s, 4s) and then
 * published to {@code <topic>.DLT} so the partition is not blocked. Malformed payloads can never succeed, so they skip the retries.
 * Operators inspect/replay the DLT (docs/DEBUGGING_PLAYBOOK.md).
 */
@Configuration
public class KafkaConsumerConfiguration {

    @Bean
    DefaultErrorHandler kafkaErrorHandler(KafkaOperations<Object, Object> template) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(template,
                (record, ex) -> new TopicPartition(record.topic() + ".DLT", -1)); // -1: let the producer choose the partition
        ExponentialBackOff backOff = new ExponentialBackOff(1000L, 2.0);
        backOff.setMaxAttempts(3);
        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backOff);
        handler.addNotRetryableExceptions(IllegalArgumentException.class, JacksonException.class);
        return handler;
    }
}
