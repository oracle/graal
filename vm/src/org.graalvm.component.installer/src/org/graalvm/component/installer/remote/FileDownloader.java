/*
 * Copyright (c) 2018, 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import org.graalvm.component.installer.CommonConstants;
import org.graalvm.component.installer.Feedback;
import org.graalvm.component.installer.SystemUtils;
import org.graalvm.component.installer.URLConnectionFactory;
import java.util.Collections;
import java.util.List;

/**
 * Downloads file to local, optionally checks its integrity using digest.
 *
 * @author sdedic
 */
public final class FileDownloader {
    private static final int TRANSFER_LENGTH = 2048;
    private static final long MIN_PROGRESS_THRESHOLD = Long.getLong("org.graalvm.component.installer.minDownloadFeedback", 1024 * 1024);
    private final String fileDescription;
    private final URL sourceURL;
    private final Feedback feedback;

    private File downloadDir;
    private File localFile;
    private long size;
    private static boolean deleteTemporary = !Boolean.FALSE.toString().equals(System.getProperty("org.graalvm.component.installer.deleteTemporary"));
    private boolean verbose;
    private static volatile File tempDir;
    private boolean displayProgress;
    private byte[] shaDigest;
    long sizeThreshold = MIN_PROGRESS_THRESHOLD;
    private final Map<String, String> requestHeaders = new HashMap<>();
    private Consumer<SeekableByteChannel> dataInterceptor;
    private URLConnectionFactory connectionFactory;
    private boolean simpleOutput;

    private Map<String, List<String>> responseHeader = Collections.emptyMap();
    private DownloadExceptionInterceptor downloadExceptionInterceptor = (ex, fd) -> ex;

    public interface DownloadExceptionInterceptor {
        /**
         * When null is returned another connection will be attempted. Otherwise the returned
         * Exception is thrown.
         */
        IOException interceptDownloadException(IOException downloadException, FileDownloader fileDownloader);
    }

    public Map<String, List<String>> getResponseHeader() {
        return responseHeader;
    }

    public Map<String, String> getRequestHeaders() {
        return Collections.unmodifiableMap(requestHeaders);
    }

    /**
     * Will intercept possible connection problem, if null is returned the downloader will do
     * another attempt to download otherwise the returned Exception is thrown.
     *
     * @param downloadExceptionInterceptor
     */
    public void setDownloadExceptionInterceptor(DownloadExceptionInterceptor downloadExceptionInterceptor) {
        if (downloadExceptionInterceptor != null) {
            this.downloadExceptionInterceptor = downloadExceptionInterceptor;
        }
    }

    /**
     * Algorithm to compute file digest. By default SHA-256 is used.
     */
    private String digestAlgorithm = "SHA-256";

    public FileDownloader(String fileDescription, URL sourceURL, Feedback feedback) {
        this.fileDescription = fileDescription;
        this.sourceURL = sourceURL;
        this.feedback = feedback.withBundle(FileDownloader.class);
    }

    public void setShaDigest(byte[] shaDigest) {
        this.shaDigest = shaDigest;
    }

    public File getDownloadDir() {
        return downloadDir;
    }

    public void setDownloadDir(File downloadDir) {
        this.downloadDir = downloadDir;
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

    public void addRequestHeader(String header, String val) {
        requestHeaders.put(header, val);
    }

    public String getDigestAlgorithm() {
        return digestAlgorithm;
    }

    public void setDigestAlgorithm(String digestAlgorithm) {
        this.digestAlgorithm = digestAlgorithm;
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
    byte[] receivedDigest;

    public File getLocalFile() {
        return localFile;
    }

    void setupProgress() {
        if (simpleOutput) {
            feedback.output("MSG_ProgressStart_Simple@", Long.toString(size));
            return;
        }
        if (!displayProgress) {
            return;
        }
        progressString = new StringBuilder(feedback.l10n("MSG_DownloadProgress@")); // NOI18N
        signChar = feedback.l10n("MSG_DownloadProgressSignChar@").charAt(0); // NOI18N
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
            if (simpleOutput) {
                feedback.output("MSG_Progress_Simple@", Long.toString(received));
                return;
            }
            progressString.setCharAt(next + startPos - 1, signChar);
            signCount = next;

            if (!first) {
                feedback.verbatimPart(backspaceString, false);
            }
            feedback.verbatimPart(progressString.toString(), false);
        }
    }

    void stopProgress(boolean success) {
        if (displayProgress && !simpleOutput) {
            feedback.verbatimPart(backspaceString, false);
        }
        String simpleSuffix = simpleOutput ? "_Simple@" : "";
        if (success) {
            feedback.verboseOutput("MSG_DownloadingDone" + simpleSuffix);
        } else {
            feedback.output("MSG_DownloadingTerminated" + simpleSuffix);
        }
    }

    void updateFileDigest(ByteBuffer input) throws IOException {
        if (shaDigest == null) {
            return;
        }
        if (fileDigest == null) {
            try {
                fileDigest = MessageDigest.getInstance(getDigestAlgorithm()); // NOI18N
            } catch (NoSuchAlgorithmException ex) {
                throw new IOException(
                                feedback.l10n("ERR_ComputeDigest", ex.getLocalizedMessage()),
                                ex);
            }
        }
        fileDigest.update(input);
    }

    static String fingerPrint(byte[] digest) {
        return SystemUtils.fingerPrint(digest);
    }

    byte[] getDigest() {
        return fileDigest.digest();
    }

    public byte[] getReceivedDigest() throws IOException {
        if (receivedDigest == null) {
            if (localFile == null) {
                return null;
            }
            receivedDigest = SystemUtils.computeFileDigest(localFile.toPath(), getDigestAlgorithm());
        }
        return receivedDigest == null ? null : receivedDigest.clone();
    }

    void verifyDigest() throws IOException {
        if (shaDigest == null || /* for testing */ shaDigest.length == 0) {
            return;
        }
        byte[] computed = fileDigest.digest();
        this.receivedDigest = computed;
        if (Arrays.equals(computed, shaDigest)) {
            return;
        }
        throw new IOException(feedback.l10n("ERR_FileDigestError",
                        fingerPrint(shaDigest), fingerPrint(computed)));
    }

    void configureHeaders(URLConnection con) {
        for (String h : requestHeaders.keySet()) {
            con.addRequestProperty(h, requestHeaders.get(h));
        }
    }

    protected void dataDownloaded(SeekableByteChannel ch) {
        if (dataInterceptor != null) {
            dataInterceptor.accept(ch);
        }
    }

    public FileDownloader setDataInterceptor(Consumer<SeekableByteChannel> interceptor) {
        this.dataInterceptor = interceptor;
        return this;
    }

    private void copySubtree(Path from) throws IOException {
        Path to = Files.createTempDirectory(createTempDir().toPath(), "download");
        SystemUtils.copySubtree(from, to);
        localFile = to.toFile();
    }

    private int attempt;

    public int getAttemptNr() {
        return attempt;
    }

    public void download() throws IOException {
        Path localCache = feedback.getLocalCache(sourceURL);
        if (localCache != null) {
            feedback.verboseOutput("MSG_Loaded_Cache", sourceURL, localCache);
            localFile = localCache.toFile();
            Map<String, List<String>> respCache = feedback.getLocalResponseHeadersCache(sourceURL);
            responseHeader = respCache == null ? Collections.emptyMap() : respCache;
            return;
        }

        simpleOutput = Boolean.TRUE.toString().equals(System.getProperty(CommonConstants.SYSPROP_SIMPLE_OUTPUT));
        boolean fromFile = sourceURL.getProtocol().equals("file");
        if (simpleOutput) {
            feedback.output(
                            "MSG_Downloading_Simple@",
                            getSourceURL(), getFileDescription() == null ? "" : getFileDescription());
        } else {
            if (fileDescription != null) {
                if (!feedback.verboseOutput("MSG_DownloadingVerbose", getFileDescription(), getSourceURL())) {
                    feedback.output(fromFile ? "MSG_UsingFile" : "MSG_Downloading", getFileDescription(), getSourceURL().getHost());
                }
            } else {
                feedback.output("MSG_DownloadingFrom", getSourceURL());
            }
        }

        if (fromFile) {
            try {
                Path p = Paths.get(sourceURL.toURI());
                if (Files.isDirectory(p)) {
                    copySubtree(p);
                    return;
                }
            } catch (URISyntaxException ex) {
                throw new IOException(ex);
            }
        }

        URLConnectionFactory urlFactory = getConnectionFactory();
        URLConnection conn = null;
        attempt = 0;
        do {
            try {
                attempt++;
                conn = urlFactory.createConnection(sourceURL, this::configureHeaders);
            } catch (IOException ex) {
                if ((ex = downloadExceptionInterceptor.interceptDownloadException(ex, this)) != null) {
                    throw ex;
                }
            }
        } while (conn == null);

        size = conn.getContentLengthLong();
        if (simpleOutput) {
            verbose = feedback.verboseOutput(null);
        } else {
            verbose = feedback.verbosePart("MSG_DownloadReceivingBytes", toKB(size));
        }
        if (verbose) {
            displayProgress = true;
        }
        if (size < sizeThreshold) {
            displayProgress = false;
        }

        setupProgress();
        ByteBuffer bb = ByteBuffer.allocate(TRANSFER_LENGTH);
        localFile = deleteOnExit(File.createTempFile("download", "", downloadDir == null ? createTempDir() : downloadDir)); // NOI18N
        boolean first = displayProgress;
        boolean success = false;
        try (
                        ReadableByteChannel rbc = Channels.newChannel(conn.getInputStream());
                        SeekableByteChannel wbc = Files.newByteChannel(localFile.toPath(),
                                        StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            int read;
            while ((read = rbc.read(bb)) >= 0) {
                if (first && !simpleOutput) {
                    feedback.verbatimPart(progressString.toString(), false);
                }
                bb.flip();
                while (bb.hasRemaining()) {
                    wbc.write(bb);
                    long pos = wbc.position();
                    dataDownloaded(wbc);
                    wbc.position(pos);
                }
                bb.flip();
                updateFileDigest(bb);
                makeProgress(first, read);
                bb.clear();
                first = false;
            }
            success = true;
        } catch (UncheckedIOException ex) {
            throw ex.getCause();
        } catch (IOException ex) {
            // f.delete();
            throw ex;
        } finally {
            stopProgress(success);
        }
        verifyDigest();
        responseHeader = conn.getHeaderFields();
        feedback.addLocalFileCache(sourceURL, localFile.toPath());
        feedback.addLocalResponseHeadersCache(sourceURL, responseHeader);
    }

    public void setConnectionFactory(URLConnectionFactory connFactory) {
        this.connectionFactory = connFactory;
    }

    URLConnectionFactory getConnectionFactory() {
        if (connectionFactory == null) {
            connectionFactory = new ProxyConnectionFactory(feedback, sourceURL);
        }
        return connectionFactory;
    }
}
