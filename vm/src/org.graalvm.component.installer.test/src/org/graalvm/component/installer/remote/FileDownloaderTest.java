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

import org.graalvm.component.installer.ChunkedConnection;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.graalvm.component.installer.SystemUtils;
import org.graalvm.component.installer.persist.NetworkTestBase;
import org.graalvm.component.installer.persist.test.Handler;
import org.junit.Assert;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;
import sun.net.ConnectionResetException;

public class FileDownloaderTest extends NetworkTestBase {

    class FA extends FeedbackAdapter {

        @Override
        public String l10n(String key, Object... params) {
            switch (key) {
                case "MSG_DownloadProgress":
                    return "[                    ]";
                case "MSG_DownloadProgressSignChar":
                    return "#";
            }
            return key;
        }

    }

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();
        delegateFeedback(new FA());
        Handler.clear();
    }

    @Test
    public void testDownloadExistingFile() throws Exception {
        URL clu = getClass().getResource("data/truffleruby2.jar");
        Handler.bind("test://graalvm.io/download/truffleruby.zip",
                        clu);

        URL u = new URL("test://graalvm.io/download/truffleruby.zip");
        FileDownloader dn = new FileDownloader("test",
                        u, this);
        dn.download();

        File local = dn.getLocalFile();
        assertTrue(local.exists());
        assertTrue(local.isFile());

        URLConnection c = clu.openConnection();
        assertEquals(c.getContentLengthLong(), local.length());
    }

    @Test
    public void testDownloadComputeDigest() throws Exception {
        URL clu = getClass().getResource("data/truffleruby2.jar");
        Handler.bind("test://graalvm.io/download/truffleruby.zip",
                        clu);

        URL u = new URL("test://graalvm.io/download/truffleruby.zip");
        FileDownloader dn = new FileDownloader("test",
                        u, this);
        dn.setShaDigest(new byte[0]);
        dn.download();

        byte[] check = SystemUtils.toHashBytes("b649fe3b9309d1b3ae4d2dbae70eebd4d2978af32cd1ce7d262ebf7e0f0f53fa");
        assertArrayEquals(check, dn.getReceivedDigest());
    }

    class Check extends FA {
        int state;
        boolean verbose = true;
        int cnt = 0;
        StringBuilder bar = new StringBuilder("[                    ]");
        String bcksp = "\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b";

        @Override
        public boolean verbosePart(String bundleKey, Object... params) {
            switch (state) {
                case 0:
                    if ("MSG_DownloadReceivingBytes".equals(bundleKey)) {
                        state++;
                    }
                    break;
            }
            return super.verbosePart(bundleKey, params);
        }

        @Override
        public void output(String bundleKey, Object... params) {
            if (state == 0 && verbose) {
                Assert.assertNotEquals("MSG_Downloading", bundleKey);
            }
        }

        @Override
        public boolean verboseOutput(String bundleKey, Object... params) {
            switch (state) {
                case 0:
                    assertEquals("MSG_DownloadingVerbose", bundleKey);
                    break;
                case 5:
                    if ("MSG_DownloadingDone".equals(bundleKey)) {
                        state++;
                    }
                    break;
            }
            return super.verboseOutput(bundleKey, params);
        }

        @Override
        public boolean verbatimPart(String msg, boolean beVerbose) {
            switch (state) {
                case 1:
                    assertEquals(bar.toString(), msg);
                    state++;
                    break;
                case 3:
                    cnt++;
                    bar.setCharAt(cnt, '#');
                    assertEquals(bar.toString(), msg);
                    if (cnt >= 20) {
                        state = 4;
                    } else {
                        state = 2;
                    }
                    break;
                case 2:
                    assertEquals(msg, bcksp);
                    state++;
                    break;
                case 4:
                    assertEquals(msg, bcksp);
                    state++;
                    break;
            }
            return super.verbatimPart(msg, beVerbose);
        }

    }

    @Test
    public void testDownloadVerboseMessages() throws Exception {
        URL clu = getClass().getResource("data/truffleruby2.jar");
        Handler.bind("test://graalvm.io/download/truffleruby.zip",
                        clu);

        URL u = new URL("test://graalvm.io/download/truffleruby.zip");
        Check check = new Check();
        delegateFeedback(check);
        FileDownloader dn = new FileDownloader("test",
                        u, this);
        dn.setVerbose(true);
        dn.setDisplayProgress(true);
        dn.sizeThreshold = 10;
        verbose = true;
        dn.download();

        assertEquals(6, check.state);
    }

    /**
     * Checks that slow proxy will be used although the direct connection has failed already.
     * 
     * @throws Exception
     */
    @Test
    public void testDownloadSlowProxy() throws Exception {
        URL clu = getClass().getResource("data/truffleruby2.jar");
        URL u = new URL("test://graalvm.io/download/truffleruby.zip");

        ChunkedConnection proxyConnect = new ChunkedConnection(
                        u,
                        clu.openConnection()) {
            @Override
            public void connect() throws IOException {
                try {
                    // called twice, default timeout is 5 secs
                    Thread.sleep(1000);
                } catch (InterruptedException ex) {
                }
                super.connect();
            }

        };

        Handler.bindProxy(u.toString(), proxyConnect);
        Check check = new Check();
        delegateFeedback(check);
        FileDownloader dn = new FileDownloader("test",
                        u, this);
        ProxyConnectionFactory pcf = new ProxyConnectionFactory(this, u);
        dn.setConnectionFactory(pcf);

        verbose = true;
        dn.setVerbose(true);
        dn.setDisplayProgress(true);

        pcf.envHttpProxy = "http://localhost:11111";
        pcf.envHttpsProxy = "http://localhost:11111";

        dn.download();
        URLConnection c = clu.openConnection();
        assertEquals(c.getContentLengthLong(), dn.getLocalFile().length());
    }

    /**
     * Checks that if proxy fails, the direct connection, although it connects later, will be used.
     * 
     * @throws Exception
     */
    @Test
    public void testDownloadFailedProxy() throws Exception {
        URL clu = getClass().getResource("data/truffleruby2.jar");
        URL u = new URL("test://graalvm.io/download/truffleruby.zip");

        ChunkedConnection directConnect = new ChunkedConnection(
                        u,
                        clu.openConnection()) {
            @Override
            public void connect() throws IOException {
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException ex) {
                }
                super.connect();
            }

        };

        Handler.bind(u.toString(), directConnect);
        Check check = new Check();
        check.verbose = false;
        delegateFeedback(check);
        FileDownloader dn = new FileDownloader("test",
                        u, this);
        dn.setVerbose(true);
        dn.setDisplayProgress(true);

        ProxyConnectionFactory pcf = new ProxyConnectionFactory(this, u);
        dn.setConnectionFactory(pcf);

        pcf.envHttpProxy = "http://localhost:11111";
        pcf.envHttpsProxy = "http://localhost:11111";

        synchronized (directConnect) {
            directConnect.nextChunk = 130 * 1024;
            directConnect.readException = new FileNotFoundException();
        }

        exception.expect(FileNotFoundException.class);
        dn.download();
    }

    /**
     * Checks that a proxy connection which results in HTTP 500 will not override delayed direct
     * connection.
     * 
     * @throws Exception
     */
    @Test
    public void testDownloadProxy500() throws Exception {
        URL clu = getClass().getResource("data/truffleruby2.jar");
        URL u = new URL("test://graalvm.io/download/truffleruby.zip");

        ChunkedConnection directConnect = new ChunkedConnection(
                        u,
                        clu.openConnection()) {
            @Override
            public void connect() throws IOException {
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException ex) {
                }
                super.connect();
            }
        };

        HttpURLConnection proxyCon = new HttpURLConnection(u) {
            @Override
            public void disconnect() {
            }

            @Override
            public boolean usingProxy() {
                return true;
            }

            @Override
            public void connect() throws IOException {
                responseCode = 500;
            }
        };

        Handler.bind(u.toString(), directConnect);
        Handler.bindProxy(u.toString(), proxyCon);
        Check check = new Check();
        check.verbose = false;
        delegateFeedback(check);
        FileDownloader dn = new FileDownloader("test",
                        u, this);
        dn.setVerbose(true);
        dn.setDisplayProgress(true);

        ProxyConnectionFactory pcf = new ProxyConnectionFactory(this, u);
        dn.setConnectionFactory(pcf);

        pcf.envHttpProxy = "http://localhost:11111";
        pcf.envHttpsProxy = "http://localhost:11111";

        dn.download();
    }

    @Test
    public void testDownloadFailure() throws Exception {
        URL clu = getClass().getResource("data/truffleruby2.jar");
        URL u = new URL("test://graalvm.io/download/truffleruby.zip");

        ChunkedConnection conn = new ChunkedConnection(
                        u,
                        clu.openConnection());
        Handler.bind(u.toString(), conn);
        Check check = new Check();
        check.verbose = false;
        delegateFeedback(check);
        FileDownloader dn = new FileDownloader("test",
                        u, this);
        dn.setVerbose(true);
        dn.setDisplayProgress(true);
        synchronized (conn) {
            conn.nextChunk = 130 * 1024;

            ProxyConnectionFactory pcf = new ProxyConnectionFactory(this, u);
            dn.setConnectionFactory(pcf);

            pcf.envHttpProxy = null;
            pcf.envHttpsProxy = null;
        }

        AtomicReference<Throwable> exc = new AtomicReference<>();
        Thread t = new Thread() {
            @Override
            public void run() {
                try {
                    dn.download();
                } catch (Throwable x) {
                    exc.set(x);
                }
            }
        };

        t.start();

        assertTrue(conn.reachedSem.tryAcquire(1, TimeUnit.SECONDS));
        // conn.reachedSem.acquire();
        conn.readException = new ConnectionResetException();
        conn.nextSem.release();
        t.join(1000);
        // t.join();
        assertFalse(t.isAlive());
        assertTrue(exc.get() instanceof IOException);
    }
}
