package com.flashgif.media.transcode;

import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TranscodeDispatcher {

    private final RabbitTemplate rabbit;

    public void dispatch(TranscodeMessage message) {
        rabbit.convertAndSend(RabbitConfig.TRANSCODE_EXCHANGE, RabbitConfig.TRANSCODE_ROUTING, message);
    }
}
