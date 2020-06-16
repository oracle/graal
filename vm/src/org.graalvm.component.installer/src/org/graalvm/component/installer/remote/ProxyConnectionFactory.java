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
package org.graalvm.component.installer.remote;

import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.graalvm.component.installer.Feedback;
import org.graalvm.component.installer.URLConnectionFactory;

/**
 * Creates URLConnections to the given destination. Caches the decision about proxy. For the first
 * {@link #openConnection(java.net.URI, java.util.function.Consumer)}, the code wil open
 * <ul>
 * <li>a direct connection to the proxy
 * <li>a connection through http proxy, if configured
 * <li>a connection through https proxy, if configured
 * </ul>
 * Users may forget to set both the http/https proxies, so the code will attempt to use whetaver is
 * known. All those connections will attempt to connect to the destination. The first connection
 * attempt that succeeds will be used. The mechanism (proxy setting, direct) that established the
 * connection will be cached for subsequent requests to the same authority. Additional requests to
 * the same location should not open additional probes then.
 * <p/>
 * The factory is caching threads; initially at most 3 threads will be created for probes, then each
 * request will reuse a thread from the thread pool (to watch time out the operation on the main
 * thread).
 * 
 * @author sdedic
 */
public class ProxyConnectionFactory implements URLConnectionFactory {
    /**
     * The max delay to connect to the final destination or open a proxy connection. In seconds.
     */
    private static final int DEFAULT_CONNECT_DELAY = Integer.getInteger("org.graalvm.component.installer.connectDelaySec", 10);

    /**
     * Delay in the case proxies are not used. Heuristics is not used at all, so the connection time
     * may be longer. In seconds.
     */
    private static final int DEFAULT_DIRECT_CONNECT_DELAY = Integer.getInteger("org.graalvm.component.installer.directConnectDelaySec", 20);

    private static final String PROXY_SCHEME_PREFIX = "http://"; // NOI18N

    private final Feedback feedback;
    private final URL urlBase;

    // @GuardedBy(this)
    private Connector winningConnector;

    /**
     * Thread pool for connection attempts. Subsequent connections do not send unnecessary probes,
     * but are done in a thread so the main thread may time out the operation. Most likely the
     * threads will be allocated from this pool.
     */
    private final ExecutorService connectors = Executors.newCachedThreadPool();

    /**
     * HTTP proxy settings. The default is taken from system environment variables and system
     * property
     */
    String envHttpProxy = System.getProperty("http_proxy", System.getenv("http_proxy")); // NOI18N

    /**
     * HTTPS proxy settings. The default is taken from system environment variables and system
     * property
     */
    String envHttpsProxy = System.getProperty("https_proxy", System.getenv("https_proxy")); // NOI18N

    /**
     * The configurable delay for this factory. Initialized to {@link #DEFAULT_CONNECT_DELAY}.
     */
    private int connectDelay = DEFAULT_CONNECT_DELAY;

    private int directConnectDelay = DEFAULT_DIRECT_CONNECT_DELAY;

    public ProxyConnectionFactory(Feedback feedback, URL urlBase) {
        this.feedback = feedback.withBundle(ProxyConnectionFactory.class);
        this.urlBase = urlBase;
    }

    /**
     * Customizes connection timeouts. Base delay must be at least 0 (infinite wait). If the direct
     * delay is negative, it is scaled from base delay using the same factor as the default timeouts
     * 
     * @param delay base delay, 0 for infinite wait. Must not be negative
     * @param directDelay single connection delay, negative to derive from the base delay
     */
    public void setConnectDelay(int delay, int directDelay) {
        if (delay < 0) {
            throw new IllegalArgumentException();
        }
        this.connectDelay = delay;
        if (directDelay >= 0) {
            this.directConnectDelay = directDelay;
        } else {
            // scale the base delay by the default connection factor
            float factor = Math.min(1, ((float) DEFAULT_DIRECT_CONNECT_DELAY / DEFAULT_CONNECT_DELAY));
            this.directConnectDelay = Math.round(delay * factor);
        }
    }

    /**
     * Scales the default timeouts by some factor.
     * 
     * @param factor factor to scale the default timeouts.
     */
    public void setConnectDelayFactor(float factor) {
        this.connectDelay = Math.round(DEFAULT_CONNECT_DELAY * factor);
        this.directConnectDelay = Math.round(DEFAULT_DIRECT_CONNECT_DELAY * factor);
    }

    public ProxyConnectionFactory setProxy(boolean secure, String proxyURI) {
        if (secure) {
            envHttpsProxy = proxyURI;
        } else {
            envHttpProxy = proxyURI;
        }
        return this;
    }

    public URLConnection openConnection(URI relative, Consumer<URLConnection> configCallback) throws IOException {
        if (relative != null) {
            try {
                return openConnectionWithProxies(urlBase.toURI().resolve(relative).toURL(), configCallback);
            } catch (URISyntaxException ex) {
                throw new IOException(ex);
            }
        } else {
            return openConnectionWithProxies(urlBase, configCallback);
        }
    }

    private class ConnectionContext {
        private final Consumer<URLConnection> configCallback;
        private final CountDownLatch countDown;
        private final URL url;
        private final List<Connector> tryConnectors = new ArrayList<>();

        // @GuardedBy(this)
        private Connector winner;
        // @GuardedBy(this)
        private URLConnection openedConnection;
        // @GuardedBy(this)
        private IOException exProxy;
        // @GuardedBy(this)
        private IOException exDirect;
        // @GuardedBy(this);
        private int outcomes;

        ConnectionContext(URL url, Consumer<URLConnection> configCallback, CountDownLatch latch) {
            this.configCallback = configCallback;
            this.countDown = latch;
            this.url = url;
        }

        synchronized URLConnection getConnection() throws IOException {
            if (openedConnection == null) {
                if (exDirect != null) {
                    throw exDirect;
                } else if (exProxy != null) {
                    throw exProxy;
                }
                throw new ConnectException(feedback.l10n("EXC_CannotConnectTo", url));
            } else {
                return openedConnection;
            }
        }

        boolean setOutcome(Connector w, URLConnection opened) {
            synchronized (this) {
                if (winner != null) {
                    return false;
                }
                winner = w;
                openedConnection = opened;
            }
            if (countDown != null) {
                countDown.countDown();
            }
            return true;
        }

        void setOutcome(boolean direct, IOException e) {
            synchronized (this) {
                if (direct) {
                    exDirect = e;
                } else {
                    exProxy = e;
                }
                if (++outcomes == tryConnectors.size()) {
                    countDown.countDown();
                }
            }
        }

        synchronized IOException getConnectException() {
            if (exDirect != null) {
                return exDirect;
            }
            return new ConnectException(feedback.l10n("EXC_TimeoutConnectTo", url));
        }

        synchronized void submit(Connector c) {
            tryConnectors.add(c);
        }

        void start() {
            for (Connector c : tryConnectors) {
                connectors.submit(c);
            }
        }
    }

    final class Connector implements Runnable {
        private final String proxySpec;
        private URL url;
        private ConnectionContext context;

        Connector(String proxySpec) {
            this.proxySpec = proxySpec;
        }

        boolean isDirect() {
            return proxySpec == null;
        }

        boolean accepts(URL u) {
            synchronized (this) {
                return Objects.equals(u.getAuthority(), url.getAuthority());
            }
        }

        Connector bind(ConnectionContext ctx) {
            synchronized (this) {
                context = ctx;
                url = ctx.url;
            }
            return this;
        }

        @Override
        public void run() {
            ConnectionContext ctx;

            synchronized (this) {
                ctx = context;
                context = null;
            }
            runWithContext(ctx);
        }

        void runWithContext(ConnectionContext ctx) {
            final Proxy proxy;
            if (isDirect()) {
                proxy = null;
            } else {
                if (proxySpec == null || proxySpec.isEmpty()) {
                    return;
                }
                try {
                    // the URI is created just to parse the proxy specification. It won't be
                    // actually used as a whole to form URL or open connection
                    URI uri = new URI(proxySpec);
                    // if the user forgets the scheme (http://) the string is misparsed and hostname
                    // becomes the scheme while the host part will be empty. Adding the "http://" in
                    // front
                    // fixes parsing at least for the host/port part.
                    if (uri.getScheme() == null || uri.getHost() == null) {
                        try {
                            uri = new URI(PROXY_SCHEME_PREFIX + proxySpec);
                        } catch (URISyntaxException ex) {
                            // better leave the specified value without the scheme.
                        }
                    }
                    InetSocketAddress address = InetSocketAddress.createUnresolved(uri.getHost(), uri.getPort());
                    proxy = new Proxy(Proxy.Type.HTTP, address);
                } catch (URISyntaxException ex) {
                    ctx.setOutcome(false, new IOException(ex.getLocalizedMessage(), ex));
                    return;
                }
            }
            Consumer<URLConnection> configCallback;
            configCallback = ctx.configCallback;
            boolean won = false;
            URLConnection test = null;
            try {
                test = proxy == null ? url.openConnection() : url.openConnection(proxy);
                if (configCallback != null) {
                    configCallback.accept(test);
                }
                test.connect();
                if (test instanceof HttpURLConnection) {
                    HttpURLConnection htest = (HttpURLConnection) test;
                    int rcode = htest.getResponseCode();
                    // using bad request 400 as a marker. All 4xx, 5xx codes should be handled this
                    // way to force
                    // the appropriate exception out.
                    if (rcode >= HttpURLConnection.HTTP_BAD_REQUEST) {
                        // force the exception, should fail with IOException
                        InputStream stm = test.getInputStream();
                        try {
                            stm.close();
                        } catch (IOException ex) {
                            // let the exception through for direct connections, maybe better error
                            // message
                            // will come out.
                            if (isDirect()) {
                                throw ex;
                            }
                            // swallow, we want to report just proxy failed.
                        }
                        if (!isDirect()) {
                            throw new IOException(feedback.l10n("EXC_ProxyFailed", rcode));
                        }
                    }
                }
                won = ctx.setOutcome(this, test);
            } catch (IOException ex) {
                ctx.setOutcome(isDirect(), ex);
            } finally {
                if (!won) {
                    if (test instanceof HttpURLConnection) {
                        ((HttpURLConnection) test).disconnect();
                    }
                }
            }
        }
    }

    private URLConnection openConnectionWithProxies(URL url, Consumer<URLConnection> configCallback) throws IOException {
        final CountDownLatch connected = new CountDownLatch(1);
        String httpProxy;
        String httpsProxy;

        Connector winner;
        ConnectionContext ctx = new ConnectionContext(url, configCallback, connected);

        synchronized (this) {
            httpProxy = envHttpProxy;
            httpsProxy = envHttpsProxy;
            winner = winningConnector;
        }

        boolean haveProxy = false;

        // reuse the same detected direct / proxy for matching URLs.
        if (winner != null && winner.accepts(url)) {
            winner.bind(ctx);
            // submit the winner so we can also recover from long connect delays
            ctx.submit(winner);
            // note: the winner will benefit from larger timeout as it is just one
            // connection
        } else {
            if (httpProxy != null) {
                ctx.submit(new Connector(httpProxy).bind(ctx));
            }
            // do not attempt 2nd probe try if http+https are set to the same value.
            if (httpsProxy != null && !Objects.equals(httpProxy, httpsProxy)) {
                ctx.submit(new Connector(httpsProxy).bind(ctx));
            }
            ctx.submit(new Connector(null).bind(ctx));
        }

        ctx.start();

        int shouldDelay = haveProxy ? connectDelay : directConnectDelay;

        URLConnection res = null;

        try {
            if (shouldDelay > 0) {
                if (!connected.await(shouldDelay, TimeUnit.SECONDS)) {
                    throw ctx.getConnectException();
                } else {
                    // may also throw exception
                    res = ctx.getConnection();
                }
            } else {
                // wait indefinitely ... until network times out.
                connected.await();
                res = ctx.getConnection();
            }
        } catch (InterruptedException ex) {
            throw new ConnectException(feedback.l10n("EXC_InterruptedConnectingTo", url));
        }
        return res;
    }

    @Override
    public URLConnection createConnection(URL u, Consumer<URLConnection> configCallback) throws IOException {
        try {
            return openConnection(u.toURI(), configCallback);
        } catch (URISyntaxException ex) {
            throw new IOException(ex);
        }
    }

}
