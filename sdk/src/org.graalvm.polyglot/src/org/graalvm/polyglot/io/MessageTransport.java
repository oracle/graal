/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.polyglot.io;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;

import org.graalvm.polyglot.Engine;

/**
 * Allows to take over transport of message communication initiated by an instrument. Implement this
 * interface to provide a transport of message communication. When an instrument is about to create
 * a server endpoint, it calls the {@link MessageTransport#open(URI, MessageEndpoint)} method with
 * the server URI.
 * <p>
 * Usage example: {@link MessageTransportSnippets#example}
 *
 * @see org.graalvm.polyglot.Engine.Builder#serverTransport(MessageTransport)
 * @see org.graalvm.polyglot.Context.Builder#serverTransport(MessageTransport)
 * @since 19.0
 */
public interface MessageTransport {

    /**
     * Called when a connection to an URI is to be established. The virtualized connection is either
     * opened and an endpoint call back is returned, or the connection is not virtualized in which
     * case <code>null</code> is returned.
     * <p>
     * This method can be called concurrently from multiple threads. However, the
     * {@link MessageEndpoint} ought to be called on one thread at a time, unless you're sure that
     * the particular implementation can handle concurrent calls. The same holds true for the
     * returned endpoint, it's called synchronously.
     *
     * @param uri the connection URI
     * @param peerEndpoint the peer endpoint representation
     * @return an implementation of {@link MessageEndpoint} call back, or <code>null</code>.
     * @throws VetoException to veto connection to the URL.
     * @since 19.0
     */
    MessageEndpoint open(URI uri, MessageEndpoint peerEndpoint) throws IOException, VetoException;

    /**
     * Thrown when a transport connection is vetoed. The initiator of the connection is obliged to
     * abandon it when this exception is thrown.
     *
     * @since 19.0
     */
    final class VetoException extends Exception {

        /**
         * Create a new VetoException.
         *
         * @since 19.0
         */
        public VetoException(String message) {
            super(message);
        }

        private static final long serialVersionUID = 3493487569356378902L;

    }
}

@SuppressWarnings("all")
class MessageTransportSnippets {

    final URI routedURI = URI.create("");
    final String denyHost = "";

    private InputStream getRouterInputStream() {
        return null;
    }

    private OutputStream getRouterOutputStream() {
        return null;
    }

    public void example() {
        // @formatter:off
        // BEGIN: MessageTransportSnippets#example
        class RoutedServer implements MessageEndpoint {

            private final MessageEndpoint remoteEndpoint;
            private final OutputStream routerOut = getRouterOutputStream();
            private final WritableByteChannel routerOutChannel;

            RoutedServer(MessageEndpoint remoteEndpoint) {
                this.remoteEndpoint = remoteEndpoint;
                this.routerOutChannel = Channels.newChannel(routerOut);
                new Thread(() -> {
                        try {
                            runInputLoop();
                        } catch (IOException ex) {
                        }
                }).start();
            }

            @Override
            public void sendText(String text) throws IOException {
                routerOut.write(text.getBytes());
                routerOut.flush();
            }

            @Override
            public void sendBinary(ByteBuffer data) throws IOException {
                routerOutChannel.write(data);
                routerOut.flush();
            }

            @Override
            public void sendPing(ByteBuffer data) throws IOException {
                remoteEndpoint.sendPong(data);
            }

            @Override
            public void sendPong(ByteBuffer data) throws IOException {
                // Did we send ping?
            }

            @Override
            public void sendClose() throws IOException {
                routerOut.close();
            }

            private void runInputLoop() throws IOException {
                try (InputStream routerIn = getRouterInputStream()) {
                    byte[] buf = new byte[1024];
                    ByteBuffer bb = ByteBuffer.wrap(buf);
                    int l;
                    while ((l = routerIn.read(buf)) > 0) {
                        bb.limit(l);
                        remoteEndpoint.sendBinary(bb);
                        bb.rewind();
                    }
                } finally {
                    remoteEndpoint.sendClose();
                }
            }
        }

        Engine.newBuilder().serverTransport(
            (uri, peerEndpoint) -> {
                if (denyHost.equals(uri.getHost())) {
                    throw new MessageTransport.VetoException("Denied access.");
                } else if (routedURI.equals(uri)) {
                    return new RoutedServer(peerEndpoint);
                } else {
                    // Permit instruments to setup the servers themselves
                    return null;
                }
            }
        ).build();
        // END: MessageTransportSnippets#example
        // @formatter:on
    }
}
