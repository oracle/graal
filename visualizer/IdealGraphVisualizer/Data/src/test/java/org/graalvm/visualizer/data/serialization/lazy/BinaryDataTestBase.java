/*
 * Copyright (c) 2013, 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

package org.graalvm.visualizer.data.serialization.lazy;

import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Path;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.netbeans.junit.NbTestCase;
import org.openide.util.Exceptions;
import org.openide.util.RequestProcessor;

import jdk.graal.compiler.graphio.parsing.BinaryReader;
import jdk.graal.compiler.graphio.parsing.BinarySource;
import jdk.graal.compiler.graphio.parsing.Builder;
import jdk.graal.compiler.graphio.parsing.ParseMonitor;
import jdk.graal.compiler.graphio.parsing.model.GraphDocument;
import jdk.graal.compiler.graphio.parsing.model.Group;
import jdk.graal.compiler.graphio.parsing.model.InputGraph;

/**
 * @author sdedic
 */
public class BinaryDataTestBase extends NbTestCase {
    private static final Logger LOG = Logger.getLogger(BinaryDataTestBase.class.getName());

    protected static final RequestProcessor RP = new RequestProcessor(DelayedLoadTest.class);
    protected static final RequestProcessor PARALLEL_LOAD = new RequestProcessor(DelayedLoadTest.class.getName(), 100);
    protected BinaryReader reader;

    public BinaryDataTestBase(String name) {
        super(name);
    }

    protected class TestBuilder extends ScanningModelBuilder {
        public int groupCount = -1;
        public Consumer<Group> groupConsumer;
        protected BinarySource ds;

        public StreamIndex index() {
            return super.index;
        }

        public TestBuilder(BinarySource dataSource, CachedContent content, GraphDocument rootDocument, ParseMonitor monitor, ScheduledExecutorService fetchExecutor, StreamPool initialPool) {
            super(dataSource, content, rootDocument, monitor, fetchExecutor, initialPool);
            this.ds = dataSource;
        }

        @Override
        public void startGroupContent() {
            super.startGroupContent();
            if (groupLevel == 1 && groupCount >= 0 && --groupCount == 0) {
                groupConsumer.accept((Group) folder());
            }
        }

        /**
         * Creates a special Entry, which will report waitFinished through semaphore
         * to signal read after the specified freeze offset
         */
        @Override
        protected StreamEntry addEntry(StreamEntry m) {
            if (file.freezeAt == -1) {
                return super.addEntry(m);
            }
            StreamEntry se = new StreamEntry(file.id(),
                    m.getMajorVersion(), m.getMinorVersion(),
                    m.getStart(), m.getInitialPool()) {
                @Override
                void beforeWait() {
                    if (file != null && file.freezeAt != -1) {
                        long e = getEnd();
                        if (e == -1 || e >= file.freezeAt) {
                            FreezeChannel fch = currentChannel.get();
                            if (fch != null) {
                                fch.frozen.release();
                            } else {
                                file.frozen.release();
                            }
                        }
                    }
                }

            };
            se.setMetadata(m.getGraphMeta());
            if (m.isFinished()) {
                se.end(m.getEnd(), m.getSkipPool());
            }
            return super.addEntry(se);
        }

        long graphStart = -1;
        long groupStart = -1;
        int nested;

        @Override
        public InputGraph endGraph() {
            nested--;
            if (nested == 0) {
                if (LOG.isLoggable(Level.FINER)) {
                    LOG.log(Level.FINER, "\t Graph {0}: start={1}, end={2}", new Object[]{super.graph(), graphStart, ds.getMark()});
                }
                graphStart = -1;
            }
            return super.endGraph();
        }

        @Override
        public InputGraph startGraph(int dumpId, String format, Object[] args) {
            if (nested == 0 && graphStart == -1) {
                graphStart = ds.getMark();
            }
            nested++;
            return super.startGraph(dumpId, format, args);
        }

        @Override
        public Group startGroup() {
            groupStart = ds.getMark();
            Group g = super.startGroup();
            if (LOG.isLoggable(Level.FINER)) {
                LOG.log(Level.FINER, "* Start Group {0}: start={1}", new Object[]{super.folder(), groupStart});
            }
            return g;
        }

        @Override
        public void endGroup() {
            if (LOG.isLoggable(Level.FINER)) {
                LOG.log(Level.FINER, "Group {0}: start={1}, end={2}", new Object[]{super.folder(), groupStart, ds.getMark()});
            }
            groupStart = -1;
            super.endGroup();
        }
    }

    /**
     * The channel being operated on by the current thread
     */
    ThreadLocal<FreezeChannel> currentChannel = new ThreadLocal<>();

    protected class TestFileContent extends FileContent {
        public volatile long freezeAt = -1;
        private long offset;
        public Semaphore condition = new Semaphore(0);
        public Semaphore frozen = new Semaphore(0);

        public Semaphore subchannelPermits = new Semaphore(0);
        public Semaphore subchannelOpens = new Semaphore(0);
        public boolean playSemaphores;
        public Function<long[], ReadableByteChannel> channelFactory;
        public volatile boolean eof;
        public volatile Throwable throwException;
        /**
         * Last created freezable channel
         */
        public FreezeChannel lastFreezeChannel;

        public TestFileContent(Path filePath, FileChannel channel) {
            super(filePath, channel);
        }


        @Override
        public int read(ByteBuffer dst) throws IOException {
            int max = dst.remaining();
            if (max == 0) {
                // sorry
                return 0;
            }
            if (offset <= freezeAt && offset + max > freezeAt) {
                max = (int) (freezeAt - offset);
            }
            if (max == 0) {
                try {
                    long f = freezeAt;
                    // leave freezeAt as it is, so it can be used until this stream unfreezes.
                    frozen.release();
                    condition.acquire();
                    if (freezeAt == f) {
                        freezeAt = -1;
                    }
                } catch (InterruptedException ex) {
                    Exceptions.printStackTrace(ex);
                }
                if (throwException != null) {
                    Throwable t = throwException;
                    throwException = null;
                    if (t instanceof IOException) {
                        throw (IOException) t;
                    } else if (t instanceof RuntimeException) {
                        throw (RuntimeException) t;
                    } else if (t instanceof Error) {
                        throw (Error) t;
                    }
                } else if (eof) {
                    throw new EOFException();
                }
                int res = read(dst);
                // offset already updated by recursive call
                return res;
            } else {
                ByteBuffer copy = dst.duplicate();
                copy.limit(copy.position() + max);
                int bytes = super.read(copy);
                if (bytes == -1) {
                    return bytes;
                }
                dst.position(dst.position() + bytes);
                offset += bytes;
                return bytes;
            }
        }

        void permitSubchannelAndWait() throws Exception {
            subchannelPermits.release();
            subchannelOpens.acquire();
        }

        @Override
        public ReadableByteChannel subChannel(long start, long end) throws IOException {
            if (playSemaphores) {
                try {
                    subchannelPermits.acquire();
                } catch (InterruptedException ex) {
                    throw new InterruptedIOException();
                }
                subchannelOpens.release();
            }
            if (channelFactory != null) {
                ReadableByteChannel ch = channelFactory.apply(new long[]{start, end});
                if (ch instanceof FreezeChannel) {
                    lastFreezeChannel = (FreezeChannel) ch;
                    currentChannel.set(lastFreezeChannel);
                }
                return ch;
            } else {
                if (freezeAt != -1) {
                    // freeze all channels
                    lastFreezeChannel = new FreezeChannel(start, end, freezeAt);
                    currentChannel.set(lastFreezeChannel);
                    return lastFreezeChannel;
                } else {
                    return super.subChannel(start, end);
                }
            }
        }

        ReadableByteChannel superSubChannel(long start, long end) throws IOException {
            return super.subChannel(start, end);
        }
    }

    protected TestFileContent file;
    protected TestBuilder mb;
    protected GraphDocument checkDocument;
    protected BinarySource scanSource;
    protected StreamPool streamPool = new StreamPool();
    /**
     * executor which will load/complete lazy groups and graphs
     */
    protected ScheduledExecutorService loadExecutor = RP;

    protected Builder createScanningTestBuilder() {
        mb = new TestBuilder(scanSource, file, checkDocument,
                null, loadExecutor, streamPool);
        return mb;
    }

    protected void loadData(String dFile) throws Exception {
        URL bigv = DelayedLoadTest.class.getResource(dFile);
        File f = new File(bigv.toURI());
        loadData(f);
    }

    protected void loadData(File f) throws Exception {
        file = new TestFileContent(f.toPath(), null);
        checkDocument = new GraphDocument();
        scanSource = new BinarySource(null, file);
        Builder b = createScanningTestBuilder();
        reader = new BinaryReader(scanSource, b);
    }

    static {
        LoadSupport._testUseWeakRefs = true;
    }

    protected void run(Runnable r) {
        r.run();
    }

    class FreezeChannel extends org.graalvm.visualizer.data.serialization.lazy.FreezeChannel {
        public FreezeChannel(long start, long end, long freezeAt) throws IOException {
            super(file.superSubChannel(start, end), start, freezeAt);
        }
    }

    protected FreezeChannel freezeChannel;
}
