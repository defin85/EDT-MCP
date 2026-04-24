/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package com.ditrix.edt.mcp.server.testruns;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import com.ditrix.edt.mcp.server.protocol.GsonProvider;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Minimal text-frame WebSocket server for YAxUnit external run control.
 */
final class YaxUnitRemoteControlServer implements Closeable
{
    private static final String MAGIC = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"; //$NON-NLS-1$

    private final ConcurrentMap<String, ClientConnection> clientsByKey = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, CompletableFuture<ClientConnection>> awaitedClients = new ConcurrentHashMap<>();
    private final ConcurrentMap<Integer, CompletableFuture<JsonElement>> pendingRuns = new ConcurrentHashMap<>();
    private final AtomicInteger nextMessageId = new AtomicInteger(1);
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final List<ClientConnection> clients = new ArrayList<>();

    private ServerSocket serverSocket;
    private Thread acceptThread;

    void start() throws IOException
    {
        serverSocket = new ServerSocket();
        serverSocket.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
        acceptThread = new Thread(this::acceptLoop, "YAxUnit-RPC-Accept-" + getPort()); //$NON-NLS-1$
        acceptThread.setDaemon(true);
        acceptThread.start();
    }

    int getPort()
    {
        return serverSocket != null ? serverSocket.getLocalPort() : 0;
    }

    ClientConnection awaitClient(String key, long timeoutMs) throws Exception
    {
        ClientConnection existing = clientsByKey.get(key);
        if (existing != null && existing.isOpen())
        {
            return existing;
        }
        CompletableFuture<ClientConnection> future = awaitedClients.computeIfAbsent(key,
                unused -> new CompletableFuture<>());
        return future.get(Math.max(1L, timeoutMs), TimeUnit.MILLISECONDS);
    }

    JsonElement runTest(String key, String module, String moduleName, List<String> methods, boolean server,
            boolean client, boolean ordinaryClient, long timeoutMs) throws Exception
    {
        ClientConnection connection = clientsByKey.get(key);
        if (connection == null || !connection.isOpen())
        {
            throw new IOException("Warm YAxUnit RPC client is not connected"); //$NON-NLS-1$
        }

        int messageId = nextMessageId.getAndIncrement();
        CompletableFuture<JsonElement> future = new CompletableFuture<>();
        pendingRuns.put(Integer.valueOf(messageId), future);
        try
        {
            connection.sendText(buildRunMessage(messageId, module, moduleName, methods, server, client,
                    ordinaryClient));
            return future.get(Math.max(1L, timeoutMs), TimeUnit.MILLISECONDS);
        }
        finally
        {
            pendingRuns.remove(Integer.valueOf(messageId));
        }
    }

    private void acceptLoop()
    {
        while (!closed.get())
        {
            try
            {
                Socket socket = serverSocket.accept();
                Thread clientThread = new Thread(() -> handleClient(socket),
                        "YAxUnit-RPC-Client-" + socket.getPort()); //$NON-NLS-1$
                clientThread.setDaemon(true);
                clientThread.start();
            }
            catch (IOException e)
            {
                if (!closed.get())
                {
                    failPending(e);
                }
                return;
            }
        }
    }

    private void handleClient(Socket socket)
    {
        ClientConnection connection = null;
        try
        {
            socket.setTcpNoDelay(true);
            connection = new ClientConnection(socket);
            performHandshake(connection);
            synchronized (clients)
            {
                clients.add(connection);
            }
            while (connection.isOpen() && !closed.get())
            {
                String text = connection.readTextFrame();
                handleMessage(connection, text);
            }
        }
        catch (Exception e)
        {
            // Disconnects are expected when the Enterprise client is recycled.
        }
        finally
        {
            if (connection != null)
            {
                unregister(connection);
                connection.closeQuietly();
            }
        }
    }

    private void handleMessage(ClientConnection connection, String text)
    {
        JsonObject message = JsonParser.parseString(text).getAsJsonObject();
        String type = stringValue(message, "type"); //$NON-NLS-1$
        if ("hello".equals(type)) //$NON-NLS-1$
        {
            JsonObject data = objectValue(message, "data"); //$NON-NLS-1$
            String key = stringValue(data, "key"); //$NON-NLS-1$
            connection.setClientKey(key);
            clientsByKey.put(key, connection);
            CompletableFuture<ClientConnection> future = awaitedClients.computeIfAbsent(key,
                    unused -> new CompletableFuture<>());
            future.complete(connection);
            return;
        }
        if ("report".equals(type)) //$NON-NLS-1$
        {
            int id = intValue(message, "id"); //$NON-NLS-1$
            CompletableFuture<JsonElement> future = pendingRuns.get(Integer.valueOf(id));
            if (future != null)
            {
                future.complete(message.get("data")); //$NON-NLS-1$
            }
        }
    }

    private void performHandshake(ClientConnection connection) throws Exception
    {
        String secWebSocketKey = null;
        while (true)
        {
            String line = connection.readHttpLine();
            if (line == null)
            {
                throw new EOFException("Client closed before WebSocket handshake"); //$NON-NLS-1$
            }
            if (line.isEmpty())
            {
                break;
            }
            int separator = line.indexOf(':');
            if (separator > 0)
            {
                String header = line.substring(0, separator).trim().toLowerCase(Locale.ROOT);
                if ("sec-websocket-key".equals(header)) //$NON-NLS-1$
                {
                    secWebSocketKey = line.substring(separator + 1).trim();
                }
            }
        }
        if (secWebSocketKey == null || secWebSocketKey.isBlank())
        {
            throw new IOException("Missing Sec-WebSocket-Key"); //$NON-NLS-1$
        }
        String accept = Base64.getEncoder().encodeToString(
                MessageDigest.getInstance("SHA-1").digest((secWebSocketKey + MAGIC).getBytes(StandardCharsets.US_ASCII))); //$NON-NLS-1$
        String response = "HTTP/1.1 101 Switching Protocols\r\n" //$NON-NLS-1$
                + "Upgrade: websocket\r\n" //$NON-NLS-1$
                + "Connection: Upgrade\r\n" //$NON-NLS-1$
                + "Sec-WebSocket-Accept: " + accept + "\r\n\r\n"; //$NON-NLS-1$ //$NON-NLS-2$
        connection.writeRaw(response.getBytes(StandardCharsets.US_ASCII));
    }

    private String buildRunMessage(int id, String module, String moduleName, List<String> methods, boolean server,
            boolean client, boolean ordinaryClient)
    {
        JsonObject data = new JsonObject();
        data.addProperty("module", module); //$NON-NLS-1$
        data.addProperty("moduleName", moduleName); //$NON-NLS-1$
        data.add("methods", GsonProvider.get().toJsonTree(methods != null ? methods : List.of())); //$NON-NLS-1$
        data.addProperty("server", server); //$NON-NLS-1$
        data.addProperty("client", client); //$NON-NLS-1$
        data.addProperty("ordinaryClient", ordinaryClient); //$NON-NLS-1$

        JsonObject message = new JsonObject();
        message.addProperty("type", "runTest"); //$NON-NLS-1$ //$NON-NLS-2$
        message.addProperty("id", id); //$NON-NLS-1$
        message.add("data", data); //$NON-NLS-1$
        return GsonProvider.toJson(message);
    }

    private void unregister(ClientConnection connection)
    {
        synchronized (clients)
        {
            clients.remove(connection);
        }
        String key = connection.getClientKey();
        if (key != null)
        {
            clientsByKey.remove(key, connection);
        }
    }

    private void failPending(Exception e)
    {
        pendingRuns.values().forEach(future -> future.completeExceptionally(e));
        awaitedClients.values().forEach(future -> future.completeExceptionally(e));
    }

    @Override
    public void close()
    {
        if (!closed.compareAndSet(false, true))
        {
            return;
        }
        closeQuietly(serverSocket);
        synchronized (clients)
        {
            for (ClientConnection client : new ArrayList<>(clients))
            {
                client.closeQuietly();
            }
            clients.clear();
        }
        failPending(new IOException("YAxUnit RPC server closed")); //$NON-NLS-1$
    }

    private static JsonObject objectValue(JsonObject object, String key)
    {
        if (object == null || !object.has(key) || object.get(key).isJsonNull() || !object.get(key).isJsonObject())
        {
            return new JsonObject();
        }
        return object.getAsJsonObject(key);
    }

    private static String stringValue(JsonObject object, String key)
    {
        if (object == null || !object.has(key) || object.get(key).isJsonNull())
        {
            return null;
        }
        return object.get(key).getAsString();
    }

    private static int intValue(JsonObject object, String key)
    {
        if (object == null || !object.has(key) || object.get(key).isJsonNull())
        {
            return 0;
        }
        return object.get(key).getAsInt();
    }

    private static void closeQuietly(Closeable closeable)
    {
        if (closeable == null)
        {
            return;
        }
        try
        {
            closeable.close();
        }
        catch (IOException e)
        {
            // Ignore close failures.
        }
    }

    static final class ClientConnection
    {
        private final Socket socket;
        private final InputStream input;
        private final OutputStream output;
        private volatile String clientKey;

        ClientConnection(Socket socket) throws IOException
        {
            this.socket = socket;
            this.input = socket.getInputStream();
            this.output = socket.getOutputStream();
        }

        boolean isOpen()
        {
            return !socket.isClosed() && socket.isConnected();
        }

        String getClientKey()
        {
            return clientKey;
        }

        void setClientKey(String clientKey)
        {
            this.clientKey = clientKey;
        }

        String readHttpLine() throws IOException
        {
            ByteArrayOutputStream line = new ByteArrayOutputStream();
            while (true)
            {
                int value = input.read();
                if (value < 0)
                {
                    return line.size() == 0 ? null : line.toString(StandardCharsets.US_ASCII);
                }
                if (value == '\n')
                {
                    break;
                }
                if (value != '\r')
                {
                    line.write(value);
                }
            }
            return line.toString(StandardCharsets.US_ASCII);
        }

        String readTextFrame() throws IOException
        {
            while (true)
            {
                int first = input.read();
                int second = input.read();
                if (first < 0 || second < 0)
                {
                    throw new EOFException("WebSocket stream closed"); //$NON-NLS-1$
                }
                int opcode = first & 0x0F;
                boolean masked = (second & 0x80) != 0;
                long length = second & 0x7F;
                if (length == 126)
                {
                    length = (readByte() << 8) | readByte();
                }
                else if (length == 127)
                {
                    byte[] bytes = readFully(8);
                    length = ByteBuffer.wrap(bytes).getLong();
                }
                byte[] mask = masked ? readFully(4) : null;
                byte[] payload = readFully(Math.toIntExact(length));
                if (masked)
                {
                    for (int index = 0; index < payload.length; index++)
                    {
                        payload[index] = (byte)(payload[index] ^ mask[index % 4]);
                    }
                }
                if (opcode == 0x8)
                {
                    throw new EOFException("WebSocket close frame received"); //$NON-NLS-1$
                }
                if (opcode == 0x9)
                {
                    sendFrame(0xA, payload);
                    continue;
                }
                if (opcode == 0x1)
                {
                    return new String(payload, StandardCharsets.UTF_8);
                }
            }
        }

        synchronized void sendText(String text) throws IOException
        {
            sendFrame(0x1, text.getBytes(StandardCharsets.UTF_8));
        }

        synchronized void sendFrame(int opcode, byte[] payload) throws IOException
        {
            output.write(0x80 | opcode);
            int length = payload != null ? payload.length : 0;
            if (length <= 125)
            {
                output.write(length);
            }
            else if (length <= 65_535)
            {
                output.write(126);
                output.write((length >>> 8) & 0xFF);
                output.write(length & 0xFF);
            }
            else
            {
                output.write(127);
                output.write(ByteBuffer.allocate(8).putLong(length).array());
            }
            if (payload != null)
            {
                output.write(payload);
            }
            output.flush();
        }

        void writeRaw(byte[] bytes) throws IOException
        {
            output.write(bytes);
            output.flush();
        }

        private int readByte() throws IOException
        {
            int value = input.read();
            if (value < 0)
            {
                throw new EOFException("WebSocket stream closed"); //$NON-NLS-1$
            }
            return value;
        }

        private byte[] readFully(int length) throws IOException
        {
            byte[] bytes = new byte[length];
            int offset = 0;
            while (offset < length)
            {
                int read = input.read(bytes, offset, length - offset);
                if (read < 0)
                {
                    throw new EOFException("WebSocket stream closed"); //$NON-NLS-1$
                }
                offset += read;
            }
            return bytes;
        }

        void closeQuietly()
        {
            YaxUnitRemoteControlServer.closeQuietly(socket);
        }
    }
}
