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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.graalvm.component.installer.Feedback;
import org.graalvm.component.installer.URLConnectionFactory;

/**
 * Creates URLConnections to the given destination. Caches the decision about proxy.
 * 
 * @author sdedic
 */
public class ProxyConnectionFactory implements URLConnectionFactory {
    /**
     * The max delay to connect to the final destination or open a proxy connection. In seconds.
     */
    private static final int DEFAULT_CONNECT_DELAY = Integer.getInteger("org.graalvm.component.installer.connectDelaySec", 5);

    private final Feedback feedback;
    private final URL urlBase;

    /**
     * Remembered type of proxy / no proxy.
     */
    private int proxyType;

    /**
     * HTTP proxy settings. The default is taken from system environment variables.
     */
    String envHttpProxy = System.getenv("http_proxy"); // NOI18N

    /**
     * HTTPS proxy settings. The default is taken from system environment variables.
     */
    String envHttpsProxy = System.getenv("https_proxy"); // NOI18N

    /**
     * The configurable delay for this factory. Initialized to {@link #DEFAULT_CONNECT_DELAY}.
     */
    private int connectDelay = DEFAULT_CONNECT_DELAY;

    public ProxyConnectionFactory(Feedback feedback, URL urlBase) {
        this.feedback = feedback.withBundle(ProxyConnectionFactory.class);
        this.urlBase = urlBase;
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

    private URLConnection openConnectionWithProxies(URL url, Consumer<URLConnection> configCallback) throws IOException {
        final URLConnection[] conn = {null};
        final CountDownLatch connected = new CountDownLatch(1);
        ExecutorService connectors = Executors.newFixedThreadPool(3);
        AtomicReference<IOException> ex3 = new AtomicReference<>();
        AtomicReference<IOException> ex2 = new AtomicReference<>();
        String httpProxy;
        String httpsProxy;

        synchronized (this) {
            httpProxy = envHttpProxy;
            httpsProxy = envHttpsProxy;
        }

        class Connector implements Runnable {
            private final String proxySpec;
            private final boolean directConnect;

            Connector() {
                directConnect = true;
                proxySpec = null;
            }

            Connector(String proxySpec) {
                this.proxySpec = proxySpec;
                this.directConnect = false;
            }

            @Override
            public void run() {
                final Proxy proxy;
                if (directConnect) {
                    proxy = null;
                } else {
                    if (proxySpec == null || proxySpec.isEmpty()) {
                        return;
                    }
                    try {
                        URI uri = new URI(httpsProxy);
                        InetSocketAddress address = InetSocketAddress.createUnresolved(uri.getHost(), uri.getPort());
                        proxy = new Proxy(Proxy.Type.HTTP, address);
                    } catch (URISyntaxException ex) {
                        return;
                    }
                }
                try {
                    URLConnection test = directConnect ? url.openConnection() : url.openConnection(proxy);
                    if (configCallback != null) {
                        configCallback.accept(test);
                    }
                    test.connect();
                    if (test instanceof HttpURLConnection) {
                        HttpURLConnection htest = (HttpURLConnection) test;
                        int rcode = htest.getResponseCode();
                        if (rcode >= 400) {
                            // force the exception, should fail with IOException
                            InputStream stm = test.getInputStream();
                            try {
                                stm.close();
                            } catch (IOException ex) {
                                // swallow, we want to report just proxy failed.
                            }
                            throw new IOException(feedback.l10n("EXC_ProxyFailed", rcode));
                        }
                    }
                    conn[0] = test;
                    connected.countDown();
                } catch (IOException ex) {
                    if (directConnect) {
                        ex3.set(ex);
                    } else {
                        ex2.set(ex);
                    }
                }
            }

        }
        connectors.submit(new Connector(httpProxy));
        connectors.submit(new Connector(httpsProxy));
        connectors.submit(new Connector());
        try {
            if (!connected.await(connectDelay, TimeUnit.SECONDS)) {
                if (ex3.get() != null) {
                    throw ex3.get();
                }
                throw new ConnectException(feedback.l10n("EXC_TimeoutConnectTo", url));
            }
            if (conn[0] == null) {
                if (ex3.get() != null) {
                    throw ex3.get();
                } else if (ex2.get() != null) {
                    throw ex2.get();
                }
                throw new ConnectException(feedback.l10n("EXC_CannotConnectTo", url));
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
        return conn[0];
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
