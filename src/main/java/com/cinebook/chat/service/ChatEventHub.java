package com.cinebook.chat.service;

import com.cinebook.chat.dto.ChatDtos.ChatEvent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Single-instance push channel. Persisted REST history is the recovery source after reconnects. */
@Component
@ConditionalOnProperty(prefix = "cinebook.chat", name = "enabled", havingValue = "true")
public class ChatEventHub {
    private final ConcurrentHashMap<Integer, Set<SseEmitter>> streams = new ConcurrentHashMap<>();

    public SseEmitter subscribe(int userId) {
        SseEmitter emitter = new SseEmitter(0L);
        Set<SseEmitter> userStreams = streams.computeIfAbsent(userId, ignored -> ConcurrentHashMap.newKeySet());
        userStreams.add(emitter);
        Runnable remove = () -> remove(userId, emitter);
        emitter.onCompletion(remove);
        emitter.onTimeout(remove);
        emitter.onError(error -> remove.run());
        try { emitter.send(SseEmitter.event().name("connected").data("ready")); }
        catch (IOException error) { emitter.completeWithError(error); remove.run(); }
        return emitter;
    }

    public void send(int userId, ChatEvent event) {
        Set<SseEmitter> userStreams = streams.get(userId);
        if (userStreams == null) return;
        for (SseEmitter emitter : userStreams) {
            try { emitter.send(SseEmitter.event().id(event.eventId()).name(event.type()).data(event)); }
            catch (IOException | IllegalStateException error) { emitter.complete(); remove(userId, emitter); }
        }
    }

    private void remove(int userId, SseEmitter emitter) {
        Set<SseEmitter> userStreams = streams.get(userId);
        if (userStreams != null) {
            userStreams.remove(emitter);
            if (userStreams.isEmpty()) streams.remove(userId, userStreams);
        }
    }
}
