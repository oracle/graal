/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.truffle.tools.dap.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.graalvm.shadowed.org.json.JSONArray;
import org.graalvm.shadowed.org.json.JSONObject;
import org.junit.Test;

import com.oracle.truffle.api.instrumentation.test.InstrumentationTestLanguage;
import com.oracle.truffle.tools.dap.server.DebugProtocolServerListener;

public class DAPServerLifecycleTest {

    private static final String SOURCE_PATH = "/runtime/reconnect.itl";
    private static final Source SOURCE = Source.newBuilder(InstrumentationTestLanguage.ID, "ROOT(\nSTATEMENT(CONSTANT(42))\n)", "reconnect.itl").uri(URI.create(
                    "file:///runtime/reconnect.itl")).buildLiteral();

    @Test
    public void testReconnectAfterDisconnect() throws Exception {
        try (TestServer server = new TestServer(false, false); Socket first = server.connect()) {
            for (int session = 0; session < 3; session++) {
                // Keep the first client open: reconnect must not depend on the IDE closing it.
                try (Socket socket = session == 0 ? first : server.connect()) {
                    boolean zeroBased = session == 0;
                    request(socket, "initialize", 1, new JSONObject().put("adapterID", "graalvm").put("linesStartAt1", !zeroBased).put("columnsStartAt1", !zeroBased));
                    JSONObject attach = zeroBased ? new JSONObject().put("localRoot", "/client").put("remoteRoot", "/runtime") : new JSONObject();
                    request(socket, "attach", 2, attach);
                    request(socket, "configurationDone", 3);
                    // Previous breakpoints must not survive, nor may startup suspension be rearmed.
                    server.evaluate().get(5, TimeUnit.SECONDS);
                    String path = zeroBased ? "/client/reconnect.itl" : SOURCE_PATH;
                    int line = zeroBased ? 1 : 2;
                    JSONObject breakpoints = request(socket, "setBreakpoints", 4, breakpointArguments(path, line));
                    assertEquals(1, breakpoints.getJSONObject("body").getJSONArray("breakpoints").getJSONObject(0).getInt("id"));
                    CompletableFuture<?> evaluation = server.evaluate();
                    JSONObject stopped = event(socket, "stopped");
                    assertEquals("breakpoint", stopped.getJSONObject("body").getString("reason"));
                    int threadId = stopped.getJSONObject("body").getInt("threadId");
                    JSONObject trace = request(socket, "stackTrace", 5, new JSONObject().put("threadId", threadId));
                    JSONObject frame = trace.getJSONObject("body").getJSONArray("stackFrames").getJSONObject(0);
                    assertEquals(line, frame.getInt("line"));
                    assertEquals(path, frame.getJSONObject("source").getString("path"));
                    JSONObject scopes = request(socket, "scopes", 6, new JSONObject().put("frameId", frame.getInt("id")));
                    int variables = scopes.getJSONObject("body").getJSONArray("scopes").getJSONObject(0).getInt("variablesReference");
                    request(socket, "variables", 7, new JSONObject().put("variablesReference", variables));
                    request(socket, "continue", 8, new JSONObject().put("threadId", threadId));
                    evaluation.get(5, TimeUnit.SECONDS);
                    request(socket, "disconnect", 9);
                    assertConnectionClosed(socket);
                }
            }
            assertTrue(!server.listener.isClosed());
        }
    }

    @Test
    public void testDisconnectWhileSuspended() throws Exception {
        testSuspendedDisconnect(false);
    }

    @Test
    public void testReconnectAfterEOFWhileSuspended() throws Exception {
        testSuspendedDisconnect(true);
    }

    private static void testSuspendedDisconnect(boolean eof) throws Exception {
        try (TestServer server = new TestServer(false, false)) {
            CompletableFuture<?> evaluation;
            try (Socket socket = server.connect()) {
                request(socket, "initialize", 1);
                request(socket, "attach", 2);
                request(socket, "configurationDone", 3);
                request(socket, "setBreakpoints", 4, breakpointArguments(SOURCE_PATH, 2));
                evaluation = server.evaluate();
                int threadId = event(socket, "stopped").getJSONObject("body").getInt("threadId");
                // A queued suspended-thread request must finish before the next session starts.
                send(socket, "stackTrace", 5, new JSONObject().put("threadId", threadId));
                if (!eof) {
                    request(socket, "disconnect", 6);
                    assertConnectionClosed(socket);
                }
            }
            evaluation.get(5, TimeUnit.SECONDS);
            try (Socket socket = server.connect()) {
                request(socket, "initialize", 1);
                request(socket, "attach", 2);
                request(socket, "configurationDone", 3);
                // The old session's breakpoint must be gone, and guest execution must resume.
                server.evaluate().get(5, TimeUnit.SECONDS);
                request(socket, "disconnect", 4);
            }
        }
    }

    @Test
    public void testDisposeBeforeConnection() throws Exception {
        try (TestServer server = new TestServer(true, true)) {
            server.server.dispose();
            server.completion.get(5, TimeUnit.SECONDS);
            assertTrue(server.listener.isClosed());
            // Disposing before attachment must release the startup wait as well.
            server.evaluate().get(5, TimeUnit.SECONDS);
        }
    }

    @Test
    public void testEngineCloseWithIdleClient() throws Exception {
        try (TestServer server = new TestServer(false, false); Socket socket = server.connect()) {
            request(socket, "initialize", 1);
            request(socket, "attach", 2);
            request(socket, "configurationDone", 3);
            CompletableFuture.runAsync(server.engine::close).get(5, TimeUnit.SECONDS);
            server.completion.get(5, TimeUnit.SECONDS);
            event(socket, "terminated");
            assertConnectionClosed(socket);
            assertTrue(server.listener.isClosed());
        }
    }

    @Test
    public void testLaunchDisconnectStillClosesContexts() throws Exception {
        try (TestServer server = new TestServer(false, false); Socket socket = server.connect()) {
            server.evaluate().get(5, TimeUnit.SECONDS);
            request(socket, "initialize", 1);
            request(socket, "launch", 2);
            request(socket, "configurationDone", 3);
            request(socket, "disconnect", 4);
            assertConnectionClosed(socket);
            try (Socket nextClient = server.connect()) {
                request(nextClient, "initialize", 1);
                assertTrue(assertThrows(PolyglotException.class, () -> server.context.eval(SOURCE)).isCancelled());
                try (Context nextContext = Context.newBuilder(InstrumentationTestLanguage.ID).engine(server.engine).build()) {
                    request(nextClient, "attach", 2);
                    request(nextClient, "configurationDone", 3);
                    CompletableFuture.runAsync(() -> nextContext.eval(SOURCE)).get(5, TimeUnit.SECONDS);
                    request(nextClient, "disconnect", 4);
                }
            }
        }
    }

    @Test
    public void testStartupSuspendIsNotRearmed() throws Exception {
        try (TestServer server = new TestServer(true, false); Socket socket = server.connect()) {
            CompletableFuture<?> evaluation = server.evaluate();
            request(socket, "initialize", 1);
            request(socket, "attach", 2);
            request(socket, "configurationDone", 3);
            assertEquals("debugger_statement", event(socket, "stopped").getJSONObject("body").getString("reason"));
            request(socket, "disconnect", 4);
            evaluation.get(5, TimeUnit.SECONDS);
            server.evaluate().get(5, TimeUnit.SECONDS);
            try (Socket nextClient = server.connect()) {
                request(nextClient, "initialize", 1);
                request(nextClient, "attach", 2);
                request(nextClient, "configurationDone", 3);
                server.evaluate().get(5, TimeUnit.SECONDS);
                request(nextClient, "disconnect", 4);
            }
        }
    }

    @Test
    public void testStartupWaitAttachedIsNotRearmed() throws Exception {
        try (TestServer server = new TestServer(false, true); Socket socket = server.connect()) {
            CompletableFuture<?> evaluation = server.evaluate();
            request(socket, "initialize", 1);
            request(socket, "attach", 2);
            assertThrows(TimeoutException.class, () -> evaluation.get(100, TimeUnit.MILLISECONDS));
            request(socket, "configurationDone", 3);
            evaluation.get(5, TimeUnit.SECONDS);
            request(socket, "disconnect", 4);
            server.evaluate().get(5, TimeUnit.SECONDS);
            try (Socket nextClient = server.connect()) {
                request(nextClient, "initialize", 1);
                request(nextClient, "attach", 2);
                request(nextClient, "configurationDone", 3);
                server.evaluate().get(5, TimeUnit.SECONDS);
                request(nextClient, "disconnect", 4);
            }
        }
    }

    private static void assertConnectionClosed(Socket socket) throws IOException {
        // Events already sent before shutdown may still be buffered ahead of EOF.
        while (true) {
            JSONObject message = receive(socket);
            if (message == null) {
                return;
            }
            assertTrue("Unexpected termination event after disconnect.", !"terminated".equals(message.optString("event")));
        }
    }

    private static JSONObject breakpointArguments(String path, int line) {
        return new JSONObject().put("source", new JSONObject().put("path", path)).put("breakpoints", new JSONArray().put(new JSONObject().put("line", line)));
    }

    private static JSONObject request(Socket socket, String command, int sequence) throws IOException {
        JSONObject arguments = new JSONObject();
        if ("initialize".equals(command)) {
            arguments.put("adapterID", "graalvm");
        } else if ("disconnect".equals(command)) {
            arguments.put("restart", false).put("terminateDebuggee", false);
        }
        return request(socket, command, sequence, arguments);
    }

    private static JSONObject request(Socket socket, String command, int sequence, JSONObject arguments) throws IOException {
        send(socket, command, sequence, arguments);
        while (true) {
            JSONObject response = receive(socket);
            assertNotNull("Connection closed before responding to " + command, response);
            if ("response".equals(response.getString("type")) && response.getInt("request_seq") == sequence) {
                assertEquals(command, response.getString("command"));
                assertTrue(response.toString(), response.getBoolean("success"));
                return response;
            }
        }
    }

    private static void send(Socket socket, String command, int sequence, JSONObject arguments) throws IOException {
        byte[] bytes = new JSONObject().put("seq", sequence).put("type", "request").put("command", command).put("arguments", arguments).toString().getBytes(StandardCharsets.UTF_8);
        OutputStream out = socket.getOutputStream();
        out.write(("Content-Length: " + bytes.length + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
        out.write(bytes);
        out.flush();
    }

    private static JSONObject receive(Socket socket) throws IOException {
        byte[] bytes = DAPTester.readMessageBytes(socket.getInputStream());
        return bytes == null ? null : new JSONObject(new String(bytes, StandardCharsets.UTF_8));
    }

    private static JSONObject event(Socket socket, String name) throws IOException {
        while (true) {
            JSONObject message = receive(socket);
            assertNotNull("Connection closed before event " + name, message);
            if ("event".equals(message.getString("type")) && name.equals(message.getString("event"))) {
                return message;
            }
        }
    }

    private static final class TestServer implements AutoCloseable {

        final Engine engine = Engine.newBuilder().build();
        final Context context = Context.newBuilder(InstrumentationTestLanguage.ID).engine(engine).build();
        final ServerSocket listener = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
        final DebugProtocolServerListener server;
        final CompletableFuture<?> completion;

        TestServer(boolean suspend, boolean waitAttached) throws IOException {
            server = engine.getInstruments().get(DAPTestInstrument.ID).lookup(DAPSessionHandlerProvider.class).getServer(suspend, waitAttached);
            completion = server.start(listener);
        }

        Socket connect() throws IOException {
            Socket socket = new Socket();
            socket.connect(listener.getLocalSocketAddress(), 5000);
            socket.setSoTimeout(5000);
            return socket;
        }

        CompletableFuture<?> evaluate() {
            return CompletableFuture.runAsync(() -> context.eval(SOURCE));
        }

        @Override
        public void close() {
            server.dispose();
            try {
                completion.get(5, TimeUnit.SECONDS);
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted waiting for DAP listener shutdown.", error);
            } catch (ExecutionException | TimeoutException error) {
                throw new AssertionError("DAP listener failed to shut down.", error);
            } finally {
                context.close(true);
                engine.close();
            }
        }
    }
}
