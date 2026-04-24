/**
 * MCP Server for EDT - Tests
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package com.ditrix.edt.mcp.server.testruns;

import static org.junit.Assert.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.Test;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class YaxUnitRemoteControlServerTest
{
    @Test
    public void testServerAcceptsHelloAndRoundTripsRunReport()
            throws Exception
    {
        YaxUnitRemoteControlServer server = new YaxUnitRemoteControlServer();
        server.start();
        try
        {
            try (Socket socket = new Socket("127.0.0.1", server.getPort())) //$NON-NLS-1$
            {
                socket.setSoTimeout(5_000);
                InputStream input = socket.getInputStream();
                OutputStream output = socket.getOutputStream();
                performHandshake(input, output);
                sendMaskedText(output,
                        "{\"type\":\"hello\",\"id\":0,\"data\":{\"key\":\"k1\",\"protocolVersion\":\"1.0.0\"}}"); //$NON-NLS-1$
                server.awaitClient("k1", 5_000L); //$NON-NLS-1$

                CompletableFuture<JsonElement> run = new CompletableFuture<>();
                Thread runThread = new Thread(() -> {
                    try
                    {
                        run.complete(server.runTest("k1", "Procedure Test()\\nEndProcedure", "Tests", //$NON-NLS-1$ //$NON-NLS-2$
                                List.of("Test"), true, false, false, 5_000L)); //$NON-NLS-1$
                    }
                    catch (Exception e)
                    {
                        run.completeExceptionally(e);
                    }
                }, "YAxUnitRemoteControlServerTest-run"); //$NON-NLS-1$
                runThread.setDaemon(true);
                runThread.start();

                JsonObject runMessage = JsonParser.parseString(readServerText(input)).getAsJsonObject();
                int id = runMessage.get("id").getAsInt(); //$NON-NLS-1$
                assertEquals("runTest", runMessage.get("type").getAsString()); //$NON-NLS-1$ //$NON-NLS-2$

                sendMaskedText(output, "{\"type\":\"report\",\"id\":" + id //$NON-NLS-1$
                        + ",\"data\":[{\"name\":\"Suite\",\"tests\":1,\"failures\":0,\"errors\":0,\"skipped\":0,\"testcase\":[]}]}"); //$NON-NLS-1$

                assertEquals(1, run.get(5, TimeUnit.SECONDS).getAsJsonArray().size());
            }
        }
        finally
        {
            server.close();
        }
    }

    private static void performHandshake(InputStream input, OutputStream output) throws Exception
    {
        String key = Base64.getEncoder().encodeToString("test-key-1234567".getBytes(StandardCharsets.US_ASCII)); //$NON-NLS-1$
        String request = "GET / HTTP/1.1\r\n" //$NON-NLS-1$
                + "Host: localhost\r\n" //$NON-NLS-1$
                + "Upgrade: websocket\r\n" //$NON-NLS-1$
                + "Connection: Upgrade\r\n" //$NON-NLS-1$
                + "Sec-WebSocket-Key: " + key + "\r\n" //$NON-NLS-1$ //$NON-NLS-2$
                + "Sec-WebSocket-Version: 13\r\n\r\n"; //$NON-NLS-1$
        output.write(request.getBytes(StandardCharsets.US_ASCII));
        output.flush();
        while (true)
        {
            String line = readLine(input);
            if (line == null || line.isEmpty())
            {
                return;
            }
        }
    }

    private static void sendMaskedText(OutputStream output, String text) throws Exception
    {
        byte[] payload = text.getBytes(StandardCharsets.UTF_8);
        output.write(0x81);
        if (payload.length <= 125)
        {
            output.write(0x80 | payload.length);
        }
        else if (payload.length <= 65_535)
        {
            output.write(0x80 | 126);
            output.write((payload.length >>> 8) & 0xFF);
            output.write(payload.length & 0xFF);
        }
        else
        {
            output.write(0x80 | 127);
            output.write(ByteBuffer.allocate(8).putLong(payload.length).array());
        }
        byte[] mask = {1, 2, 3, 4};
        output.write(mask);
        for (int index = 0; index < payload.length; index++)
        {
            output.write(payload[index] ^ mask[index % 4]);
        }
        output.flush();
    }

    private static String readServerText(InputStream input) throws Exception
    {
        int first = input.read();
        int second = input.read();
        int length = second & 0x7F;
        if (length == 126)
        {
            length = (input.read() << 8) | input.read();
        }
        else if (length == 127)
        {
            length = Math.toIntExact(ByteBuffer.wrap(readFully(input, 8)).getLong());
        }
        byte[] payload = readFully(input, length);
        assertEquals(0x1, first & 0x0F);
        return new String(payload, StandardCharsets.UTF_8);
    }

    private static String readLine(InputStream input) throws Exception
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
                return line.toString(StandardCharsets.US_ASCII).replace("\r", ""); //$NON-NLS-1$ //$NON-NLS-2$
            }
            line.write(value);
        }
    }

    private static byte[] readFully(InputStream input, int length) throws Exception
    {
        byte[] bytes = new byte[length];
        int offset = 0;
        while (offset < length)
        {
            int read = input.read(bytes, offset, length - offset);
            if (read < 0)
            {
                throw new AssertionError("Unexpected end of stream"); //$NON-NLS-1$
            }
            offset += read;
        }
        return bytes;
    }
}
