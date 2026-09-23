package com.smartlife.server.websocket;

import jakarta.websocket.OnClose;
import jakarta.websocket.OnError;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.ServerEndpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket 端点，sid 用 userId：商家连接后接收获单提醒，用户连接接收支付/状态回执。
 * 每个连接一个实例（@ServerEndpoint 的默认行为），所以会话表必须是 static。
 * 一个用户开多个页签时后连的覆盖先连的，只保最新会话。
 */
@Component
@ServerEndpoint("/ws/{sid}")
public class WebSocketServer {

    private static final Logger log = LoggerFactory.getLogger(WebSocketServer.class);

    private static final Map<String, Session> SESSIONS = new ConcurrentHashMap<>();

    @OnOpen
    public void onOpen(Session session) {
        SESSIONS.put(sidOf(session), session);
        log.info("ws 连接建立：{}", sidOf(session));
    }

    @OnClose
    public void onClose(Session session) {
        SESSIONS.remove(sidOf(session));
        log.info("ws 连接关闭：{}", sidOf(session));
    }

    @OnError
    public void onError(Session session, Throwable e) {
        SESSIONS.remove(sidOf(session));
        log.warn("ws 连接异常：{}", sidOf(session), e);
    }

    /** 定向推送。接收方不在线就静默跳过：提醒是尽力而为，不阻塞业务 */
    public static void sendTo(String sid, String message) {
        Session session = SESSIONS.get(sid);
        if (session == null || !session.isOpen()) {
            return;
        }
        // Tomcat 的 Session 非线程安全，串行化发送
        synchronized (session) {
            try {
                session.getBasicRemote().sendText(message);
            } catch (IOException e) {
                log.warn("ws 推送失败：{}", sid, e);
            }
        }
    }

    private static String sidOf(Session session) {
        return session.getPathParameters().get("sid");
    }
}
