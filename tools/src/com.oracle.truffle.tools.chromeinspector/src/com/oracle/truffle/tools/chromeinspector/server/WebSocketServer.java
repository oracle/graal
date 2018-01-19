/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.PushbackInputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoWSD;

import org.json.JSONArray;
import org.json.JSONObject;

import com.oracle.truffle.tools.chromeinspector.TruffleDebugger;
import com.oracle.truffle.tools.chromeinspector.TruffleExecutionContext;
import com.oracle.truffle.tools.chromeinspector.TruffleProfiler;
import com.oracle.truffle.tools.chromeinspector.TruffleRuntime;
import com.oracle.truffle.tools.chromeinspector.domains.DebuggerDomain;
import com.oracle.truffle.tools.chromeinspector.domains.ProfilerDomain;
import com.oracle.truffle.tools.chromeinspector.domains.RuntimeDomain;

/**
 * Server of the
 * <a href="https://chromium.googlesource.com/v8/v8/+/master/src/inspector/js_protocol.json">Chrome
 * inspector protocol</a>.
 */
public final class WebSocketServer extends NanoWSD {

    private static final Map<String, TruffleExecutionContext> SESSIONS = new HashMap<>();
    private static final Map<String, Boolean> DEBUG_BRK = new HashMap<>();
    private static final Map<InetSocketAddress, WebSocketServer> SERVERS = new HashMap<>();

    private final String path;
    private final PrintStream log;

    private WebSocketServer(InetSocketAddress isa, String path, PrintStream log) {
        super(isa.getHostName(), isa.getPort());
        this.path = path;
        this.log = log;
        if (log != null) {
            log.println("New WebSocketServer at " + isa);
            log.flush();
        }
    }

    public static WebSocketServer get(InetSocketAddress isa, String path,
                    TruffleExecutionContext context, boolean debugBrk) throws IOException {
        synchronized (SESSIONS) {
            SESSIONS.put(path, context);
            DEBUG_BRK.put(path, debugBrk);
        }
        WebSocketServer wss;
        synchronized (SERVERS) {
            wss = SERVERS.get(isa);
            if (wss == null) {
                PrintStream traceLog = null;
                String traceLogFile = System.getProperty("chromeinspector.traceMessages");
                if (traceLogFile != null) {
                    if (Boolean.parseBoolean(traceLogFile)) {
                        traceLog = System.err;
                    } else if (!"false".equalsIgnoreCase(traceLogFile)) {
                        if ("tmp".equalsIgnoreCase(traceLogFile)) {
                            traceLog = new PrintStream(new FileOutputStream(File.createTempFile("ChromeInspectorProtocol", ".txt")));
                        } else {
                            traceLog = new PrintStream(new FileOutputStream(traceLogFile));
                        }
                    }
                }
                wss = new WebSocketServer(isa, path, traceLog);
                wss.start(Integer.MAX_VALUE);
            }
        }
        return wss;
    }

    @Override
    public Response serveHttp(IHTTPSession session) {
        if (Method.GET == session.getMethod()) {
            String uri = session.getUri();
            String responseJson = null;
            if ("/json/version".equals(uri)) {
                JSONObject version = new JSONObject();
                version.put("Browser", "GraalVM");
                version.put("Protocol-Version", "1.2");
                responseJson = version.toString();
            }
            if ("/json".equals(uri)) {
                JSONObject info = new JSONObject();
                info.put("description", "GraalVM");
                info.put("faviconUrl", "https://assets-cdn.github.com/images/icons/emoji/unicode/1f680.png");
                String ws = getHostname() + ":" + getListeningPort() + path;
                info.put("devtoolsFrontendUrl", "chrome-devtools://devtools/bundled/inspector.html?experiments=true&v8only=true&ws=" + ws);
                info.put("id", path.substring(1));
                info.put("title", "GraalVM");
                info.put("type", "node");
                info.put("webSocketDebuggerUrl", "ws://" + ws);
                JSONArray json = new JSONArray();
                json.put(info);
                responseJson = json.toString();
            }
            if (log != null) {
                log.println("serverHttp(" + uri + "): response = '" + responseJson + "'");
            }
            if (responseJson != null) {
                Response response = NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.OK,
                                "application/json; charset=UTF-8",
                                responseJson);
                response.addHeader("Cache-Control", "no-cache");
                return response;
            }
        }
        return super.serveHttp(session);
    }

    @Override
    protected NanoWSD.WebSocket openWebSocket(NanoHTTPD.IHTTPSession handshake) {
        String descriptor = handshake.getUri();
        TruffleExecutionContext context = SESSIONS.get(descriptor);
        if (log != null) {
            log.println("CLIENT ws connection opened, resource = " + descriptor + ", context = " + context);
            log.flush();
        }
        if (context != null) {
            boolean debugBreak = DEBUG_BRK.get(descriptor);
            RuntimeDomain runtime = new TruffleRuntime(context);
            DebuggerDomain debugger = new TruffleDebugger(context, debugBreak);
            ProfilerDomain profiler = new TruffleProfiler(context);
            InspectServerSession iss = new InspectServerSession(runtime, debugger, profiler, context);
            return new InspectWebSocket(handshake, iss, log);
        } else {
            return new ClosedWebSocket(handshake);
        }
    }

    @Override
    protected ClientHandler createClientHandler(Socket finalAccept, InputStream inputStream) {
        PushbackInputStream pbInputStream = new PushbackInputStream(inputStream, 3);
        try {
            byte[] buf = new byte[3];
            pbInputStream.read(buf);
            String text = new String(buf);
            pbInputStream.unread(buf);
            if (!"GET".equals(text)) {
                try (OutputStream outputStream = finalAccept.getOutputStream()) {
                    ContentType contentType = new ContentType(NanoHTTPD.MIME_PLAINTEXT);
                    PrintWriter pw = new PrintWriter(new BufferedWriter(new OutputStreamWriter(outputStream, contentType.getEncoding())), false);
                    pw.append("HTTP/1.1 ").append(Response.Status.BAD_REQUEST.getDescription()).append(" \r\n");
                    String mimeType = contentType.getContentTypeHeader();
                    if (mimeType != null) {
                        pw.append("Content-Type: ").append(mimeType).append("\r\n");
                    }
                    pw.append("\r\n");
                    pw.append("WebSockets request was expected");
                    pw.flush();
                }
                return null;
            }
        } catch (IOException ex) {
        }
        return new ClientHandler(pbInputStream, finalAccept);
    }

    private class InspectWebSocket extends NanoWSD.WebSocket {

        private final InspectServerSession iss;
        private final PrintStream log;

        InspectWebSocket(NanoHTTPD.IHTTPSession handshake, InspectServerSession iss,
                        PrintStream log) {
            super(handshake);
            this.iss = iss;
            this.log = log;
        }

        @Override
        public void onOpen() {
            if (log != null) {
                log.println("CLIENT web socket connection opened.");
                log.flush();
            }
            iss.setMessageListener(new InspectServerSession.MessageListener() {
                @Override
                public void sendMessage(String message) {
                    if (log != null) {
                        log.println("SERVER: " + message);
                        log.flush();
                    }
                    try {
                        send(message);
                    } catch (IOException ex) {
                        if (log != null) {
                            ex.printStackTrace(log);
                            log.flush();
                        }
                    }
                }
            });
        }

        @Override
        public void onClose(NanoWSD.WebSocketFrame.CloseCode code, String reason, boolean initiatedByRemote) {
            if (log != null) {
                log.println("CLIENT web socket connection closed.");
                log.flush();
            }
            iss.dispose();
        }

        @Override
        public void onMessage(NanoWSD.WebSocketFrame frame) {
            String message = frame.getTextPayload();
            if (log != null) {
                log.println("CLIENT: " + message);
                log.flush();
            }
            iss.onMessage(message);
        }

        @Override
        protected void onPong(NanoWSD.WebSocketFrame pong) {
            if (log != null) {
                log.println("CLIENT PONG: " + pong.toString());
                log.flush();
            }
        }

        @Override
        protected void onException(IOException exception) {
            if (log != null) {
                exception.printStackTrace(log);
                log.flush();
            }
        }

    }

    private class ClosedWebSocket extends NanoWSD.WebSocket {

        ClosedWebSocket(NanoHTTPD.IHTTPSession handshakeRequest) {
            super(handshakeRequest);
            try {
                close(WebSocketFrame.CloseCode.UnsupportedData, "Bad path.", false);
            } catch (IOException ioex) {
            }
        }

        @Override
        protected void onOpen() {
        }

        @Override
        protected void onClose(WebSocketFrame.CloseCode cc, String string, boolean bln) {
        }

        @Override
        protected void onMessage(WebSocketFrame wsf) {
        }

        @Override
        protected void onPong(WebSocketFrame wsf) {
        }

        @Override
        protected void onException(IOException ioe) {
        }

    }
}
