/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.test.polyglot;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;

import org.junit.Assert;
import org.junit.Test;

import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Instrument;
import org.graalvm.polyglot.io.MessageTransport;

import com.oracle.truffle.api.TruffleMessageTransportHandler;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;

public class MessageTransportTest {

    private static final String INSTRUMENT_ID = "messageTransportTestInstrument1";

    private final URI testUri = createTestUri();

    private static URI createTestUri() {
        try {
            return new URI("http", "a", "/b", "c");
        } catch (URISyntaxException ex) {
            throw new RuntimeException(ex);
        }
    }

    @Test
    public void defaultTransportTest() throws Exception {
        Engine engine = Engine.create();
        Instrument access = engine.getInstruments().get(INSTRUMENT_ID);
        TruffleInstrument.Env env = access.lookup(TruffleInstrument.Env.class);
        URI[] uris = new URI[]{testUri, new URI("ws", "a", "b"), new URI("file", "/a", "")};
        for (URI uri : uris) {
            Assert.assertNull(env.getMessageTransportHandler(uri, true));
        }
    }

    @Test
    public void vetoedTransportTest() throws Exception {
        Engine engine = Engine.newBuilder().messageTransportInterceptor(new MessageTransport.Interceptor() {
            @Override
            public boolean handle(URI uri, boolean server) throws VetoException {
                throw new VetoException("No access to " + testUri);
            }

            @Override
            public MessageTransport.MessageHandler onOpen(URI uri, boolean server, MessageTransport transport) {
                Assert.fail();
                return null;
            }
        }).build();
        Instrument access = engine.getInstruments().get(INSTRUMENT_ID);
        TruffleInstrument.Env env = access.lookup(TruffleInstrument.Env.class);
        try {
            env.getMessageTransportHandler(testUri, false);
            Assert.fail();
        } catch (MessageTransport.Interceptor.VetoException ex) {
            Assert.assertEquals("No access to " + testUri, ex.getMessage());
        }
    }

    @Test
    public void noTransportTest() throws Exception {
        Engine engine = Engine.newBuilder().messageTransportInterceptor(new MessageTransport.Interceptor() {
            @Override
            public boolean handle(URI uri, boolean server) throws VetoException {
                return false;
            }

            @Override
            public MessageTransport.MessageHandler onOpen(URI uri, boolean server, MessageTransport transport) {
                Assert.fail();
                return null;
            }
        }).build();
        Instrument access = engine.getInstruments().get(INSTRUMENT_ID);
        TruffleInstrument.Env env = access.lookup(TruffleInstrument.Env.class);
        Assert.assertNull(env.getMessageTransportHandler(testUri, false));
    }

    @Test
    public void testMessages() throws Exception {
        for (MessageKind kind : MessageKind.values()) {
            if (kind == MessageKind.CLOSE) {
                testMessages(kind, null, null);
            } else {
                testMessages(kind, "Hi From Client", "Hi From Backend", "Next From Client");
            }
        }
    }

    private void testMessages(MessageKind kind, String... messages) throws Exception {
        MessageTransport[] polyglotTransport = new MessageTransport[1];
        PolyglotMessageHandler[] polyglotMessageHandlerRef = new PolyglotMessageHandler[]{null};
        Engine engine = Engine.newBuilder().messageTransportInterceptor(new MessageTransport.Interceptor() {
            @Override
            public boolean handle(URI uri, boolean server) throws VetoException {
                Assert.assertSame(testUri, uri);
                return true;
            }

            @Override
            public MessageTransport.MessageHandler onOpen(URI uri, boolean server, MessageTransport transport) {
                Assert.assertSame(testUri, uri);
                sendMessage(kind, transport, messages[0]);
                polyglotTransport[0] = transport;
                PolyglotMessageHandler pmh = new PolyglotMessageHandler();
                polyglotMessageHandlerRef[0] = pmh;
                return pmh;
            }
        }).build();
        Instrument access = engine.getInstruments().get(INSTRUMENT_ID);
        TruffleInstrument.Env env = access.lookup(TruffleInstrument.Env.class);
        TruffleMessageTransportHandler messageTransportHandler = env.getMessageTransportHandler(testUri, true);
        TruffleMessageHandler truffleMessageHandler = new TruffleMessageHandler();
        truffleMessageHandler.setExpect(kind, messages[0]);
        TruffleMessageTransportHandler.Endpoint endpoint = messageTransportHandler.open(truffleMessageHandler);
        truffleMessageHandler.assertWasCalled();
        polyglotMessageHandlerRef[0].setExpect(kind, messages[1]);
        switch (kind) {
            case TEXT:
                endpoint.sendText(messages[1]);
                break;
            case BINARY:
                endpoint.sendBinary(ByteBuffer.wrap(messages[1].getBytes()));
                break;
            case PING:
                endpoint.sendPing(ByteBuffer.wrap(messages[1].getBytes()));
                break;
            case PONG:
                endpoint.sendPong(ByteBuffer.wrap(messages[1].getBytes()));
                break;
            case CLOSE:
                messageTransportHandler.close();
                break;
            default:
                throw new IllegalStateException("Unknown kind: " + kind);
        }
        if (kind != MessageKind.CLOSE) {
            polyglotMessageHandlerRef[0].assertWasCalled();
            // Send a next message back
            truffleMessageHandler.setExpect(kind, messages[2]);
            sendMessage(kind, polyglotTransport[0], messages[2]);
            truffleMessageHandler.assertWasCalled();
            // Close the transport
            truffleMessageHandler.setExpect(MessageKind.CLOSE, null);
            polyglotMessageHandlerRef[0].setExpect(MessageKind.CLOSE, null);
            messageTransportHandler.close();
            truffleMessageHandler.assertWasCalled();
            polyglotMessageHandlerRef[0].assertWasCalled();
        }
    }

    private static void sendMessage(MessageKind kind, MessageTransport transport, String message) {
        MessageTransport.Endpoint endpoint = transport.getDefaultEndpoint();
        switch (kind) {
            case TEXT:
                endpoint.sendText(message);
                break;
            case BINARY:
                endpoint.sendBinary(ByteBuffer.wrap(message.getBytes()));
                break;
            case PING:
                endpoint.sendPing(ByteBuffer.wrap(message.getBytes()));
                break;
            case PONG:
                endpoint.sendPong(ByteBuffer.wrap(message.getBytes()));
                break;
            case CLOSE:
                transport.close();
                break;
            default:
                throw new IllegalStateException("Unknown kind: " + kind);
        }
    }

    private enum MessageKind {
        TEXT,
        BINARY,
        PING,
        PONG,
        CLOSE
    }

    private static class MessageHandlerVerifier {

        private MessageKind expectedKind;
        private String expectedText;
        private int numCallbacks = 0;

        void setExpect(MessageKind kind, String text) {
            this.expectedKind = kind;
            this.expectedText = text;
        }

        void assertWasCalled() {
            Assert.assertEquals(1, numCallbacks);
            this.numCallbacks = 0;
        }

        public void onTextMessage(String text) {
            Assert.assertSame(MessageKind.TEXT, expectedKind);
            Assert.assertEquals(expectedText, text);
            numCallbacks++;
        }

        public void onBinaryMessage(ByteBuffer data) {
            Assert.assertSame(MessageKind.BINARY, expectedKind);
            assertData(data);
        }

        public void onPing(ByteBuffer data) {
            Assert.assertSame(MessageKind.PING, expectedKind);
            assertData(data);
        }

        public void onPong(ByteBuffer data) {
            Assert.assertSame(MessageKind.PONG, expectedKind);
            assertData(data);
        }

        public void onClose() {
            Assert.assertSame(MessageKind.CLOSE, expectedKind);
            Assert.assertNull(expectedText);
            numCallbacks++;
        }

        private void assertData(ByteBuffer data) {
            byte[] bytes = new byte[data.remaining()];
            data.get(bytes);
            Assert.assertArrayEquals(expectedText.getBytes(), bytes);
            numCallbacks++;
        }
    }

    private static class PolyglotMessageHandler extends MessageHandlerVerifier implements MessageTransport.MessageHandler {

    }

    private static class TruffleMessageHandler extends MessageHandlerVerifier implements TruffleMessageTransportHandler.MessageHandler {

    }

    @TruffleInstrument.Registration(id = INSTRUMENT_ID, name = INSTRUMENT_ID, services = TruffleInstrument.Env.class)
    public static class TestAccessInstruments extends TruffleInstrument {

        @Override
        protected void onCreate(final TruffleInstrument.Env env) {
            env.registerService(env);
        }
    }

}
