/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.api.test.polyglot;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.io.IOException;

import org.junit.Assert;
import org.junit.Test;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.io.MessageEndpoint;
import org.graalvm.polyglot.io.MessageTransport;

public class MessageTransportTest extends AbstractPolyglotTest {

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
        setupEnv();
        URI[] uris = new URI[]{testUri, new URI("ws", "a", "b"), new URI("file", "/a", "")};
        for (URI uri : uris) {
            Assert.assertNull(instrumentEnv.startServer(uri, new MessageHandlerVerifier()));
        }
    }

    @Test
    public void vetoedTransportTest() throws Exception {
        Engine engine = Engine.newBuilder().serverTransport(new MessageTransport() {
            @Override
            public MessageEndpoint open(URI uri, MessageEndpoint peerEndpoint) throws IOException, MessageTransport.VetoException {
                throw new VetoException("No access to " + testUri);
            }
        }).build();
        setupEnv(Context.newBuilder().engine(engine).build());
        try {
            instrumentEnv.startServer(testUri, new MessageHandlerVerifier());
            Assert.fail();
        } catch (MessageTransport.VetoException ex) {
            Assert.assertEquals("No access to " + testUri, ex.getMessage());
        }
    }

    @Test
    public void noTransportTest() throws Exception {
        Engine engine = Engine.newBuilder().serverTransport(new MessageTransport() {
            @Override
            public MessageEndpoint open(URI uri, MessageEndpoint peerEndpoint) throws IOException, MessageTransport.VetoException {
                return null;
            }
        }).build();
        setupEnv(Context.newBuilder().engine(engine).build());
        Assert.assertNull(instrumentEnv.startServer(testUri, new MessageHandlerVerifier()));
    }

    @Test(expected = IllegalStateException.class)
    public void sharedEngineFails() {
        Context.newBuilder().serverTransport((uri, peerEndpoint) -> null).engine(Engine.create()).build();
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
        MessageEndpoint[] embedderPeerEndpoint = new MessageEndpoint[1];
        MessageHandlerVerifier[] embedderEndpoint = new MessageHandlerVerifier[]{null};
        Context cntx = Context.newBuilder().serverTransport(new MessageTransport() {
            @Override
            public MessageEndpoint open(URI uri, MessageEndpoint peerEndpoint) throws IOException, MessageTransport.VetoException {
                Assert.assertSame(testUri, uri);
                // The first message goes from embedder to the instrument
                sendMessage(kind, peerEndpoint, messages[0]);
                embedderPeerEndpoint[0] = peerEndpoint;
                MessageHandlerVerifier pmh = new MessageHandlerVerifier();
                embedderEndpoint[0] = pmh;
                return pmh;
            }
        }).build();
        setupEnv(cntx);
        MessageHandlerVerifier instrumentEndpoint = new MessageHandlerVerifier();
        // Instrument has received the first message from the embedder
        instrumentEndpoint.setExpect(kind, messages[0]);
        MessageEndpoint instrumentPeerEndpoint = instrumentEnv.startServer(testUri, instrumentEndpoint);
        instrumentEndpoint.assertWasCalled();
        // Instrument sends the second message to embedder
        embedderEndpoint[0].setExpect(kind, messages[1]);
        sendMessage(kind, instrumentPeerEndpoint, messages[1]);
        if (kind != MessageKind.CLOSE) {
            embedderEndpoint[0].assertWasCalled();
            // Send a next message back from embedder to the instrument
            instrumentEndpoint.setExpect(kind, messages[2]);
            sendMessage(kind, embedderPeerEndpoint[0], messages[2]);
            instrumentEndpoint.assertWasCalled();
            // Close the transport
            embedderEndpoint[0].setExpect(MessageKind.CLOSE, null);
            instrumentPeerEndpoint.sendClose();
            embedderEndpoint[0].assertWasCalled();
            instrumentEndpoint.setExpect(MessageKind.CLOSE, null);
            embedderPeerEndpoint[0].sendClose();
            instrumentEndpoint.assertWasCalled();
        }
        // Assert that implementation class of embedder is not accessible to the instrument
        // and vice versa:
        Assert.assertNotEquals(embedderEndpoint[0], instrumentPeerEndpoint);
        Assert.assertNotEquals(instrumentEndpoint, embedderPeerEndpoint[0]);
    }

    private static void sendMessage(MessageKind kind, MessageEndpoint endpoint, String message) throws IOException {
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
                endpoint.sendClose();
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

    private static class MessageHandlerVerifier implements MessageEndpoint {

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

        @Override
        public void sendText(String text) {
            Assert.assertSame(MessageKind.TEXT, expectedKind);
            Assert.assertEquals(expectedText, text);
            numCallbacks++;
        }

        @Override
        public void sendBinary(ByteBuffer data) {
            Assert.assertSame(MessageKind.BINARY, expectedKind);
            assertData(data);
        }

        @Override
        public void sendPing(ByteBuffer data) {
            Assert.assertSame(MessageKind.PING, expectedKind);
            assertData(data);
        }

        @Override
        public void sendPong(ByteBuffer data) {
            Assert.assertSame(MessageKind.PONG, expectedKind);
            assertData(data);
        }

        @Override
        public void sendClose() {
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

}
