/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package com.ditrix.edt.mcp.server.progress;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Tracks session-bound SSE streams for server-originated notifications.
 */
public class SseSessionRegistry
{
    private final Map<String, SessionStream> sessions = new ConcurrentHashMap<>();
    private final AtomicLong eventIds = new AtomicLong();

    public SessionStream registerSession(String sessionId, OutputStream outputStream)
    {
        Objects.requireNonNull(sessionId);
        SessionStream stream = new SessionStream(outputStream);
        SessionStream previous = sessions.put(sessionId, stream);
        if (previous != null)
        {
            previous.markClosed();
        }
        return stream;
    }

    public SessionStream createDetachedStream(OutputStream outputStream)
    {
        return new SessionStream(outputStream);
    }

    public void unregisterSession(String sessionId, SessionStream stream)
    {
        if (sessionId == null || stream == null)
        {
            return;
        }
        sessions.remove(sessionId, stream);
        stream.markClosed();
    }

    public void removeSession(String sessionId)
    {
        if (sessionId == null)
        {
            return;
        }
        SessionStream stream = sessions.remove(sessionId);
        if (stream != null)
        {
            stream.markClosed();
        }
    }

    public boolean sendEvent(String sessionId, String eventName, String dataJson)
    {
        if (sessionId == null)
        {
            return false;
        }
        SessionStream stream = sessions.get(sessionId);
        if (stream == null)
        {
            return false;
        }
        boolean sent = stream.writeEvent(eventIds.incrementAndGet(), eventName, dataJson);
        if (!sent)
        {
            sessions.remove(sessionId, stream);
        }
        return sent;
    }

    public void clear()
    {
        for (Map.Entry<String, SessionStream> entry : sessions.entrySet())
        {
            entry.getValue().markClosed();
        }
        sessions.clear();
    }

    /**
     * Single SSE output stream protected by a write lock.
     */
    public static final class SessionStream
    {
        private final OutputStream outputStream;
        private final Object writeLock = new Object();
        private volatile boolean closed;

        private SessionStream(OutputStream outputStream)
        {
            this.outputStream = Objects.requireNonNull(outputStream);
        }

        public boolean writeComment(String comment)
        {
            String text = comment != null ? comment : "keep-alive"; //$NON-NLS-1$
            return write(": " + text + "\n\n"); //$NON-NLS-1$ //$NON-NLS-2$
        }

        public boolean writeEvent(long eventId, String eventName, String dataJson)
        {
            StringBuilder payload = new StringBuilder();
            payload.append("event: ").append(eventName != null ? eventName : "message").append("\n"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            payload.append("id: ").append(eventId).append("\n"); //$NON-NLS-1$ //$NON-NLS-2$
            payload.append("data: ").append(dataJson).append("\n\n"); //$NON-NLS-1$ //$NON-NLS-2$
            return write(payload.toString());
        }

        public void markClosed()
        {
            closed = true;
            try
            {
                outputStream.close();
            }
            catch (IOException e)
            {
                // Ignore close failures while evicting stale SSE sessions.
            }
        }

        private boolean write(String payload)
        {
            synchronized (writeLock)
            {
                if (closed)
                {
                    return false;
                }
                try
                {
                    outputStream.write(payload.getBytes(StandardCharsets.UTF_8));
                    outputStream.flush();
                    return true;
                }
                catch (IOException e)
                {
                    closed = true;
                    return false;
                }
            }
        }
    }
}
