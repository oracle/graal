/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.tools.chromeinspector.test;

import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.Assert;
import org.junit.Test;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.io.MessageEndpoint;
import org.graalvm.polyglot.io.MessageTransport;

/**
 * Tests the use of {@link MessageTransport} with the Inspector.
 */
public class InspectorMessageTransportTest extends EnginesGCedTest {

    private static final String PORT = "54367";
    private static final Pattern URI_PATTERN = Pattern.compile("ws://.*:" + PORT + "/[\\dA-Za-z_\\-]+");
    private static final String[] INITIAL_MESSAGES = {
                    "{\"id\":5,\"method\":\"Runtime.enable\"}",
                    "{\"id\":6,\"method\":\"Debugger.enable\"}",
                    "{\"id\":7,\"method\":\"Debugger.setPauseOnExceptions\",\"params\":{\"state\":\"none\"}}",
                    "{\"id\":8,\"method\":\"Debugger.setAsyncCallStackDepth\",\"params\":{\"maxDepth\":32}}",
                    "{\"id\":20,\"method\":\"Debugger.setBlackboxPatterns\",\"params\":{\"patterns\":[]}}",
                    "{\"id\":28,\"method\":\"Runtime.runIfWaitingForDebugger\"}"
    };
    private static final String[] MESSAGES_TO_BACKEND;
    private static final String[] MESSAGES_TO_CLIENT = {
                    "{\"result\":{},\"id\":5}",
                    "{\"result\":{},\"id\":6}",
                    "{\"result\":{},\"id\":7}",
                    "{\"result\":{},\"id\":8}",
                    "{\"result\":{},\"id\":20}",
                    "{\"result\":{},\"id\":28}",
                    "{\"method\":\"Runtime.executionContextCreated\"",
                    "{\"method\":\"Debugger.paused\",",
                    "{\"result\":{},\"id\":50}",
                    "{\"method\":\"Debugger.resumed\"}",
                    "{\"method\":\"Debugger.paused\",",
                    "{\"result\":{},\"id\":100}",
                    "{\"method\":\"Debugger.resumed\"}"
    };
    static {
        MESSAGES_TO_BACKEND = Arrays.copyOf(INITIAL_MESSAGES, INITIAL_MESSAGES.length + 2);
        MESSAGES_TO_BACKEND[INITIAL_MESSAGES.length] = "{\"id\":50,\"method\":\"Debugger.stepOver\"}";
        MESSAGES_TO_BACKEND[INITIAL_MESSAGES.length + 1] = "{\"id\":100,\"method\":\"Debugger.resume\"}";
    }

    @Test
    public void inspectorEndpointDefaultPathTest() {
        inspectorEndpointTest(null);
    }

    @Test
    public void inspectorEndpointExplicitPathTest() {
        inspectorEndpointTest("simplePath" + SecureInspectorPathGenerator.getToken());
        inspectorEndpointTest("/some/complex/path" + SecureInspectorPathGenerator.getToken());
    }

    @Test
    public void inspectorEndpointRaceTest() {
        RaceControl rc = new RaceControl();
        inspectorEndpointTest(null, rc);
    }

    private void inspectorEndpointTest(String path) {
        inspectorEndpointTest(path, null);
    }

    private void inspectorEndpointTest(String path, RaceControl rc) {
        Session session = new Session(rc);
        DebuggerEndpoint endpoint = new DebuggerEndpoint(path, rc);
        try (Engine engine = endpoint.onOpen(session)) {
            Context context = Context.newBuilder().engine(engine).build();
            Value result = context.eval("sl", "function main() {\n  x = 1;\n  return x;\n}");
            Assert.assertEquals("Result", "1", result.toString());

            endpoint.onClose(session);
        }
        int numMessages = MESSAGES_TO_BACKEND.length + MESSAGES_TO_CLIENT.length;
        Assert.assertEquals(session.messages.toString(), numMessages, session.messages.size());
        assertMessages(session.messages, MESSAGES_TO_BACKEND.length, MESSAGES_TO_CLIENT.length);
    }

    private static void assertMessages(List<String> messages, int num2B, int num2C) {
        for (int ib = 0, ic = 0; ib < num2B && ic < num2C;) {
            String msg = messages.get(ib + ic);
            if (msg.startsWith("2B")) { // to backend
                if (!msg.substring(2).startsWith(MESSAGES_TO_BACKEND[ib])) {
                    Assert.fail("Expected start with '" + MESSAGES_TO_BACKEND[ib] + "', got: '" + msg + "'");
                }
                ib++;
            } else {
                Assert.assertTrue(msg, msg.startsWith("2C")); // to client
                if (!msg.substring(2).startsWith(MESSAGES_TO_CLIENT[ic])) {
                    Assert.fail("Expected start with '" + MESSAGES_TO_CLIENT[ic] + "', got: '" + msg + "'");
                }
                ic++;
            }
        }
    }

    @Test
    public void inspectorReconnectTest() throws IOException, InterruptedException {
        Session session = new Session(null);
        DebuggerEndpoint endpoint = new DebuggerEndpoint("simplePath" + SecureInspectorPathGenerator.getToken(), null);
        try (Engine engine = endpoint.onOpen(session)) {

            try (Context context = Context.newBuilder().engine(engine).build()) {
                Value result = context.eval("sl", "function main() {\n  x = 1;\n  return x;\n}");
                Assert.assertEquals("Result", "1", result.toString());

                MessageEndpoint peerEndpoint = endpoint.peer;
                peerEndpoint.sendClose();
                Assert.assertNotSame(peerEndpoint, endpoint.peer);
                result = context.eval("sl", "function main() {\n  x = 2;\n  return x;\n}");
                Assert.assertEquals("Result", "2", result.toString());
            }
            // We will not get the last 6 messages to client and 2 to backend
            // as we do not do initial suspension on re-connect
            int expectedNumMessages = 2 * (MESSAGES_TO_BACKEND.length + MESSAGES_TO_CLIENT.length) - 8;
            synchronized (session.messages) {
                while (session.messages.size() < expectedNumMessages) {
                    // The reply messages are sent asynchronously. We need to wait for them.
                    session.messages.wait();
                }
            }

            Assert.assertEquals(session.messages.toString(), expectedNumMessages, session.messages.size());
            assertMessages(session.messages, MESSAGES_TO_BACKEND.length, MESSAGES_TO_CLIENT.length);
            // Messages after reconnect
            List<String> messagesAfterReconnect = session.messages.subList(MESSAGES_TO_BACKEND.length + MESSAGES_TO_CLIENT.length, expectedNumMessages);
            assertMessages(messagesAfterReconnect, MESSAGES_TO_BACKEND.length - 2, MESSAGES_TO_CLIENT.length - 6);
        }
    }

    @Test
    public void inspectorClosedTest() throws IOException, InterruptedException {
        Session session = new Session(null);
        DebuggerEndpoint endpoint = new DebuggerEndpoint("simplePath" + SecureInspectorPathGenerator.getToken(), null);
        endpoint.setOpenCountLimit(1);
        try (Engine engine = endpoint.onOpen(session)) {
            try (Context context = Context.newBuilder().engine(engine).build()) {
                Value result = context.eval("sl", "function main() {\n  x = 1;\n  return x;\n}");
                Assert.assertEquals("Result", "1", result.toString());

                MessageEndpoint peerEndpoint = endpoint.peer;
                peerEndpoint.sendClose();
                // Execution goes on after close:
                result = context.eval("sl", "function main() {\n  x = 2;\n  debugger;\n  return x;\n}");
                Assert.assertEquals("Result", "2", result.toString());
            }
            try (Engine engine2 = endpoint.onOpen(session)) {
                try (Context context = Context.newBuilder().engine(engine2).build()) {
                    Value result = context.eval("sl", "function main() {\n  x = 3;\n  debugger;  return x;\n}");
                    Assert.assertEquals("Result", "3", result.toString());
                }
                int expectedNumMessages = MESSAGES_TO_BACKEND.length + MESSAGES_TO_CLIENT.length;
                synchronized (session.messages) {
                    while (session.messages.size() < expectedNumMessages) {
                        // The reply messages are sent asynchronously. We need to wait for them.
                        session.messages.wait();
                    }
                }

                Assert.assertEquals(session.messages.toString(), expectedNumMessages, session.messages.size());
                assertMessages(session.messages, MESSAGES_TO_BACKEND.length, MESSAGES_TO_CLIENT.length);
            }
        }
    }

    @Test
    public void inspectorVetoedTest() {
        Engine.Builder engineBuilder = Engine.newBuilder().serverTransport(new MessageTransport() {

            @Override
            public MessageEndpoint open(URI uri, MessageEndpoint peerEndpoint) throws IOException, MessageTransport.VetoException {
                throw new MessageTransport.VetoException("Server vetoed.");
            }
        }).option("inspect", PORT);
        try {
            engineBuilder.build();
            Assert.fail("Veto not effective.");
        } catch (PolyglotException ex) {
            String message = ex.getMessage();
            Assert.assertTrue(message, message.startsWith("Starting inspector on "));
            Assert.assertTrue(message, message.endsWith(":" + PORT + " failed: Server vetoed."));
        }
    }

    private static final class Session {

        private final RaceControl rc;
        final List<String> messages = Collections.synchronizedList(new ArrayList<>(MESSAGES_TO_BACKEND.length + MESSAGES_TO_CLIENT.length));
        private final BasicRemote remote = new BasicRemote(messages);
        private boolean opened = true;

        Session(RaceControl rc) {
            this.rc = rc;
        }

        BasicRemote getBasicRemote() {
            return remote;
        }

        public boolean isOpen() {
            return opened;
        }

        void addMessageHandler(MsgHandler handler) throws IOException {
            remote.handler = handler;
            sendInitialMessages(handler);
            if (rc != null) {
                rc.clientMessagesSent();
            }
        }

        private void sendInitialMessages(final MsgHandler handler) throws IOException {
            if (opened) {
                for (String message : INITIAL_MESSAGES) {
                    handler.onMessage(message);
                }
            }
        }

        private void close() {
            opened = false;
        }

        interface MsgHandler {
            void onMessage(String message) throws IOException;
        }
    }

    private static final class BasicRemote {

        Session.MsgHandler handler;
        private final List<String> messages;
        private boolean didStep = false;

        BasicRemote(List<String> messages) {
            this.messages = messages;
        }

        void sendText(String text) throws IOException {
            if (!text.startsWith("{\"method\":\"Debugger.scriptParsed\"")) {
                synchronized (messages) {
                    messages.add("2C" + text);
                    messages.notifyAll();
                }
            }
            if (text.startsWith("{\"method\":\"Debugger.paused\"")) {
                if (!didStep) {
                    handler.onMessage("{\"id\":50,\"method\":\"Debugger.stepOver\"}");
                    didStep = true;
                } else {
                    handler.onMessage("{\"id\":100,\"method\":\"Debugger.resume\"}");
                    didStep = false;
                }
            }
        }

    }

    private final class DebuggerEndpoint {

        private final String path;
        private final RaceControl rc;
        private int openCountLimit = -1;
        MessageEndpoint peer;

        DebuggerEndpoint(String path, RaceControl rc) {
            this.path = path;
            this.rc = rc;
        }

        void setOpenCountLimit(int openCountLimit) {
            this.openCountLimit = openCountLimit;
        }

        public Engine onOpen(final Session session) {
            assert this != null;
            Engine.Builder engineBuilder = Engine.newBuilder().serverTransport(new MessageTransport() {
                @Override
                public MessageEndpoint open(URI requestURI, MessageEndpoint peerEndpoint) throws IOException, MessageTransport.VetoException {
                    Assert.assertEquals("Invalid protocol", "ws", requestURI.getScheme());
                    DebuggerEndpoint.this.peer = peerEndpoint;
                    String uriStr = requestURI.toString();
                    if (path == null) {
                        Assert.assertTrue(uriStr, URI_PATTERN.matcher(uriStr).matches());
                    } else {
                        Assert.assertTrue(uriStr, uriStr.startsWith("ws://"));
                        String absolutePath = path.startsWith("/") ? path : "/" + path;
                        Assert.assertTrue(uriStr, uriStr.endsWith(":" + PORT + absolutePath));
                    }
                    boolean closed = false;
                    if (openCountLimit == 0) {
                        closed = true;
                        peerEndpoint.sendClose();
                    } else if (openCountLimit > 0) {
                        openCountLimit--;
                    }
                    MessageEndpoint ourEndpoint = new ChromeDebuggingProtocolMessageHandler(session, requestURI, peerEndpoint, closed);
                    if (rc != null) {
                        rc.waitTillClientDataAreSent();
                    }
                    return ourEndpoint;
                }
            }).option("inspect", PORT);
            if (path != null) {
                engineBuilder.option("inspect.Path", path);
            }
            Engine engine = engineBuilder.build();
            addEngineReference(engine);
            return engine;
        }

        public void onClose(Session session) {
            Assert.assertNotNull(session);
            assert this != null;
        }

    }

    private static final class ChromeDebuggingProtocolMessageHandler implements MessageEndpoint {

        private final Session session;

        ChromeDebuggingProtocolMessageHandler(Session session, URI uri, MessageEndpoint peerEndpoint, boolean closed) throws IOException {
            this.session = session;
            if (closed) {
                session.close();
            }
            Assert.assertEquals("ws", uri.getScheme());

            /* Forward a JSON message from the client to the backend. */
            session.addMessageHandler(message -> {
                Assert.assertTrue(session.isOpen());
                session.messages.add("2B" + message);
                peerEndpoint.sendText(message);
            });
        }

        /* Forward a JSON message from the backend to the client. */
        @Override
        public void sendText(String text) throws IOException {
            Assert.assertTrue(session.isOpen());
            session.getBasicRemote().sendText(text);
        }

        @Override
        public void sendBinary(ByteBuffer data) throws IOException {
            throw new UnsupportedOperationException("sendBinary");
        }

        @Override
        public void sendPing(ByteBuffer data) throws IOException {
            throw new UnsupportedOperationException("onPing");
        }

        @Override
        public void sendPong(ByteBuffer data) throws IOException {
            throw new UnsupportedOperationException("onPong");
        }

        @Override
        public void sendClose() throws IOException {
            session.close();
        }

    }

    // Test a possible race between data sent and transport open.
    // The WSInterceptorServer needs to be able to deal with sendText() being called before opened()
    private static final class RaceControl {

        private boolean clientSent = false;

        private synchronized void clientMessagesSent() {
            clientSent = true;
            notifyAll();
        }

        private synchronized void waitTillClientDataAreSent() {
            try {
                while (!clientSent) {
                    wait();
                }
                // The data were sent, but it takes a while to process them till
                // WSInterceptorServer#sendText gets called.
                Thread.sleep(100);
            } catch (InterruptedException ex) {
                throw new IllegalStateException(ex);
            }
        }

    }
}
