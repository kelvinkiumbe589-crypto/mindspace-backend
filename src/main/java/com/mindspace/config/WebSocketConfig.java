package com.mindspace.config;

import com.mindspace.ws.SessionSignalingHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final SessionSignalingHandler signalingHandler;

    @Value("${app.frontend.url:*}")
    private String frontendUrl;

    public WebSocketConfig(SessionSignalingHandler signalingHandler) {
        this.signalingHandler = signalingHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // Auth is done inside the handler (JWT in the query string + booking membership),
        // so allow the browser origins to connect. Adjust origins as needed.
        registry.addHandler(signalingHandler, "/ws/session")
                .setAllowedOriginPatterns("*");
    }
}
