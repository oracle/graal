/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.tools.chromeinspector.server;

import java.io.IOException;
import java.nio.ByteBuffer;

import com.oracle.truffle.tools.chromeinspector.instrument.Token;
import org.graalvm.polyglot.io.MessageEndpoint;
import org.graalvm.polyglot.io.MessageTransport;

import com.oracle.truffle.tools.chromeinspector.instrument.InspectorWSConnection;

/**
 * Inspector server that delegates to {@link MessageTransport}.
 */
public final class WSInterceptorServer implements InspectorWSConnection, MessageEndpoint {

    private final int port;
    private final Token token;
    private final ConnectionWatcher connectionWatcher;
    private InspectServerSession iss;
    private MessageEndpoint inspectEndpoint;

    public WSInterceptorServer(int port, Token token, InspectServerSession iss, ConnectionWatcher connectionWatcher) {
        this.port = port;
        this.token = token;
        this.connectionWatcher = connectionWatcher;
        this.iss = iss;
        iss.setMessageListener(this);
    }

    public void newSession(InspectServerSession newIss) {
        this.iss.setMessageListener(null);
        this.iss = newIss;
        this.iss.setMessageListener(this);
    }

    public void opened(MessageEndpoint endpoint) {
        this.inspectEndpoint = endpoint;
        iss.setMessageListener(this);
        this.connectionWatcher.notifyOpen();
    }

    @Override
    public int getPort() {
        return port;
    }

    @Override
    public void close(Token tokenToClose) throws IOException {
        iss.setMessageListener(null);
        if (inspectEndpoint != null) {
            if (tokenToClose.equals(this.token)) {
                inspectEndpoint.sendClose();
            }
        }
    }

    @Override
    public void dispose() {
        iss.dispose();
    }

    @Override
    public void consoleAPICall(Token tokenToCall, String type, Object text) {
        iss.consoleAPICall(type, text);
    }

    @Override
    public void sendText(String message) throws IOException {
        connectionWatcher.waitForOpen();
        inspectEndpoint.sendText(message);
    }

    @Override
    public void sendBinary(ByteBuffer data) throws IOException {
        inspectEndpoint.sendBinary(data);
    }

    @Override
    public void sendPing(ByteBuffer data) throws IOException {
        inspectEndpoint.sendPing(data);
    }

    @Override
    public void sendPong(ByteBuffer data) throws IOException {
        inspectEndpoint.sendPong(data);
    }

    @Override
    public void sendClose() throws IOException {
        if (inspectEndpoint != null) {
            inspectEndpoint.sendClose();
        }
    }

}
