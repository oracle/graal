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
package org.graalvm.polyglot.io;

import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;

/**
 * Represents a transport of message communication with a peer. This service provides the ability to
 * virtualize message communication with a peer at an URL.
 * <p>
 * Use
 * {@link org.graalvm.polyglot.Engine.Builder#messageTransportInterceptor(MessageTransport.Interceptor)}
 * to register message handlers.
 *
 * @since 1.0
 */
public interface MessageTransport {

    /**
     * Get a default endpoint implementation.
     *
     * @since 1.0
     */
    Endpoint getDefaultEndpoint();

    // getAsynchronousEndpoint() may come in the future

    /**
     * Close this message transport. No messages can be sent to the endpoint after close.
     *
     * @since 1.0
     */
    void close();

    /**
     * Allows to take over transport of message communication initiated by a GraalVM instrument.
     *
     * @since 1.0
     */
    public interface Interceptor {

        /**
         * Decide whether to handle connection to the given URI.
         *
         * @param uri the connection URI
         * @param server true if a server endpoint is going to be created at that URI, false for
         *            client connections
         * @return true to take over the connection, then
         *         {@link #onOpen(URI, boolean, MessageTransport)} will be called with the URI when
         *         the connection is opened, false to ignore and let the initiator to perform the
         *         connection themselves. To veto the connection, throw {@link VetoException}.
         * @throws Interceptor.VetoException to veto connection to the URL.
         * @since 1.0
         */
        boolean handle(URI uri, boolean server) throws VetoException;

        /**
         * Called when a connection to an URI is to be established. This method is called only with
         * URI for which {@link #handle(URI, boolean)} returned <code>true</code>.
         *
         * @param uri the connection URI
         * @param server true if a server endpoint is going to be created at that URI, false for
         *            client connections
         * @param transport the transport representation
         * @return an implementation of {@link MessageHandler} call back.
         * @since 1.0
         */
        MessageHandler onOpen(URI uri, boolean server, MessageTransport transport) throws IOException;

        /**
         * Thrown when a transport connection is vetoed. The initiator of the connection is obliged
         * to abandon it when this exception is thrown.
         *
         * @since 1.0
         */
        final class VetoException extends Exception {

            /**
             * Create a new VetoException.
             *
             * @since 1.0
             */
            public VetoException(String message) {
                super(message);
            }

            private static final long serialVersionUID = 3493487569356378902L;

        }
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
     * Call back of messages received from the remote endpoint.
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
