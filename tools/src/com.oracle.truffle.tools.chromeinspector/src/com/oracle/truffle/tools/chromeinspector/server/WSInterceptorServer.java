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
package com.oracle.truffle.tools.chromeinspector.server;

import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;

import org.graalvm.polyglot.io.MessageTransport;

import com.oracle.truffle.api.TruffleMessageTransportHandler;
import com.oracle.truffle.tools.chromeinspector.TruffleDebugger;
import com.oracle.truffle.tools.chromeinspector.TruffleExecutionContext;
import com.oracle.truffle.tools.chromeinspector.TruffleProfiler;
import com.oracle.truffle.tools.chromeinspector.TruffleRuntime;
import com.oracle.truffle.tools.chromeinspector.domains.DebuggerDomain;
import com.oracle.truffle.tools.chromeinspector.domains.ProfilerDomain;
import com.oracle.truffle.tools.chromeinspector.domains.RuntimeDomain;

/**
 * Inspector server that delegates to {@link MessageTransport}.
 */
public final class WSInterceptorServer implements InspectorServer, InspectServerSession.MessageListener {

    private final URI uri;
    private final TruffleMessageTransportHandler messageHandler;
    private final InspectorMessageHandler inspectMessageHandler;
    private final TruffleMessageTransportHandler.Endpoint inspectEndpoint;
    private final InspectServerSession inspectSession;
    private final ConnectionWatcher connectionWatcher;

    public WSInterceptorServer(URI uri, TruffleMessageTransportHandler messageHandler, TruffleExecutionContext context, boolean debugBreak, ConnectionWatcher connectionWatcher) throws IOException {
        this.uri = uri;
        this.messageHandler = messageHandler;
        this.connectionWatcher = connectionWatcher;
        RuntimeDomain runtime = new TruffleRuntime(context);
        DebuggerDomain debugger = new TruffleDebugger(context, debugBreak);
        ProfilerDomain profiler = new TruffleProfiler(context, connectionWatcher);
        inspectSession = new InspectServerSession(runtime, debugger, profiler, context);
        this.inspectMessageHandler = new InspectorMessageHandler(inspectSession);
        inspectSession.setMessageListener(this);
        inspectEndpoint = messageHandler.open(this.inspectMessageHandler);
        connectionWatcher.notifyOpen();
    }

    @Override
    public int getListeningPort() {
        return uri.getPort();
    }

    @Override
    public void close(String path) {
        if (path.equals(uri.getPath())) {
            messageHandler.close();
        }
    }

    @Override
    public void sendMessage(String message) {
        connectionWatcher.waitForOpen();
        inspectEndpoint.sendText(message);
    }

    private static final class InspectorMessageHandler implements TruffleMessageTransportHandler.MessageHandler {

        private final InspectServerSession inspectSession;

        InspectorMessageHandler(InspectServerSession inspectSession) {
            this.inspectSession = inspectSession;
        }

        @Override
        public void onTextMessage(String text) {
            inspectSession.onMessage(text);
        }

        @Override
        public void onBinaryMessage(ByteBuffer data) {
            throw new UnsupportedOperationException("Binary messages are not supported.");
        }

        @Override
        public void onPing(ByteBuffer data) {
        }

        @Override
        public void onPong(ByteBuffer data) {
        }

        @Override
        public void onClose() {
            inspectSession.dispose();
        }
    }
}
