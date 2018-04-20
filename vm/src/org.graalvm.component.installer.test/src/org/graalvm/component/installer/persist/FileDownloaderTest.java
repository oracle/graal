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
package org.graalvm.component.installer.persist;

import org.graalvm.component.installer.ChunkedConnection;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.graalvm.component.installer.persist.test.Handler;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import sun.net.ConnectionResetException;

/**
 *
 * @author sdedic
 */
public class FileDownloaderTest extends NetworkTestBase {
    @Rule public ExpectedException exception = ExpectedException.none();

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
    public void setUp() throws Exception {
        delegateFeedback(new FA());
        System.setProperty("org.graalvm.component.installer.minDownloadFeedback", "10");
    }

    @Test
    public void testDownloadExistingFile() throws Exception {
        URL clu = getClass().getResource("data/truffleruby2.jar");
        Handler.bind("test://graal.us.oracle.com/download/truffleruby.zip",
                        clu);

        URL u = new URL("test://graal.us.oracle.com/download/truffleruby.zip");
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
        Handler.bind("test://graal.us.oracle.com/download/truffleruby.zip",
                        clu);

        URL u = new URL("test://graal.us.oracle.com/download/truffleruby.zip");
        FileDownloader dn = new FileDownloader("test",
                        u, this);
        dn.setShaDigest(new byte[0]);
        dn.download();

        byte[] check = RemoteStorage.toHashBytes(null, "b649fe3b9309d1b3ae4d2dbae70eebd4d2978af32cd1ce7d262ebf7e0f0f53fa", this);
        assertArrayEquals(check, dn.getDigest());
    }

    class Check extends FA {
        int state;

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
        public boolean verboseOutput(String bundleKey, Object... params) {
            switch (state) {
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
        Handler.bind("test://graal.us.oracle.com/download/truffleruby.zip",
                        clu);

        URL u = new URL("test://graal.us.oracle.com/download/truffleruby.zip");
        Check check = new Check();
        delegateFeedback(check);
        FileDownloader dn = new FileDownloader("test",
                        u, this);
        dn.setVerbose(true);
        dn.setDisplayProgress(true);
        verbose = true;
        dn.download();

        assertEquals(6, check.state);
    }

    @Test
    public void testDownloadFailedProxy() throws Exception {
        URL clu = getClass().getResource("data/truffleruby2.jar");
        URL u = new URL("test://graal.us.oracle.com/download/truffleruby.zip");

        ChunkedConnection conn = new ChunkedConnection(
                        u,
                        clu.openConnection()) {
            @Override
            public void connect() throws IOException {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ex) {
                }
                super.connect();
            }

        };
        Handler.bind(u.toString(), conn);
        Check check = new Check();
        delegateFeedback(check);
        FileDownloader dn = new FileDownloader("test",
                        u, this);
        dn.setVerbose(true);
        dn.setDisplayProgress(true);

        dn.envHttpProxy = "http://localhost:11111";
        dn.envHttpsProxy = "http://localhost:11111";

        synchronized (conn) {
            conn.nextChunk = 130 * 1024;
            conn.readException = new FileNotFoundException();
        }

        exception.expect(FileNotFoundException.class);
        dn.download();
    }

    @Test
    public void testDownloadFailure() throws Exception {
        URL clu = getClass().getResource("data/truffleruby2.jar");
        URL u = new URL("test://graal.us.oracle.com/download/truffleruby.zip");

        ChunkedConnection conn = new ChunkedConnection(
                        u,
                        clu.openConnection());
        Handler.bind(u.toString(), conn);
        Check check = new Check();
        delegateFeedback(check);
        FileDownloader dn = new FileDownloader("test",
                        u, this);
        dn.setVerbose(true);
        dn.setDisplayProgress(true);
        synchronized (conn) {
            conn.nextChunk = 130 * 1024;
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
