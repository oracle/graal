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
package com.oracle.truffle.api;

import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

import org.graalvm.polyglot.io.MessageTransport;

/**
 * Handler of a message transport provided by {@link MessageTransport}. It abstracts the message
 * communication with a peer at an URL. Use
 * <code>TruffleInstrument.Env.getMessageTransportHandler()</code> to obtain an instance of this
 * class.
 *
 * @since 1.0
 */
public final class TruffleMessageTransportHandler {

    private final URI uri;
    private final boolean server;
    private final MessageTransport.Interceptor messageInterceptor;
    private MessageTransport transport;

    TruffleMessageTransportHandler(URI uri, boolean server, MessageTransport.Interceptor messageInterceptor) {
        this.uri = uri;
        this.server = server;
        this.messageInterceptor = messageInterceptor;
    }

    /**
     * Open connection to the {@link Endpoint}.
     *
     * @param messageHandler consumer of incoming messages from the URI
     * @return an endpoint to send the messages to
     * @throws IOException when a communication issue occurs
     * @since 1.0
     */
    public Endpoint open(MessageHandler messageHandler) throws IOException {
        if (this.transport != null) {
            throw new IOException("Opened already.");
        }
        MessageTransport.MessageHandler[] messageTransportHandlerRef = new MessageTransport.MessageHandler[]{null};
        this.transport = new MessageTransport() {

            private final AtomicBoolean closed = new AtomicBoolean(false);

            @Override
            public MessageTransport.Endpoint getDefaultEndpoint() {
                return new MessageTransport.Endpoint() {
                    @Override
                    public void sendText(String text) {
                        messageHandler.onTextMessage(text);
                    }

                    @Override
                    public void sendBinary(ByteBuffer data) {
                        messageHandler.onBinaryMessage(data);
                    }

                    @Override
                    public void sendPing(ByteBuffer data) {
                        messageHandler.onPing(data);
                    }

                    @Override
                    public void sendPong(ByteBuffer data) {
                        messageHandler.onPong(data);
                    }
                };
            }

            @Override
            public void close() {
                if (closed.getAndSet(true)) {
                    return;
                }
                messageHandler.onClose();
                if (messageTransportHandlerRef[0] != null) {
                    messageTransportHandlerRef[0].onClose();
                }
            }
        };
        MessageTransport.MessageHandler messageTransportHandler = messageInterceptor.onOpen(uri, server, transport);
        messageTransportHandlerRef[0] = messageTransportHandler;
        return new Endpoint() {
            @Override
            public void sendText(String text) {
                messageTransportHandler.onTextMessage(text);
            }

            @Override
            public void sendBinary(ByteBuffer data) {
                messageTransportHandler.onBinaryMessage(data);
            }

            @Override
            public void sendPing(ByteBuffer data) {
                messageTransportHandler.onPing(data);
            }

            @Override
            public void sendPong(ByteBuffer data) {
                messageTransportHandler.onPong(data);
            }
        };
    }

    /**
     * Close the message transport.
     *
     * @since 1.0
     */
    public void close() {
        transport.close();
    }

    /**
     * A remote endpoint.
     *
     * @since 1.0
     */
    public interface Endpoint {

        /**
         * Send a text message.
         *
         * @since 1.0
         */
        void sendText(String text);

        /**
         * Send a binary message.
         *
         * @since 1.0
         */
        void sendBinary(ByteBuffer data);

        /**
         * Send a ping request.
         *
         * @since 1.0
         */
        void sendPing(ByteBuffer data);

        /**
         * Send a pong reply as a response to ping.
         *
         * @since 1.0
         */
        void sendPong(ByteBuffer data);

    }

    /**
     * Call back of messages received from the remote peer.
     *
     * @since 1.0
     */
    public interface MessageHandler {

        /**
         * Called when a text message is received.
         *
         * @since 1.0
         */
        void onTextMessage(String text);

        /**
         * Called when a binary message is received.
         *
         * @since 1.0
         */
        void onBinaryMessage(ByteBuffer data);

        /**
         * Called when a ping is received.
         *
         * @since 1.0
         */
        void onPing(ByteBuffer data);

        /**
         * Called when a pong is received.
         *
         * @since 1.0
         */
        void onPong(ByteBuffer data);

        /**
         * Called when the connection is closed by the remote peer.
         *
         * @since 1.0
         */
        void onClose();
    }

}
