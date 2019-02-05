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
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.PushbackInputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.ConcurrentHashMap;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLServerSocketFactory;

import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoWSD;

import org.graalvm.polyglot.io.MessageEndpoint;

import com.oracle.truffle.api.TruffleOptions;
import com.oracle.truffle.tools.utils.json.JSONArray;
import com.oracle.truffle.tools.utils.json.JSONObject;

import com.oracle.truffle.tools.chromeinspector.InspectorExecutionContext;
import com.oracle.truffle.tools.chromeinspector.instrument.KeyStoreOptions;
import com.oracle.truffle.tools.chromeinspector.instrument.InspectorWSConnection;

/**
 * Server of the
 * <a href="https://chromium.googlesource.com/v8/v8/+/master/src/inspector/js_protocol.json">Chrome
 * inspector protocol</a>.
 */
public final class WebSocketServer extends NanoWSD implements InspectorWSConnection {

    private static final Map<InetSocketAddress, WebSocketServer> SERVERS = new HashMap<>();

    private final int port;
    private final Map<String, ServerPathSession> sessions = new ConcurrentHashMap<>();
    private final PrintStream log;

    private WebSocketServer(InetSocketAddress isa, PrintStream log) {
        super(isa.getHostName(), isa.getPort());
        this.port = isa.getPort();
        this.log = log;
        if (log != null) {
            log.println("New WebSocketServer at " + isa);
            log.flush();
        }
    }

    public static WebSocketServer get(InetSocketAddress isa, String path, InspectorExecutionContext context, boolean debugBrk,
                    boolean secure, KeyStoreOptions keyStoreOptions, ConnectionWatcher connectionWatcher,
                    InspectServerSession initialSession) throws IOException {
        WebSocketServer wss;
        boolean startServer = false;
        synchronized (SERVERS) {
            wss = SERVERS.get(isa);
            if (wss == null) {
                wss = new WebSocketServer(isa, context.getLogger());
                if (secure) {
                    if (TruffleOptions.AOT) {
                        throw new IOException("Secure connection is not available in the native-image yet.");
                    } else {
                        wss.makeSecure(createSSLFactory(keyStoreOptions), null);
                    }
                }
                startServer = true;
                SERVERS.put(isa, wss);
            }
        }
        wss.sessions.put(path, new ServerPathSession(context, initialSession, debugBrk, connectionWatcher));
        if (startServer) {
            wss.start(Integer.MAX_VALUE);
        }
        return wss;
    }

    private static SSLServerSocketFactory createSSLFactory(KeyStoreOptions keyStoreOptions) throws IOException {
        String keyStoreFile = keyStoreOptions.getKeyStore();
        if (keyStoreFile != null) {
            try {
                String filePasswordProperty = keyStoreOptions.getKeyStorePassword();
                // obtaining password for unlock keystore
                char[] filePassword = filePasswordProperty == null ? "".toCharArray() : filePasswordProperty.toCharArray();
                String keystoreType = keyStoreOptions.getKeyStoreType();
                if (keystoreType == null) {
                    keystoreType = KeyStore.getDefaultType();
                }
                KeyStore keystore = KeyStore.getInstance(keystoreType);
                File keyFile = new File(keyStoreFile);
                keystore.load(new FileInputStream(keyFile), filePassword);
                String keyRecoverPasswordProperty = keyStoreOptions.getKeyPassword();
                char[] keyRecoverPassword = keyRecoverPasswordProperty == null ? filePassword : keyRecoverPasswordProperty.toCharArray();
                final KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
                kmf.init(keystore, keyRecoverPassword);
                return NanoHTTPD.makeSSLSocketFactory(keystore, kmf);
            } catch (KeyStoreException | NoSuchAlgorithmException | CertificateException | UnrecoverableKeyException ex) {
                throw new IOException(ex);
            }
        } else {
            throw new IOException("Use options to specify the keystore");
        }
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
                JSONArray json = new JSONArray();
                for (String path : sessions.keySet()) {
                    JSONObject info = new JSONObject();
                    info.put("description", "GraalVM");
                    info.put("faviconUrl", "https://assets-cdn.github.com/images/icons/emoji/unicode/1f680.png");
                    String ws = getHostname() + ":" + getListeningPort() + path;
                    info.put("devtoolsFrontendUrl", "chrome-devtools://devtools/bundled/js_app.html?ws=" + ws);
                    info.put("id", path.substring(1));
                    info.put("title", "GraalVM");
                    info.put("type", "node");
                    info.put("webSocketDebuggerUrl", "ws://" + ws);
                    json.put(info);
                }
                responseJson = json.toString();
            }
            if (log != null) {
                log.println("serverHttp(" + uri + "): response = '" + responseJson + "'");
            }
            if (responseJson != null) {
                Response response = NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.OK,
                                "application/json; charset=UTF-8",
                                responseJson);
                response.addHeader("Cache-Control", "no-cache,no-store,must-revalidate");
                response.addHeader("Pragma", "no-cache");
                response.addHeader("X-Content-Type-Options", "nosniff");
                return response;
            }
        }
        return super.serveHttp(session);
    }

    @Override
    protected NanoWSD.WebSocket openWebSocket(NanoHTTPD.IHTTPSession handshake) {
        String descriptor = handshake.getUri();
        ServerPathSession session;
        session = sessions.get(descriptor);
        if (log != null) {
            log.println("CLIENT ws connection opened, resource = " + descriptor + ", context = " + session);
            log.flush();
        }
        if (session != null) {
            InspectServerSession iss = session.getServerSession();
            if (iss == null) {
                // Do the initial break for the first time only, do not break on reconnect
                boolean debugBreak = Boolean.TRUE.equals(session.getDebugBrkAndReset());
                iss = InspectServerSession.create(session.getContext(), debugBreak, session.getConnectionWatcher());
            }
            InspectWebSocket iws = new InspectWebSocket(handshake, iss, session.getConnectionWatcher(), log);
            session.activeWS = iws;
            return iws;
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

    @Override
    public int getPort() {
        int p = getListeningPort();
        if (p == -1) {
            p = this.port;
        }
        return p;
    }

    /**
     * Close the web socket server on the specific path. No web socket connection is active on the
     * path already, this is called after the {@link ConnectionWatcher#waitForClose()} is done.
     */
    @Override
    public void close(String wspath) throws IOException {
        ServerPathSession sps = sessions.remove(wspath);
        if (sps != null) {
            InspectWebSocket iws = sps.activeWS;
            if (iws != null) {
                iws.close(WebSocketFrame.CloseCode.GoingAway, "", false);
            }
        }
        if (sessions.isEmpty()) {
            stop();
        }
    }

    @Override
    public void consoleAPICall(String wsspath, String type, Object text) {
        ServerPathSession sps = sessions.get(wsspath);
        if (sps != null) {
            InspectWebSocket iws = sps.activeWS;
            if (iws != null) {
                iws.iss.consoleAPICall(type, text);
            }
        }
    }

    @Override
    public void stop() {
        super.stop();
        synchronized (SERVERS) {
            Iterator<Map.Entry<InetSocketAddress, WebSocketServer>> entries = SERVERS.entrySet().iterator();
            while (entries.hasNext()) {
                if (entries.next().getValue() == this) {
                    entries.remove();
                    break;
                }
            }
        }
    }

    private static class ServerPathSession {

        private final InspectorExecutionContext context;
        private final AtomicReference<InspectServerSession> serverSession;
        private final AtomicBoolean debugBrk;
        private final ConnectionWatcher connectionWatcher;
        volatile InspectWebSocket activeWS;

        ServerPathSession(InspectorExecutionContext context, InspectServerSession serverSession, boolean debugBrk, ConnectionWatcher connectionWatcher) {
            this.context = context;
            this.serverSession = new AtomicReference<>(serverSession);
            this.debugBrk = new AtomicBoolean(debugBrk);
            this.connectionWatcher = connectionWatcher;
        }

        InspectorExecutionContext getContext() {
            return context;
        }

        InspectServerSession getServerSession() {
            return serverSession.getAndSet(null);
        }

        boolean getDebugBrkAndReset() {
            return debugBrk.getAndSet(false);
        }

        ConnectionWatcher getConnectionWatcher() {
            return connectionWatcher;
        }
    }

    private class InspectWebSocket extends NanoWSD.WebSocket {

        private final String descriptor;
        private final InspectServerSession iss;
        private final ConnectionWatcher connectionWatcher;
        private final PrintStream log;

        InspectWebSocket(NanoHTTPD.IHTTPSession handshake, InspectServerSession iss,
                        ConnectionWatcher connectionWatcher, PrintStream log) {
            super(handshake);
            this.descriptor = handshake.getUri();
            this.iss = iss;
            this.connectionWatcher = connectionWatcher;
            this.log = log;
        }

        @Override
        public void onOpen() {
            if (log != null) {
                log.println("CLIENT web socket connection opened.");
                log.flush();
            }
            connectionWatcher.notifyOpen();
            iss.setMessageListener(new MessageEndpoint() {
                @Override
                public void sendText(String message) throws IOException {
                    if (log != null) {
                        log.println("SERVER: " + message);
                        log.flush();
                    }
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
                    close(WebSocketFrame.CloseCode.NormalClosure, "", true);
                }
            });
        }

        @Override
        public void onClose(NanoWSD.WebSocketFrame.CloseCode code, String reason, boolean initiatedByRemote) {
            if (log != null) {
                log.println("CLIENT web socket connection closed.");
                log.flush();
            }
            connectionWatcher.notifyClosing();
            ServerPathSession sps = sessions.get(descriptor);
            if (sps != null) {
                sps.activeWS = null;
            }
            iss.sendClose();
        }

        @Override
        public void onMessage(NanoWSD.WebSocketFrame frame) {
            String message = frame.getTextPayload();
            if (log != null) {
                log.println("CLIENT: " + message);
                log.flush();
            }
            iss.sendText(message);
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
