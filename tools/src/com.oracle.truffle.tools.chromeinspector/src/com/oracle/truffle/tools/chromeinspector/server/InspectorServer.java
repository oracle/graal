/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates. All rights reserved.
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
import java.io.PrintWriter;
import java.io.PushbackInputStream;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
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

import com.oracle.truffle.tools.chromeinspector.instrument.Token;
import org.nanohttpd.protocols.http.ClientHandler;
import org.nanohttpd.protocols.http.IHTTPSession;
import org.nanohttpd.protocols.http.NanoHTTPD;
import org.nanohttpd.protocols.http.content.ContentType;
import org.nanohttpd.protocols.http.request.Method;
import org.nanohttpd.protocols.http.response.Response;
import org.nanohttpd.protocols.http.response.Status;
import org.nanohttpd.protocols.websockets.CloseCode;
import org.nanohttpd.protocols.websockets.NanoWSD;
import org.nanohttpd.protocols.websockets.WebSocket;
import org.nanohttpd.protocols.websockets.WebSocketFrame;
import org.nanohttpd.util.IHandler;

import org.graalvm.polyglot.io.MessageEndpoint;

import com.oracle.truffle.api.TruffleOptions;
import com.oracle.truffle.tools.utils.json.JSONArray;
import com.oracle.truffle.tools.utils.json.JSONObject;

import com.oracle.truffle.tools.chromeinspector.InspectorExecutionContext;
import com.oracle.truffle.tools.chromeinspector.instrument.KeyStoreOptions;
import com.oracle.truffle.tools.chromeinspector.instrument.InspectorWSConnection;
import sun.net.util.IPAddressUtil;

/**
 * Server of the
 * <a href="https://chromium.googlesource.com/v8/v8/+/master/src/inspector/js_protocol.json">Chrome
 * inspector protocol</a>.
 */
public final class InspectorServer extends NanoWSD implements InspectorWSConnection {

    private static final String WS_PREFIX = "ws://";
    private static final String WS_PREFIX_SECURE = "wss://";
    private static final String DEV_TOOLS_PREFIX = "devtools://devtools/bundled/js_app.html?";
    private static final Map<InetSocketAddress, InspectorServer> SERVERS = new HashMap<>();

    private final int port;
    private final boolean secure;
    private final Map<Token, ServerPathSession> sessions = new ConcurrentHashMap<>();

    private InspectorServer(InetSocketAddress isa, KeyStoreOptions keyStoreOptions) throws IOException {
        super(isa.getAddress().getHostAddress(), isa.getPort());
        this.port = isa.getPort();
        // Note that the DNS rebind attack protection does not apply to WebSockets, because they are
        // handled with a higher-priority HTTP interceptor. We probably could add the protection in
        // openWebSocket method, but it is not needed, because WebSockets are already protected by
        // secret URLs. Also, protecting WebSockets from DNS rebind attacks is not much useful,
        // since they are not protected by same-origin policy.
        addHTTPInterceptor(new DNSRebindProtectionHandler());
        addHTTPInterceptor(new JSONHandler());
        if (keyStoreOptions != null) {
            if (TruffleOptions.AOT) {
                throw new IOException("Secure connection is not available in the native-image yet.");
            } else {
                makeSecure(createSSLFactory(keyStoreOptions), null);
            }
        }
        secure = keyStoreOptions != null;
    }

    public static InspectorServer get(InetSocketAddress isa, Token token, String pathContainingToken, InspectorExecutionContext context, boolean debugBrk,
                    boolean secure, KeyStoreOptions keyStoreOptions, ConnectionWatcher connectionWatcher,
                    InspectServerSession initialSession) throws IOException {
        InspectorServer wss;
        boolean startServer = false;
        synchronized (SERVERS) {
            wss = SERVERS.get(isa);
            if (wss == null) {
                wss = new InspectorServer(isa, secure ? keyStoreOptions : null);
                context.logMessage("", "New WebSocketServer at " + isa);
                startServer = true;
                SERVERS.put(isa, wss);
            }
            if (wss.sessions.containsKey(token)) {
                // We have a session with this path already
                throw new IOException("Inspector session with the same path exists already on " + isa.getHostString() + ":" + isa.getPort());
            }
            wss.sessions.put(token, new ServerPathSession(context, initialSession, debugBrk, connectionWatcher, pathContainingToken));
        }
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
                try (FileInputStream keyIn = new FileInputStream(keyFile)) {
                    keystore.load(keyIn, filePassword);
                }
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

    /**
     * DNS rebind attack uses victim's web browser as a proxy in order to access servers in local
     * network or even on localhost. The web browser works with a DNS name the attacker can control
     * (say evil.example.com), which suddenly starts being resolved to a local IP (even to
     * 127.0.0.1). This technique allows the attacker to partially circumvent the same-origin
     * policy. That is, the attacker will be able to connect to the server, but the browser will
     * consider it as evil.example.com. As a result, the browser sends the attacker's domain name
     * (e.g., evil.example.com) in the Host header. Also, the attacker does not get access to
     * cookies or other locally-stored data for the website, as the browser considers it as a
     * different domain.
     *
     * So, the attacker circumvents just the network restriction, and even this is limited: The
     * protocol has to be based on HTTP (or HTTPS with likely invalid certificate) and the Host
     * header will point to an attacker-controlled domain. Note that browsers prevent webpages to
     * override values of this header: https://fetch.spec.whatwg.org/#forbidden-header-name
     *
     * In order to prevent the attack, we check the Host header:
     * <ul>
     * <li>If there is a valid IP address (IPv4 or IPv6), it can't be a DNS rebind attack, as no DNS
     * name was used.</li>
     * <li>If there is a whitelisted domain name (one that attacker can't control), it is OK. We
     * assume that the attacker can control DNS records for everything but localhost. This matches
     * both standard practice and RFC 6761.</li>
     * <li>For everything else (including missing host header), we deny the request.</li>
     * </ul>
     */
    private class DNSRebindProtectionHandler implements IHandler<IHTTPSession, Response> {

        @Override
        public Response handle(IHTTPSession ihttpSession) {
            return handleDnsRebind(ihttpSession);
        }

    }

    private Response handleDnsRebind(IHTTPSession ihttpSession) {
        String host = ihttpSession.getHeaders().get("host");
        if (!isHostOk(host)) {
            String badHost = host != null ? "Bad host " + host + ". Please use IP address." : "Missing host header. Use an up-to-date client.";
            String message = badHost + " This request cannot be served because it looks like DNS rebind attack.";
            Iterator<ServerPathSession> sessionIterator = sessions.values().iterator();
            if (sessionIterator.hasNext()) {
                sessionIterator.next().getContext().getErr().println("Bad connection from " + ihttpSession.getRemoteIpAddress() + ". " + message);
            }
            return Response.newFixedLengthResponse(
                            Status.BAD_REQUEST,
                            "text/plain; charset=UTF-8",
                            message);
        } else {
            return null;
        }
    }

    private static boolean isHostOk(String host) {
        if (host == null) {
            return false;
        } else {
            final String bareHost = host.replaceFirst(":([0-9]+)$", "");
            return (bareHost.equals("localhost") || isValidIp(bareHost));
        }
    }

    private static boolean isValidIp(String host) {
        return IPAddressUtil.isIPv4LiteralAddress(host) || isValidIpv6(host);
    }

    private static boolean isValidIpv6(String host) {
        return host.startsWith("[") && host.endsWith("]") && IPAddressUtil.isIPv6LiteralAddress(host.substring(1, host.length() - 1));
    }

    public String getWSAddress(Token token) {
        ServerPathSession serverSession = sessions.get(token);
        return getWSAddress(serverSession);
    }

    private String getWSAddress(ServerPathSession serverSession) {
        String prefix = secure ? WS_PREFIX_SECURE : WS_PREFIX;
        return prefix + getHostname() + ":" + getPort() + serverSession.pathContainingToken;
    }

    public String getDevtoolsAddress(Token token) {
        return getDevtoolsAddress(getWSAddress(token));
    }

    private static String getDevtoolsAddress(String wsAddress) {
        return DEV_TOOLS_PREFIX + wsAddress.replace("://", "=");
    }

    private class JSONHandler implements IHandler<IHTTPSession, Response> {

        @Override
        public Response handle(IHTTPSession session) {
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
                    for (ServerPathSession serverPathSession : sessions.values()) {
                        final String path = serverPathSession.pathContainingToken;
                        JSONObject info = new JSONObject();
                        info.put("description", "GraalVM");
                        info.put("faviconUrl", "https://assets-cdn.github.com/images/icons/emoji/unicode/1f680.png");
                        String ws = getWSAddress(serverPathSession);
                        info.put("devtoolsFrontendUrl", getDevtoolsAddress(ws));
                        info.put("id", path.substring(1));
                        info.put("title", "GraalVM");
                        info.put("type", "node");
                        info.put("webSocketDebuggerUrl", ws);
                        json.put(info);
                    }
                    responseJson = json.toString();
                }
                if (responseJson != null) {
                    Response response = Response.newFixedLengthResponse(Status.OK,
                                    "application/json; charset=UTF-8",
                                    responseJson);
                    response.addHeader("Cache-Control", "no-cache,no-store,must-revalidate");
                    response.addHeader("Pragma", "no-cache");
                    response.addHeader("X-Content-Type-Options", "nosniff");
                    return response;
                }
            }
            return null;
        }
    }

    @Override
    protected WebSocket openWebSocket(IHTTPSession handshake) {
        final String uri = handshake.getUri();
        final Token token = Token.createHashedTokenFromString(uri);
        ServerPathSession session;
        session = sessions.get(token);
        if (session != null) {
            InspectServerSession iss = session.getServerSession();
            if (iss == null) {
                // Do the initial break for the first time only, do not break on reconnect
                boolean debugBreak = Boolean.TRUE.equals(session.getDebugBrkAndReset());
                iss = InspectServerSession.create(session.getContext(), debugBreak, session.getConnectionWatcher());
            }
            InspectWebSocket iws = new InspectWebSocket(handshake, iss, session.getConnectionWatcher());
            session.activeWS = iws;
            iss.context.logMessage("CLIENT ws connection opened, token = ", token);
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
            String text;
            try {
                text = new String(buf, "US-ASCII");
            } catch (UnsupportedEncodingException ex) {
                text = null;
            } finally {
                pbInputStream.unread(buf);
            }
            if (!"GET".equals(text)) {
                return new ClientHandler(this, pbInputStream, finalAccept) {
                    @Override
                    public void run() {
                        try (OutputStream outputStream = finalAccept.getOutputStream()) {
                            ContentType contentType = new ContentType(NanoHTTPD.MIME_PLAINTEXT);
                            PrintWriter pw = new PrintWriter(new BufferedWriter(new OutputStreamWriter(outputStream, contentType.getEncoding())), false);
                            pw.append("HTTP/1.1 ").append(Status.BAD_REQUEST.getDescription()).append(" \r\n");
                            String mimeType = contentType.getContentTypeHeader();
                            if (mimeType != null) {
                                pw.append("Content-Type: ").append(mimeType).append("\r\n");
                            }
                            pw.append("\r\n");
                            pw.append("WebSockets request was expected");
                            pw.flush();
                        } catch (IOException ex) {
                            // The handler is not associated with any particular session,
                            // log to some of the sessions:
                            Iterator<ServerPathSession> sessionIterator = sessions.values().iterator();
                            if (sessionIterator.hasNext()) {
                                sessionIterator.next().context.logException(ex);
                            }
                        } finally {
                            NanoHTTPD.safeClose(pbInputStream);
                            NanoHTTPD.safeClose(finalAccept);
                        }
                    }
                };
            }
        } catch (IOException ex) {
        }
        return new ClientHandler(this, pbInputStream, finalAccept);
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
    public void close(Token token) throws IOException {
        ServerPathSession sps = sessions.remove(token);
        if (sps != null) {
            InspectWebSocket iws = sps.activeWS;
            if (iws != null) {
                iws.close(CloseCode.GoingAway, "", false);
            }
        }
        if (sessions.isEmpty()) {
            stop();
        }
    }

    @Override
    public void consoleAPICall(Token token, String type, Object text) {
        ServerPathSession sps = sessions.get(token);
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
            Iterator<Map.Entry<InetSocketAddress, InspectorServer>> entries = SERVERS.entrySet().iterator();
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
        private final String pathContainingToken;
        volatile InspectWebSocket activeWS;

        ServerPathSession(InspectorExecutionContext context, InspectServerSession serverSession, boolean debugBrk, ConnectionWatcher connectionWatcher, String pathContainingToken) {
            this.context = context;
            this.serverSession = new AtomicReference<>(serverSession);
            this.debugBrk = new AtomicBoolean(debugBrk);
            this.connectionWatcher = connectionWatcher;
            this.pathContainingToken = pathContainingToken;
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

    private class InspectWebSocket extends WebSocket {

        private final Token token;
        private final InspectServerSession iss;
        private final ConnectionWatcher connectionWatcher;

        InspectWebSocket(IHTTPSession handshake, InspectServerSession iss,
                        ConnectionWatcher connectionWatcher) {
            super(handshake);
            this.token = Token.createHashedTokenFromString(handshake.getUri());
            this.iss = iss;
            this.connectionWatcher = connectionWatcher;
        }

        @Override
        public void onOpen() {
            iss.context.logMessage("CLIENT web socket connection opened.", "");
            connectionWatcher.notifyOpen();
            iss.setMessageListener(new MessageEndpoint() {
                @Override
                public void sendText(String message) throws IOException {
                    iss.context.logMessage("SERVER: ", message);
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
                    close(CloseCode.NormalClosure, "", true);
                }
            });
        }

        @Override
        public void onClose(CloseCode code, String reason, boolean initiatedByRemote) {
            iss.context.logMessage("CLIENT web socket connection closed.", "");
            connectionWatcher.notifyClosing();
            ServerPathSession sps = sessions.get(token);
            if (sps != null) {
                sps.activeWS = null;
            }
            iss.sendClose();
        }

        @Override
        public void close(CloseCode code, String reason, boolean initiatedByRemote) throws IOException {
            try {
                super.close(code, reason, initiatedByRemote);
            } catch (SocketException ex) {
                // The socket is broken already.
            }
        }

        @Override
        public void onMessage(WebSocketFrame frame) {
            String message = frame.getTextPayload();
            iss.context.logMessage("CLIENT: ", message);
            iss.sendText(message);
        }

        @Override
        protected void onPong(WebSocketFrame pong) {
            iss.context.logMessage("CLIENT PONG: ", pong);
        }

        @Override
        protected void onException(IOException exception) {
            iss.context.logException("CLIENT: ", exception);
        }

    }

    private class ClosedWebSocket extends WebSocket {

        ClosedWebSocket(IHTTPSession handshakeRequest) {
            super(handshakeRequest);
            try {
                close(CloseCode.UnsupportedData, "Bad path.", false);
            } catch (IOException ioex) {
            }
        }

        @Override
        protected void onOpen() {
        }

        @Override
        protected void onClose(CloseCode cc, String string, boolean bln) {
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
