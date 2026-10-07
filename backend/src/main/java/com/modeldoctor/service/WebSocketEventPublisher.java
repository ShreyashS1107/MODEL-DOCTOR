package com.modeldoctor.service;

import com.modeldoctor.dto.SystemActivityDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
public class WebSocketEventPublisher {

    private final SimpMessagingTemplate messagingTemplate;

    @Autowired
    public WebSocketEventPublisher(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void broadcastTelemetry(SystemActivityDto activity) {
        messagingTemplate.convertAndSend("/topic/telemetry", activity);
    }

    public void broadcastDiagnosticProgress(String runId, Object progressPayload) {
        messagingTemplate.convertAndSend("/topic/diagnostics/" + runId, progressPayload);
    }
}
