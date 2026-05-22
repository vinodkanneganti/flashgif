package com.flashgif.media.transcode;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
class RabbitConfig {

    static final String TRANSCODE_EXCHANGE = "media.transcode";
    static final String TRANSCODE_QUEUE    = "media.transcode.requests";
    static final String TRANSCODE_DLQ      = "media.transcode.dlq";
    static final String TRANSCODE_ROUTING  = "transcode";

    @Bean
    DirectExchange transcodeExchange() {
        return new DirectExchange(TRANSCODE_EXCHANGE, true, false);
    }

    @Bean
    Queue transcodeQueue() {
        // Failed deliveries get rerouted via the default exchange to the DLQ name.
        return QueueBuilder.durable(TRANSCODE_QUEUE)
                .withArgument("x-dead-letter-exchange", "")
                .withArgument("x-dead-letter-routing-key", TRANSCODE_DLQ)
                .build();
    }

    @Bean
    Queue transcodeDlq() {
        return QueueBuilder.durable(TRANSCODE_DLQ).build();
    }

    @Bean
    Binding transcodeBinding(Queue transcodeQueue, DirectExchange transcodeExchange) {
        return BindingBuilder.bind(transcodeQueue).to(transcodeExchange).with(TRANSCODE_ROUTING);
    }

    @Bean
    MessageConverter rabbitMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    RabbitTemplate rabbitTemplate(ConnectionFactory cf, MessageConverter mc) {
        RabbitTemplate t = new RabbitTemplate(cf);
        t.setMessageConverter(mc);
        t.setExchange(TRANSCODE_EXCHANGE);
        return t;
    }
}
