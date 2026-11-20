package com.powertech.stock.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class NotificationService {

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    private static final String NOTIFICATION_TOPIC = "/topic/notifications";

    public void envoyerNotification(String message, String type) {
        Map<String, Object> notification = new HashMap<>();
        notification.put("message", message);
        notification.put("type", type);
        notification.put("timestamp", System.currentTimeMillis());

        messagingTemplate.convertAndSend(NOTIFICATION_TOPIC, notification);
    }

    public void envoyerNotification(String message, String type, String details) {
        Map<String, Object> notification = new HashMap<>();
        notification.put("message", message);
        notification.put("type", type);
        notification.put("details", details);
        notification.put("timestamp", System.currentTimeMillis());

        messagingTemplate.convertAndSend(NOTIFICATION_TOPIC, notification);
    }

    public void envoyerNotification(String message, String type, String details, String lien) {
        Map<String, Object> notification = new HashMap<>();
        notification.put("message", message);
        notification.put("type", type);
        notification.put("details", details);
        notification.put("lien", lien);
        notification.put("timestamp", System.currentTimeMillis());

        messagingTemplate.convertAndSend(NOTIFICATION_TOPIC, notification);
    }
}