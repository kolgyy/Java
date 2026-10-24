package ru.bibank.common.events;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;

/**
 * Топики создаются приложением (идемпотентно), а не автосозданием брокера,
 * чтобы число партиций было определено явно.
 */
@Configuration
public class KafkaTopicsConfig {

    private static final int PARTITIONS = 3;

    @Bean
    public KafkaAdmin.NewTopics bibankTopics() {
        return new KafkaAdmin.NewTopics(
                topic(Topics.USER_EVENTS), topic(Topics.USER_EVENTS + Topics.DLT_SUFFIX),
                topic(Topics.ACCOUNT_EVENTS), topic(Topics.ACCOUNT_EVENTS + Topics.DLT_SUFFIX),
                topic(Topics.PAYMENT_EVENTS), topic(Topics.PAYMENT_EVENTS + Topics.DLT_SUFFIX),
                topic(Topics.LOAN_EVENTS), topic(Topics.LOAN_EVENTS + Topics.DLT_SUFFIX));
    }

    private static NewTopic topic(String name) {
        return TopicBuilder.name(name).partitions(PARTITIONS).replicas(1).build();
    }
}
