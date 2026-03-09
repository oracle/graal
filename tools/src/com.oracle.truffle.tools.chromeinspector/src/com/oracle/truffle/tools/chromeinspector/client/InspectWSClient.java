/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.tools.chromeinspector.client;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;

import com.oracle.truffle.tools.chromeinspector.InspectorExecutionContext;
import com.oracle.truffle.tools.chromeinspector.instrument.InspectorWSConnection;
import com.oracle.truffle.tools.chromeinspector.instrument.Token;
import com.oracle.truffle.tools.chromeinspector.server.ConnectionWatcher;
import com.oracle.truffle.tools.chromeinspector.server.InspectServerSession;
import com.oracle.truffle.tools.utils.java_websocket.client.WebSocketClient;
import com.oracle.truffle.tools.utils.java_websocket.handshake.ServerHandshake;
import org.graalvm.polyglot.io.MessageEndpoint;

/**
 * Web socket client that connects to a listening inspector client.
 */
public class InspectWSClient extends WebSocketClient implements InspectorWSConnection {

    private final String host;
    private final int port;
    private final InspectorExecutionContext executionContext;
    private final boolean debugBreak;
    private final ConnectionWatcher connectionWatcher;
    private InspectServerSession iss;

    private static URI getURI(InetSocketAddress isa, String wsspath) {
        try {
            return new URI("ws://" + isa.getHostString() + ":" + isa.getPort() + wsspath);
        } catch (URISyntaxException ex) {
            throw new IllegalStateException(ex);
        }
    }

    @SuppressWarnings("this-escape")
    public InspectWSClient(InetSocketAddress isa, String wsspath, InspectorExecutionContext executionContext, boolean debugBreak,
                    ConnectionWatcher connectionWatcher, PrintWriter info) throws IOException {
        super(getURI(isa, wsspath));
        this.host = isa.getHostString();
        this.port = isa.getPort();
        this.executionContext = executionContext;
        this.debugBreak = debugBreak;
        this.connectionWatcher = connectionWatcher;
        try {
            boolean success = connectBlocking();
            if (!success) {
                info.println("Could not attach to " + host + ":" + port);
                info.flush();
            }
        } catch (InterruptedException ex) {
            throw new IOException("Interrupted " + ex.getLocalizedMessage());
        }
    }

    @Override
    public int getPort() {
        return port;
    }

    @Override
    public void onOpen(ServerHandshake sh) {
        executionContext.logMessage("CLIENT ws connection opened at ", getURI());
        iss = InspectServerSession.create(executionContext, debugBreak, connectionWatcher, () -> close());
        connectionWatcher.notifyOpen();
        iss.open(new MessageEndpoint() {
            @Override
            public void sendText(String message) {
                executionContext.logMessage("SERVER: ", message);
                send(message);
            }

            @Override
            public void sendBinary(ByteBuffer data) throws IOException {
                throw new UnsupportedOperationException("Binary messages are not supported.");
            }

            @Override
            public void sendPing(ByteBuffer data) throws IOException {
            }

            @Override
            public void sendPong(ByteBuffer data) throws IOException {
            }

            @Override
            public void sendClose() throws IOException {
                close();
            }
        });
    }

    @Override
    public void onMessage(String message) {
        executionContext.logMessage("CLIENT: ", message);
        try {
            iss.sendText(message);
        } catch (IOException e) {
            executionContext.logException(e);
        }
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        executionContext.logMessage("SERVER closed ", reason);
        connectionWatcher.notifyClosing();
        if (!executionContext.canRun()) {
            // The connection was not successfull, resume the execution
            executionContext.doRunIfWaitingForDebugger();
        }
    }

    @Override
    public void onError(Exception excptn) {
        executionContext.logException("SERVER error ", excptn);
    }

    @Override
    public void consoleAPICall(Token token, String type, Object text) {
        iss.consoleAPICall(type, text);
    }

    @Override
    public void close(Token token) {
        close();
    }
}
