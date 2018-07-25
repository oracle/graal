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

import java.io.File;
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
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.graalvm.component.installer.Feedback;

public class FileDownloader {
    String envHttpProxy = System.getenv("http_proxy"); // NOI18N
    String envHttpsProxy = System.getenv("https_proxy"); // NOI18N

    private static final int TRANSFER_LENGTH = 2048;
    private static final long MIN_PROGRESS_THRESHOLD = Long.getLong("org.graalvm.component.installer.minDownloadFeedback", 1024 * 1024);
    private static final int DEFAULT_CONNECT_DELAY = Integer.getInteger("org.graalvm.component.installer.connectDelaySec", 5);
    private final String fileDescription;
    private final URL sourceURL;
    private final Feedback feedback;

    private File localFile;
    private long size;
    private static boolean deleteTemporary = !Boolean.FALSE.toString().equals(System.getProperty("org.graalvm.component.installer.deleteTemporary"));
    private boolean verbose;
    private static volatile File tempDir;
    private boolean displayProgress;
    private byte[] shaDigest;
    private int connectDelay = DEFAULT_CONNECT_DELAY;
    long sizeThreshold = MIN_PROGRESS_THRESHOLD;

    public FileDownloader(String fileDescription, URL sourceURL, Feedback feedback) {
        this.fileDescription = fileDescription;
        this.sourceURL = sourceURL;
        this.feedback = feedback;
    }

    public void setShaDigest(byte[] shaDigest) {
        this.shaDigest = shaDigest;
    }

    public static void setDeleteTemporary(boolean deleteTemporary) {
        FileDownloader.deleteTemporary = deleteTemporary;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    public void setDisplayProgress(boolean displayProgress) {
        this.displayProgress = displayProgress;
    }

    public static synchronized File createTempDir() throws IOException {
        if (tempDir == null) {
            Path p = Files.createTempDirectory("graalvm_install"); // NOI18N
            tempDir = p.toFile();
            tempDir.deleteOnExit();
        }
        return tempDir;
    }

    private static File deleteOnExit(File f) {
        if (deleteTemporary) {
            f.deleteOnExit();
        }
        return f;
    }

    public String getFileDescription() {
        return fileDescription;
    }

    public URL getSourceURL() {
        return sourceURL;
    }

    private static int toKB(long size) {
        return (int) (size + 1023) / 1024;
    }

    StringBuilder progressString;
    String backspaceString;
    int startPos;
    int signCount;
    long received;
    char signChar;
    MessageDigest fileDigest;

    public File getLocalFile() {
        return localFile;
    }

    void setupProgress() {
        if (!displayProgress) {
            return;
        }
        progressString = new StringBuilder(feedback.l10n("MSG_DownloadProgress")); // NOI18N
        signChar = feedback.l10n("MSG_DownloadProgressSignChar").charAt(0); // NOI18N
        startPos = progressString.toString().indexOf(' ');
        StringBuilder bs = new StringBuilder(progressString.length());
        for (int i = 0; i < progressString.length(); i++) {
            bs.append('\b'); // NOI18N
        }
        backspaceString = bs.toString();
    }

    int cnt(long rcvd) {
        return (int) ((rcvd * 20 + (rcvd / 2)) / size);
    }

    void makeProgress(boolean first, int chunk) {
        if (!displayProgress) {
            return;
        }
        int now = cnt(received);
        received += chunk;
        int next = cnt(received);
        if (now < next) {
            progressString.setCharAt(next + startPos - 1, signChar);
            signCount = next;

            if (!first) {
                feedback.verbatimPart(backspaceString, false);
            }
            feedback.verbatimPart(progressString.toString(), false);
        }
    }

    void stopProgress() {
        if (displayProgress) {
            feedback.verbatimPart(backspaceString, false);
        }
        feedback.verboseOutput("MSG_DownloadingDone");
    }

    void updateFileDigest(ByteBuffer input) throws IOException {
        if (shaDigest == null) {
            return;
        }
        if (fileDigest == null) {
            try {
                fileDigest = MessageDigest.getInstance("SHA-256"); // NOI18N
            } catch (NoSuchAlgorithmException ex) {
                throw new IOException(
                                feedback.l10n("ERR_ComputeDigest", ex.getLocalizedMessage()),
                                ex);
            }
        }
        fileDigest.update(input);
    }

    String fingerPrint(byte[] digest) {
        StringBuilder sb = new StringBuilder(digest.length * 3);
        for (int i = 0; i < digest.length; i++) {
            if (i > 0) {
                sb.append(':');
            }
            sb.append(String.format("%02x", (digest[i] & 0xff)));
        }
        return sb.toString();
    }

    byte[] getDigest() {
        return fileDigest.digest();
    }

    void verifyDigest() throws IOException {
        if (shaDigest == null || /* for testing */ shaDigest.length == 0) {
            return;
        }
        byte[] computed = fileDigest.digest();
        if (Arrays.equals(computed, shaDigest)) {
            return;
        }
        throw new IOException(feedback.l10n("ERR_FileDigestError",
                        fingerPrint(shaDigest), fingerPrint(computed)));
    }

    private URLConnection openConnectionWithProxies(URL url) throws IOException {
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
                throw new ConnectException("Timeout while connecting to " + url);
            }
            if (conn[0] == null) {
                if (ex3.get() != null) {
                    throw ex3.get();
                } else if (ex2.get() != null) {
                    throw ex2.get();
                }
                throw new ConnectException("Cannot connect to " + url);
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
        return conn[0];
    }

    public void download() throws IOException {
        if (fileDescription != null) {
            if (!feedback.verboseOutput("MSG_DownloadingVerbose", getFileDescription(), getSourceURL())) {
                feedback.output("MSG_Downloading", getFileDescription());
            }
        } else {
            feedback.output("MSG_DownloadingFrom", getSourceURL());
        }
        URLConnection conn = openConnectionWithProxies(sourceURL);
        size = conn.getContentLengthLong();
        verbose = feedback.verbosePart("MSG_DownloadReceivingBytes", toKB(size));
        if (verbose) {
            displayProgress = true;
        }
        if (size < sizeThreshold) {
            displayProgress = false;
        }

        setupProgress();
        ByteBuffer bb = ByteBuffer.allocate(TRANSFER_LENGTH);
        localFile = deleteOnExit(File.createTempFile("download", "", createTempDir())); // NOI18N
        if (fileDescription != null) {
            feedback.bindFilename(localFile.toPath(), fileDescription);
        }
        boolean first = displayProgress;
        try (
                        ReadableByteChannel rbc = Channels.newChannel(conn.getInputStream());
                        WritableByteChannel wbc = Files.newByteChannel(localFile.toPath(),
                                        StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            int read;
            while ((read = rbc.read(bb)) >= 0) {
                if (first) {
                    feedback.verbatimPart(progressString.toString(), false);
                }
                bb.flip();
                while (bb.hasRemaining()) {
                    wbc.write(bb);
                }
                bb.flip();
                updateFileDigest(bb);
                makeProgress(first, read);
                bb.clear();
                first = false;
            }
        } catch (IOException ex) {
            // f.delete();
            throw ex;
        }
        stopProgress();
        verifyDigest();
    }
}
