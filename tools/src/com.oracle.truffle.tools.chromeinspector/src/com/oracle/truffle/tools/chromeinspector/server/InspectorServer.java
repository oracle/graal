/*
 * Copyright (c) 2017, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.io.FileInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

import org.graalvm.polyglot.io.MessageEndpoint;

import com.oracle.truffle.tools.chromeinspector.InspectorExecutionContext;
import com.oracle.truffle.tools.chromeinspector.instrument.InspectorWSConnection;
import com.oracle.truffle.tools.chromeinspector.instrument.KeyStoreOptions;
import com.oracle.truffle.tools.chromeinspector.instrument.Token;
import com.oracle.truffle.tools.utils.java_websocket.WebSocket;
import com.oracle.truffle.tools.utils.java_websocket.WebSocketAdapter;
import com.oracle.truffle.tools.utils.java_websocket.WebSocketImpl;
import com.oracle.truffle.tools.utils.java_websocket.WebSocketServerFactory;
import com.oracle.truffle.tools.utils.java_websocket.drafts.Draft;
import com.oracle.truffle.tools.utils.java_websocket.framing.Framedata;
import com.oracle.truffle.tools.utils.java_websocket.framing.PingFrame;
import com.oracle.truffle.tools.utils.java_websocket.handshake.ClientHandshake;
import com.oracle.truffle.tools.utils.java_websocket.server.DefaultSSLWebSocketServerFactory;
import com.oracle.truffle.tools.utils.java_websocket.server.DefaultWebSocketServerFactory;
import com.oracle.truffle.tools.utils.java_websocket.server.WebSocketServer;
import org.graalvm.shadowed.org.json.JSONArray;
import org.graalvm.shadowed.org.json.JSONObject;

/**
 * Server of the
 * <a href="https://chromium.googlesource.com/v8/v8/+/master/src/inspector/js_protocol.json">Chrome
 * inspector protocol</a>.
 */
public final class InspectorServer extends WebSocketServer implements InspectorWSConnection {

    private static final String WS_PREFIX = "ws://";
    private static final String WS_PREFIX_SECURE = "wss://";
    private static final String DEV_TOOLS_PREFIX = "devtools://devtools/bundled/js_app.html?";
    private static final Map<InetSocketAddress, InspectorServer> SERVERS = new HashMap<>();

    private final boolean secure;
    private final Map<Token, ServerPathSession> sessions = new ConcurrentHashMap<>();
    private final Map<WebSocket, InspectWebSocketHandler> socketConnectionHandlers = new ConcurrentHashMap<>();
    private final CountDownLatch started = new CountDownLatch(1);

    private InspectorServer(InetSocketAddress isa, KeyStoreOptions keyStoreOptions) throws IOException {
        super(isa, 2); // 2 websocket workers to process the network data
        // Note that the DNS rebind attack protection does not apply to WebSockets, because they are
        // handled with a higher-priority HTTP interceptor. We probably could add the protection in
        // openWebSocket method, but it is not needed, because WebSockets are already protected by
        // secret URLs. Also, protecting WebSockets from DNS rebind attacks is not much useful,
        // since they are not protected by same-origin policy.
        // See DNSRebindProtectionHandler in WrappingSocketServerFactory

        // Disable Lost connection detection, Chrome Inspector does not send PONG response reliably.
        setConnectionLostTimeout(0);
        WebSocketServerFactory wssf;
        if (keyStoreOptions != null) {
            wssf = new WrappingSocketServerFactory(new DefaultSSLWebSocketServerFactory(sslContext(keyStoreOptions)));
        } else {
            wssf = new WrappingSocketServerFactory(new DefaultWebSocketServerFactory());
        }
        setWebSocketFactory(wssf);
        setReuseAddr(true);
        secure = keyStoreOptions != null;
    }

    private static SSLContext sslContext(KeyStoreOptions keyStoreOptions) throws IOException {
        String keyStoreFile = keyStoreOptions.getKeyStore();
        if (keyStoreFile != null) {
            String filePasswordProperty = keyStoreOptions.getKeyStorePassword();
            char[] filePassword = filePasswordProperty == null ? "".toCharArray() : filePasswordProperty.toCharArray();
            SSLContext sslContext;
            try {
                KeyStore keystore = KeyStore.getInstance(KeyStore.getDefaultType());
                try (InputStream in = new FileInputStream(keyStoreFile)) {
                    keystore.load(in, filePassword);
                }
                KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
                keyManagerFactory.init(keystore, filePassword);

                TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                trustManagerFactory.init(keystore);

                sslContext = SSLContext.getInstance("TLS");
                sslContext.init(keyManagerFactory.getKeyManagers(),
                                trustManagerFactory.getTrustManagers(),
                                new SecureRandom());
            } catch (GeneralSecurityException ex) {
                throw new IOException(ex);
            }
            return sslContext;
        } else {
            throw new IOException("Use options to specify the keystore");
        }
    }

    private class WrappingSocketServerFactory implements WebSocketServerFactory {

        private final WebSocketServerFactory delegate;

        WrappingSocketServerFactory(WebSocketServerFactory delegate) {
            this.delegate = delegate;
        }

        @Override
        public WebSocketImpl createWebSocket(WebSocketAdapter wsa, Draft draft) {
            return delegate.createWebSocket(wsa, draft);
        }

        @Override
        public WebSocketImpl createWebSocket(WebSocketAdapter wsa, List<Draft> list) {
            return delegate.createWebSocket(wsa, list);
        }

        @Override
        @SuppressWarnings("unchecked")
        public ByteChannel wrapChannel(SocketChannel channel, SelectionKey key) throws IOException {
            return new HTTPChannelWrapper(channel, new DNSRebindProtectionHandler(), new JSONHandler());
        }

        @Override
        public void close() {
            delegate.close();
        }

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
            wss.start();
        }
        return wss;
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
    private class DNSRebindProtectionHandler implements Function<HttpRequest, HttpResponse> {

        @Override
        public HttpResponse apply(HttpRequest request) {
            return handleDnsRebind(request);
        }

    }

    private HttpResponse handleDnsRebind(HttpRequest request) {
        String host = request.getHeaders().get("host");
        if (!isHostOk(host)) {
            String badHost = host != null ? "Bad host " + host + ". Please use IP address." : "Missing host header. Use an up-to-date client.";
            String message = badHost + " This request cannot be served because it looks like DNS rebind attack.";
            Iterator<ServerPathSession> sessionIterator = sessions.values().iterator();
            if (sessionIterator.hasNext()) {
                sessionIterator.next().getContext().getErr().println("Bad connection from " + request.getRemoteAddress() + ". " + message);
            }
            return new HttpResponse("400 Bad Request", "text/plain", "UTF-8", message);
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
        boolean ipv6 = host.startsWith("[") && host.endsWith("]");
        String h = host;
        if (ipv6) {
            h = h.substring(1, h.length() - 1);
        }
        InetAddress address;
        try {
            address = InetAddress.getByName(h);
        } catch (UnknownHostException ex) {
            return false;
        }
        return address instanceof Inet4Address == !ipv6;
    }

    public String getWSAddress(Token token) {
        ServerPathSession serverSession = sessions.get(token);
        return getWSAddress(serverSession);
    }

    private String getWSAddress(ServerPathSession serverSession) {
        String prefix = secure ? WS_PREFIX_SECURE : WS_PREFIX;
        try {
            // Wait for the server to start to be able to provide the actual port number.
            started.await();
        } catch (InterruptedException ex) {
        }
        return prefix + getAddress().getAddress().getHostAddress() + ":" + getPort() + serverSession.pathContainingToken;
    }

    public String getDevtoolsAddress(Token token) {
        return getDevtoolsAddress(getWSAddress(token));
    }

    private static String getDevtoolsAddress(String wsAddress) {
        return DEV_TOOLS_PREFIX + wsAddress.replace("://", "=");
    }

    private class JSONHandler implements Function<HttpRequest, HttpResponse> {

        @Override
        public HttpResponse apply(HttpRequest request) {
            if ("GET".equals(request.getMethod())) {
                String uriStr = request.getUri();
                URI uri;
                try {
                    uri = new URI(uriStr);
                } catch (URISyntaxException ex) {
                    return null;
                }
                String uriPath = uri.getPath();
                String responseJson = null;
                if ("/json/version".equals(uriPath)) {
                    JSONObject version = new JSONObject();
                    version.put("Browser", "GraalVM");
                    version.put("Protocol-Version", "1.2");
                    responseJson = version.toString();
                }
                if ("/json".equals(uriPath) || "/json/list".equals(uriPath)) {
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
                    HttpResponse response = new HttpResponse("200", "application/json", "UTF-8", responseJson);
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
    public void onStart() {
        InetSocketAddress address = getAddress();
        if (address.getPort() == 0) {
            InetSocketAddress realAddress = new InetSocketAddress(address.getAddress(), getPort());
            // Set this server for the real address.
            synchronized (SERVERS) {
                InspectorServer wss = SERVERS.remove(address);
                assert wss == this;
                SERVERS.put(realAddress, wss);
            }
        }
        started.countDown();
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        String uri = handshake.getResourceDescriptor();
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
            InspectWebSocketHandler iws = new InspectWebSocketHandler(token, conn, iss, session.getConnectionWatcher());
            session.activeWS = iws;
            iss.context.logMessage("CLIENT ws connection opened, token = ", token);
            socketConnectionHandlers.put(conn, iws);
        } else {
            conn.close(1003 /* Unsupported Data */, "Bad path.");
        }
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        InspectWebSocketHandler iws = socketConnectionHandlers.remove(conn);
        if (iws != null) {
            iws.didClose();
        }
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        InspectWebSocketHandler iws = socketConnectionHandlers.get(conn);
        if (iws != null) {
            iws.onMessage(message);
        }
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        if (conn == null) {
            Iterator<ServerPathSession> sessionIterator = sessions.values().iterator();
            if (sessionIterator.hasNext()) {
                PrintWriter err = sessionIterator.next().getContext().getErr();
                err.println("WebSocket Error:");
                ex.printStackTrace(err);
            }
            return;
        }
        InspectWebSocketHandler iws = socketConnectionHandlers.get(conn);
        if (iws != null) {
            iws.onException(ex);
        }
    }

    @Override
    public void onWebsocketPing(WebSocket conn, Framedata f) {
        InspectWebSocketHandler iws = socketConnectionHandlers.get(conn);
        if (iws != null) {
            iws.onPing();
        }
        super.onWebsocketPing(conn, f);
    }

    @Override
    public void onWebsocketPong(WebSocket conn, Framedata f) {
        InspectWebSocketHandler iws = socketConnectionHandlers.get(conn);
        if (iws != null) {
            iws.onPong();
        }
        super.onWebsocketPong(conn, f);
    }

    @Override
    public PingFrame onPreparePing(WebSocket conn) {
        InspectWebSocketHandler iws = socketConnectionHandlers.get(conn);
        if (iws != null) {
            iws.onPreparePing();
        }
        return super.onPreparePing(conn);
    }

    @Override
    public void consoleAPICall(Token token, String type, Object text) {
        ServerPathSession sps = sessions.get(token);
        if (sps != null) {
            InspectWebSocketHandler iws = sps.activeWS;
            if (iws != null) {
                iws.iss.consoleAPICall(type, text);
            }
        }
    }

    @Override
    public void close(Token token) throws IOException {
        ServerPathSession sps = sessions.remove(token);
        if (sps != null) {
            InspectWebSocketHandler iws = sps.activeWS;
            if (iws != null) {
                iws.connection.close(1001 /* Going Away */);
            }
            sps.dispose();
        }
        if (sessions.isEmpty()) {
            try {
                stop();
            } catch (InterruptedException ex) {
                throw new IOException(ex);
            }
        }
    }

    @Override
    public void stop() throws InterruptedException {
        synchronized (SERVERS) {
            Iterator<Map.Entry<InetSocketAddress, InspectorServer>> entries = SERVERS.entrySet().iterator();
            while (entries.hasNext()) {
                if (entries.next().getValue() == this) {
                    entries.remove();
                    break;
                }
            }
        }
        super.stop();
    }

    private static class ServerPathSession {

        private final InspectorExecutionContext context;
        private final AtomicReference<InspectServerSession> serverSession;
        private final AtomicBoolean debugBrk;
        private final ConnectionWatcher connectionWatcher;
        private final String pathContainingToken;
        volatile InspectWebSocketHandler activeWS;

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

        void dispose() {
            InspectServerSession iss = serverSession.getAndSet(null);
            if (iss != null) {
                iss.dispose();
            } else {
                InspectWebSocketHandler iws = activeWS;
                if (iws != null) {
                    iws.disposeSession();
                }
            }
        }
    }

    private class InspectWebSocketHandler {

        private final Token token;
        private final WebSocket connection;
        private final InspectServerSession iss;
        private final ConnectionWatcher connectionWatcher;

        InspectWebSocketHandler(Token token, WebSocket connection, InspectServerSession iss, ConnectionWatcher connectionWatcher) {
            this.token = token;
            this.connection = connection;
            this.iss = iss;
            this.connectionWatcher = connectionWatcher;
            init();
        }

        private void init() {
            iss.context.logMessage("CLIENT web socket connection opened.", "");
            connectionWatcher.notifyOpen();
            iss.open(new MessageEndpoint() {
                @Override
                public void sendText(String message) throws IOException {
                    iss.context.logMessage("SERVER: ", message);
                    connection.send(message);
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
                    connection.close(1000 /* Normal Closure */);
                }
            });
        }

        void didClose() {
            iss.context.logMessage("CLIENT web socket connection closed.", "");
            connectionWatcher.notifyClosing();
            ServerPathSession sps = sessions.get(token);
            if (sps != null) {
                sps.activeWS = null;
            }
            try {
                iss.sendClose();
            } catch (IOException e) {
                iss.context.logException(e);
            }
        }

        void onMessage(String message) {
            iss.context.logMessage("CLIENT: ", message);
            try {
                iss.sendText(message);
            } catch (IOException e) {
                iss.context.logException(e);
            }
        }

        void onException(Exception exception) {
            iss.context.logException("CLIENT: ", exception);
        }

        void onPreparePing() {
            iss.context.logMessage("SERVER: ", "SENDING PING");
        }

        void onPing() {
            iss.context.logMessage("CLIENT: ", "PING");
            // The default implementation of WebSocketAdapter sends pong.
        }

        void onPong() {
            iss.context.logMessage("CLIENT: ", "PONG");
        }

        void disposeSession() {
            iss.dispose();
        }
    }

    private static class HTTPChannelWrapper implements ByteChannel {

        private final Function<HttpRequest, HttpResponse>[] interceptors;
        private final SocketChannel channel;
        private final ByteBuffer buffer = ByteBuffer.allocate(16384);
        private boolean wsUpgraded = false;

        @SafeVarargs
        @SuppressWarnings("varargs")
        HTTPChannelWrapper(SocketChannel channel, Function<HttpRequest, HttpResponse>... interceptors) {
            this.channel = channel;
            this.interceptors = interceptors;
        }

        @Override
        public int read(ByteBuffer dst) throws IOException {
            if (wsUpgraded) {
                return channel.read(dst);
            }

            buffer.clear();
            int r = channel.read(buffer);
            if (r > 0) {
                buffer.flip();
                HttpRequest httpRequest = readHttpRequest();
                if ("Upgrade".equalsIgnoreCase(httpRequest.getHeaders().get("connection")) && httpRequest.getHeaders().get("sec-websocket-key") != null) {
                    assert !httpRequest.getVersion().isEmpty();
                    wsUpgraded = true;
                    buffer.rewind();
                    dst.put(buffer);
                    return r;
                }
                writeHttpResponse(httpRequest);
                close();
                r = 0;
            }
            return r;
        }

        private HttpRequest readHttpRequest() throws IOException {
            String line = Draft.readStringLine(buffer);
            StringTokenizer tokens = new StringTokenizer(line);
            if (!tokens.hasMoreTokens()) {
                HttpResponse.write400(channel, "Bad Request: method is missing");
            }
            String method = tokens.nextToken();
            if (!tokens.hasMoreTokens()) {
                HttpResponse.write400(channel, "Bad Request: URI is missing");
            }
            String uri = tokens.nextToken();
            if (!tokens.hasMoreTokens()) {
                HttpResponse.write400(channel, "Bad Request: protocol version is missing");
            }
            String version = tokens.nextToken();
            HttpRequest request = new HttpRequest((InetSocketAddress) channel.getRemoteAddress(), method, uri, version);

            while ((line = Draft.readStringLine(buffer)) != null && !line.trim().isEmpty()) {
                int p = line.indexOf(':');
                if (p >= 0) {
                    request.addHeader(line.substring(0, p).trim().toLowerCase(Locale.ENGLISH), line.substring(p + 1).trim());
                }
            }
            return request;
        }

        private void writeHttpResponse(HttpRequest request) throws IOException {
            for (Function<HttpRequest, HttpResponse> interceptor : interceptors) {
                HttpResponse response = interceptor.apply(request);
                if (response != null) {
                    response.writeTo(channel);
                    return;
                }
            }
            HttpResponse.write404(channel);
        }

        @Override
        public int write(ByteBuffer src) throws IOException {
            if (wsUpgraded) {
                return channel.write(src);
            } else {
                throw new IOException("Unexpected write of " + src);
            }
        }

        @Override
        public boolean isOpen() {
            return channel.isOpen();
        }

        @Override
        public void close() throws IOException {
            channel.close();
        }

    }

    public static final class HttpRequest {

        private final InetSocketAddress remoteAddress;
        private final String method;
        private final String uri;
        private final String version;
        private final Map<String, String> headers = new LinkedHashMap<>();

        HttpRequest(InetSocketAddress remoteAddress, String method, String uri, String version) {
            this.remoteAddress = remoteAddress;
            this.method = method;
            this.uri = uri;
            this.version = version;
        }

        private void addHeader(String name, String value) {
            headers.put(name, value);
        }

        public InetSocketAddress getRemoteAddress() {
            return remoteAddress;
        }

        private Object getMethod() {
            return method;
        }

        private String getUri() {
            return uri;
        }

        private String getVersion() {
            return version;
        }

        private Map<String, String> getHeaders() {
            return headers;
        }
    }

    public static final class HttpResponse {

        private final String status;
        private final String contentType;
        private final String encoding;
        private final String content;
        private final Map<String, String> headers = new LinkedHashMap<>();

        HttpResponse(String status, String contentType, String encoding, String content) {
            this.status = status;
            this.contentType = contentType;
            this.encoding = encoding;
            this.content = content;
        }

        public void addHeader(String name, String value) {
            headers.put(name, value);
        }

        public void writeTo(ByteChannel channel) throws IOException {
            write(channel, status, contentType, encoding, headers, content);
        }

        public static void write404(ByteChannel channel) throws IOException {
            write(channel, "404", "text/plain", StandardCharsets.US_ASCII.name(), Collections.emptyMap(), "");
        }

        public static void write400(ByteChannel channel, String text) throws IOException {
            write(channel, "400 Bad Request", "text/plain", StandardCharsets.US_ASCII.name(), Collections.emptyMap(), text);
        }

        private static void write(ByteChannel channel, String status, String contentType, String encodingOrig, Map<String, String> headers, String content) throws IOException {
            // To prevent from "parameter encoding should not be assigned" warning.
            String encoding = encodingOrig;
            byte[] bytes;
            try {
                Charset charset = Charset.forName(encoding);
                CharsetEncoder newEncoder = charset.newEncoder();
                if (!newEncoder.canEncode(content)) {
                    charset = StandardCharsets.UTF_8;
                    encoding = charset.name();
                }
                bytes = content.getBytes(charset);
            } catch (IllegalCharsetNameException | UnsupportedCharsetException e) {
                bytes = new byte[0];
            }
            write(channel, ("HTTP/1.1 " + status + " \r\n").getBytes(StandardCharsets.US_ASCII));
            writeHeader(channel, "Content-Type", contentType + "; charset=" + encoding);
            for (Map.Entry<String, String> header : headers.entrySet()) {
                writeHeader(channel, header.getKey(), header.getValue());
            }
            writeHeader(channel, "Content-Length", Integer.toString(bytes.length));
            write(channel, "\r\n".getBytes(StandardCharsets.US_ASCII));
            channel.write(ByteBuffer.wrap(bytes));
        }

        private static void write(ByteChannel channel, byte[] bytes) throws IOException {
            channel.write(ByteBuffer.wrap(bytes));
        }

        private static void writeHeader(ByteChannel channel, String name, String value) throws IOException {
            write(channel, (name + ": " + value + "\r\n").getBytes(StandardCharsets.US_ASCII));
        }
    }
}
