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

import java.io.File;
import java.io.IOException;
import java.nio.channels.ReadableByteChannel;
import java.util.*;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;

import jdk.graal.compiler.graphio.parsing.*;
import jdk.graal.compiler.graphio.parsing.model.GraphDocument;
import jdk.graal.compiler.graphio.parsing.model.Group;
import jdk.graal.compiler.graphio.parsing.model.InputGraph;

/**
 * @author sdedic
 */
public class ScanningBuilderTest extends BinaryDataTestBase {

    int builderType;

    public ScanningBuilderTest(String name) {
        super(name);
    }

    PosTestBuilder posBuilder;

    Semaphore alert = new Semaphore(0);

    /**
     * Complete and frozen snaphots at start of each root.
     */
    Map<Long, ConstantPool> poolSnaphots = new LinkedHashMap<>();

    class PosTestBuilder extends TestBuilder {
        BinarySource dataSource;
        Semaphore blockScanning = new Semaphore(0);
        boolean next;

        public PosTestBuilder(BinarySource dataSource, CachedContent content, GraphDocument rootDocument, ParseMonitor monitor, ScheduledExecutorService fetchExecutor, StreamPool initialPool) {
            super(dataSource, content, rootDocument, monitor, fetchExecutor, initialPool);
            this.dataSource = dataSource;
        }

        long mark;

        @Override
        public InputGraph startGraph(int dumpId, String formta, Object[] args) {
            record();
            return super.startGraph(dumpId, formta, args);
        }

        @Override
        public Group startGroup() {
            record();
            return super.startGroup();
        }

        void record() {
            poolSnaphots.put(mark, new ConstantPool(getConstantPool().copyData()));
        }

        @Override
        public void startRoot() {
            mark = dataSource.getMark();
            super.startRoot();
        }
    }

    protected Builder createScanningTestBuilder() {
        switch (builderType) {
            case 1:
                return posBuilder = new PosTestBuilder(scanSource, file, checkDocument, null, PARALLEL_LOAD, streamPool);
        }
        return super.createScanningTestBuilder();
    }

    class VerifyPool extends ConstantPool {
        ConstantPool saved;
        ConstantPool delegate;

        public VerifyPool(ConstantPool delegate, ConstantPool saved) {
            this.delegate = delegate;
            this.saved = saved;
        }

        VerifyPool(List<Object> data, ConstantPool delegate, ConstantPool saved) {
            super(data);
            this.delegate = delegate;
            this.saved = saved;
        }

        protected VerifyPool create(List<Object> data) {
            return new VerifyPool(data, delegate, saved);
        }

        @Override
        public synchronized Object addPoolEntry(int index, Object obj, long where) {
            saved.addPoolEntry(index, obj, where);
            return delegate.addPoolEntry(index, obj, where);
            //return super.addPoolEntry(index, obj, where);
        }

        @Override
        public Object get(int index, long where) {
            Object x = delegate.get(index, where);
            Object prev = saved.get(index, where);
            if (!prev.equals(x)) {
                assertEquals("Constant pool corrupt at index " + index + ", stream position " + where, prev, x);
            }
            return x;
        }

        @Override
        public int size() {
            return delegate.size();
        }

        @Override
        public ConstantPool restart() {
            saved.restart();
            return delegate = delegate.restart();
        }
    }

    public void testWaitForOffset() throws Exception {

    }

    public void testWaitForEntry() throws Exception {

    }

    class VerifyValuePool extends StreamPool {
        Map<Integer, Object> values = new HashMap<>();

        public VerifyValuePool() {
        }

        public VerifyValuePool(int generation, List<Object> data) {
            super(generation, data);
        }

        protected VerifyValuePool create(List<Object> data) {
            return new VerifyValuePool(generation + 1, data);
        }

        @Override
        public synchronized Object addPoolEntry(int index, Object obj, long where) {
            return super.addPoolEntry(index, obj, where);
        }

        @Override
        public Object get(int index, long where) {
            Object x = super.get(index, where);
            Object prev = values.put(index, x);
            assert prev == null || prev == x || modified();
            return x;
        }
    }

    public void testEntriesAreFinishedAtEnd() throws Exception {
        streamPool = new VerifyValuePool();
        loadData("mega2.bgv");
        reader.parse();
        for (Iterator<StreamEntry> it = mb.index().iterator(); it.hasNext(); ) {
            StreamEntry se = it.next();
            assertNotNull(se.getSkipPool());
        }
    }

    public class PoolVerifier extends SingleGroupBuilder.Ignore {
        long entryStart;

        public PoolVerifier(GraphDocument rootDocument) {
            super(rootDocument, null);
        }

        @Override
        public void startRoot() {
            super.startRoot();
        }

        @Override
        public void startGroupContent() {
            // skip contents of groups -- uninteresting
            throw new SkipRootException(entryStart, -1, null);
        }
    }

    /**
     * During initial scanning, saves exact copy of a constant pool at root start. Then, re-parses each
     * entry (group, graph), working with the initial pool copy AND the saved pool in parallel. Verifies that
     * the saved pool and the StreamEntry's pool provide the same answers/values.
     */
    public void testBadConstantPool() throws Exception {
        builderType = 1;
        // make scan, record entries
        loadData("mega2.bgv");
        GraphDocument rd = reader.parse();

        for (Map.Entry<Long, ConstantPool> en : poolSnaphots.entrySet()) {
            long offset = en.getKey();
            ConstantPool savedCopy = en.getValue();
            StreamEntry sen = posBuilder.index.get(offset);
            if (sen == null) {
                continue;
            }
            VerifyPool vp = new VerifyPool(sen.getInitialPool().copy(), savedCopy);
            Builder b = new PoolVerifier(rd);
            BinarySource src = new BinarySource(
                    file.subChannel(sen.getStart(), sen.getEnd()),
                    sen.getMajorVersion(), sen.getMinorVersion(), sen.getStart());
            BinaryReader entryReader = new BinaryReader(src, b);
            entryReader.setConstantPool(vp);

            entryReader.parse();
        }
    }

    NetworkStreamContent content;
    ReadableByteChannel dataChannel;

    private void initContent() throws IOException {
        File name = File.createTempFile("igvtest_", "");
        name.delete();
        name.mkdirs();
        content = new NetworkStreamContent(dataChannel, name);
    }

    public void testSunday() throws Exception {
        initContent();
    }
}
