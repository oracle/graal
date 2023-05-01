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
import java.net.URL;
import java.nio.channels.FileChannel;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import junit.framework.TestCase;
import static junit.framework.TestCase.assertEquals;
import org.graalvm.visualizer.data.Folder;
import org.graalvm.visualizer.data.FolderElement;
import org.graalvm.visualizer.data.GraphDocument;
import org.graalvm.visualizer.data.Group;
import org.graalvm.visualizer.data.InputGraph;
import org.graalvm.visualizer.data.serialization.BinaryParser;
import org.graalvm.visualizer.data.serialization.BinaryReader;
import org.graalvm.visualizer.data.serialization.BinarySource;
import org.graalvm.visualizer.data.serialization.ConstantPool;
import org.graalvm.visualizer.data.serialization.FileContent;
import org.graalvm.visualizer.data.serialization.ModelBuilder;
import org.graalvm.visualizer.data.services.GroupCallback;
import org.junit.Assert;
import static org.junit.Assert.assertNotEquals;
import org.openide.util.Exceptions;
import org.openide.util.RequestProcessor;

/**
 *
 */
public class ScanningBinaryParserTest extends TestCase {
    public ScanningBinaryParserTest(String name) {
        super(name);
    }

    private static class TrackConstantPool extends StreamPool {
        protected List<CPData> cpReads = new ArrayList<>();

        public TrackConstantPool() {
        }

        public TrackConstantPool(List<Object> data, int generation, List<CPData> cpReads) {
            super(generation, data);
        }

        @Override
        public Object get(int index, long where) {
            Object o = super.get(index, where);
            cpReads.add(new CPData(true, index, o));
            return o;
        }

        @Override
        public synchronized Object addPoolEntry(int index, Object obj, long where) {
            Object o = super.addPoolEntry(index, obj, where);
            cpReads.add(new CPData(false, index, o));
            return o;
        }

        void reset() {
            cpReads.clear();
        }

        @Override
        protected StreamPool create(List<Object> data) {
            return new TrackConstantPool(data, generation + 1, cpReads);
        }
    }

    private static class TrackConstantPool2 extends TrackConstantPool {
        private ConstantPool delegate;

        public TrackConstantPool2(ConstantPool delegate) {
            this.delegate = delegate;
        }

        @Override
        public Object get(int index, long where) {
            Object o = delegate.get(index, where);
            cpReads.add(new CPData(true, index, o));
            return o;
        }

        @Override
        public synchronized Object addPoolEntry(int index, Object obj, long where) {
            Object o = delegate.addPoolEntry(index, obj, where);
            cpReads.add(new CPData(false, index, o));
            return o;
        }

        void reset() {
            cpReads.clear();
        }

        @Override
        public int size() {
            return delegate.size();
        }
    }

    AtomicInteger groupIndex = new AtomicInteger();

    void verifyScanningAndBinary() {
        assertEquals(mockScanning.start, mockBinary.start);
        assertEquals(mockScanning.end, mockBinary.end);

        TrackConstantPool tPoolB = (TrackConstantPool) mockBinary.getConstantPool();
        TrackConstantPool tPoolS = (TrackConstantPool) mockScanning.getConstantPool();
        assertEquals(tPoolB.cpReads.size(), tPoolS.cpReads.size());

        int max = tPoolS.cpReads.size();
        for (int i = 0; i < max; i++) {
            CPData d1 = tPoolS.cpReads.get(i);
            CPData d2 = tPoolB.cpReads.get(i);

            assertEquals("inconsistent index, operation " + i + ", index = " + d2.index + " / scanIndex = " + d1.index, d2.index, d1.index);
            assertEquals("inconsistent read/write, operation " + i + ", index = " + d1.index, d2.read, d1.read);

            assertEquals("Inconsistent data on operation " + i + ", index " + d1.index, d2.data.toString(), d1.data.toString());
        }
    }

    private MockScanningParser mockScanning;
    private MockBinaryParser mockBinary;

    private Semaphore waitScanning = new Semaphore(0);
    private Semaphore waitBinary = new Semaphore(0);

    private static class CPData {
        private boolean read;
        private int index;
        private Object data;

        public CPData(boolean read, int index, Object data) {
            this.read = read;
            this.index = index;
            this.data = data;
        }

        @Override
        public int hashCode() {
            int hash = 7;
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final CPData other = (CPData) obj;
            if (this.read != other.read) {
                return false;
            }
            if (this.index != other.index) {
                return false;
            }
            if (!Objects.equals(this.data, other.data)) {
                return false;
            }
            return true;
        }
    }

    private volatile Group binaryGroup;
    private volatile Throwable compareError;

    private class MockBinaryParser extends BinaryParser {
        long start;
        long end;
        Group closingGroup;
        BinarySource dSource;

        public MockBinaryParser(BinarySource dataSource, TrackConstantPool pool, GraphDocument rootDocument, GroupCallback callback) {
            super(dataSource, pool, rootDocument, callback);
            this.dSource = dataSource;
        }

        @Override
        protected void registerGraph(Folder parent, FolderElement graph) {
        }

        @Override
        protected void beginGroup(Folder parent) throws IOException {
            if (parent instanceof GraphDocument) {
                start = dSource.getMark();
            }
            super.beginGroup(parent);
        }

        private void rethrowCompareError() throws IOException {
            if (compareError != null) {
                if (compareError instanceof IOException) {
                    throw (IOException) compareError;
                } else if (compareError instanceof Error) {
                    throw (Error) compareError;
                }
            }
        }

        @Override
        protected void closeGroup(Group g) throws IOException {
            super.closeGroup(g);
            if (g.getParent() instanceof GraphDocument) {
                binaryGroup = g;
                System.err.println("Parsed: " + g.getName());
                end = dSource.getMark();
                waitBinary.release();
                rethrowCompareError();
                try {
                    waitScanning.acquire();
                } catch (InterruptedException ex) {
                    Exceptions.printStackTrace(ex);
                }
                rethrowCompareError();
                groupIndex.incrementAndGet();
                ((TrackConstantPool) getConstantPool()).reset();
            }
        }
    }

    /*

    public void xtestScanningParserReadsTheSame() throws Exception {
        Path p = Paths.get(DIR_NAME);
        for (File f : p.toFile().listFiles()) {
            System.err.println("Checking file: " + f);


            FileChannel fch = FileChannel.open(f.toPath(), StandardOpenOption.READ);
            FileChannel fch2 = FileChannel.open(f.toPath(), StandardOpenOption.READ);
            GraphDocument checkDocument = new GraphDocument();
            BinarySource scanSource = new BinarySource(fch2);
            MockScanningParser msp = new MockScanningParser(scanSource, null, new TrackConstantPool(), checkDocument);
            this.mockScanning = msp;
            RequestProcessor.getDefault().post(msp);
            GraphDocument rootDocument = new GraphDocument();
            this.mockBinary = new MockBinaryParser(new BinarySource(fch), new TrackConstantPool(), rootDocument, null);
            GraphDocument r = mockBinary.parse();
            assertNotNull(r);
        }
    }

    public void testPartialParserSame() throws Exception {
        Path p = Paths.get(DIR_NAME);
        for (File f : p.toFile().listFiles()) {
            System.err.println("Chekcing file: " + f);
            checkReadFile(f);
        }
    }
     */
    class MockScanningParser {
        long start;
        long end;

        ConstantPool getConstantPool() {
            return null;
        }
    }

    static final RequestProcessor RP = new RequestProcessor(ScanningBinaryParserTest.class);

    /**
     * Check that primitive types like integers are transfered properly.
     */
    public void testReadData30() throws Exception {
        URL bigv = ScanningBinaryParserTest.class.getResource("bigv-3.0.bgv");
        /* this graph has been generated with following patch in graal-core repo:
index 9e2fccf..eca9e3e 100644
--- a/compiler/src/org.graalvm.compiler.printer/src/org/graalvm/compiler/printer/BinaryGraphPrinter.java
+++ b/compiler/src/org.graalvm.compiler.printer/src/org/graalvm/compiler/printer/BinaryGraphPrinter.java
@@ -170,16 +170,18 @@ public class BinaryGraphPrinter implements GraphPrinter {
     public SnippetReflectionProvider getSnippetReflectionProvider() {
         return snippetReflection;
     }
+    static int cnt = 0;

     @Override
     public void print(Graph graph, Map<Object, Object> properties, int id, String format, Object... args) throws IOException {
         writeByte(BEGIN_GRAPH);
         writeInt(id);
-        writeString(format);
-        writeInt(args.length);
+        writeString(format + " id: %d");
+        writeInt(args.length + 1);
         for (Object a : args) {
             writePropertyObject(a);
         }
+        writePropertyObject(++cnt);
         writeGraph(graph, properties);
         flush();
     }
         */
        File f = new File(bigv.toURI());
        FileChannel fch2 = FileChannel.open(f.toPath(), StandardOpenOption.READ);
        GraphDocument checkDocument = new GraphDocument();
        BinarySource scanSource = new BinarySource(null, fch2);
        AtomicInteger count = new AtomicInteger();
        List<String> titles = new ArrayList<>();
        ModelBuilder mb = new ModelBuilder(checkDocument, 
                                           (g) -> count.incrementAndGet(), null) {
            @Override
            public InputGraph startGraph(int dumpId, String format, Object[] args) {
                titles.add(InputGraph.makeGraphName(dumpId, format, args));
                return super.startGraph(dumpId, format, args);
            }
        };
        BinaryReader rdr = new BinaryReader(scanSource, mb);
        rdr.parse();
        assertEquals("Three graphs started", 3, titles.size());
        int prev = -1;
        for (String t : titles) {
            assertEquals("All % are gone", -1, t.indexOf("%"));
            int idColon = t.indexOf("id: ");
            assertNotEquals("id: found", -1, idColon);
            int cnt = Integer.parseInt(t.substring(idColon + 4));
            assertTrue("New counter (" + cnt + ") is bigger than " + prev, prev < cnt);
            prev = cnt;
        }
        if (prev < 5) {
            fail("Expecting at least 5 counted titles: " + prev);
        }
    }

    /**
     * Checks that the data can be fully read correctly by the new implementation
     * of full scanner.
     */
    public void testReadData20() throws Exception {
        URL bigv = ScanningBinaryParserTest.class.getResource("bigv-2.0.bgv");
        File f = new File(bigv.toURI());
        FileChannel fch2 = FileChannel.open(f.toPath(), StandardOpenOption.READ);
        GraphDocument checkDocument = new GraphDocument();
        BinarySource scanSource = new BinarySource(null, fch2);
        AtomicInteger count = new AtomicInteger();
        ModelBuilder mb = new ModelBuilder(checkDocument, 
                                           (g) -> count.incrementAndGet(), null);
        BinaryReader rdr = new BinaryReader(scanSource, mb);
        rdr.parse();
        System.err.println("Read " + count.get());
    }

    /**
     * Checks that the data can be fully read correctly by the new implementation
     * of full scanner.
     */
    public void testReadData10() throws Exception {
        URL bigv = ScanningBinaryParserTest.class.getResource("bigv-1.0.bgv");
        File f = new File(bigv.toURI());
        FileChannel fch2 = FileChannel.open(f.toPath(), StandardOpenOption.READ);
        GraphDocument checkDocument = new GraphDocument();
        BinarySource scanSource = new BinarySource(null, fch2);
        AtomicInteger count = new AtomicInteger();
        ModelBuilder mb = new ModelBuilder(checkDocument, 
                                           (g) -> count.incrementAndGet(), null);
        BinaryReader rdr = new BinaryReader(scanSource, mb);
        rdr.parse();
        System.err.println("Read " + count.get());
    }

    /**
     * Checks that the data can be fully read correctly by the new implementation
     * of full scanner.
     */
    public void testReadDataOldImpl() throws Exception {
        URL bigv = ScanningBinaryParserTest.class.getResource("bigv-1.0.bgv");
        File f = new File(bigv.toURI());
        FileChannel fch2 = FileChannel.open(f.toPath(), StandardOpenOption.READ);
        GraphDocument checkDocument = new GraphDocument();

        AtomicInteger count = new AtomicInteger();
        BinaryParser parser = new BinaryParser(fch2, null, checkDocument, (g) -> count.incrementAndGet());
        parser.parse();
        System.err.println("Read " + count.get());
    }

    BinaryReader reader;

    ConstantPool getCP() {
        return reader.getConstantPool();
    }

    /**
     * Checks that groups whose contents are skipped during initial scan
     * are read when expanded.
     * @throws IOException
     */
    public void testReadLazy() throws Exception {
        URL bigv = ScanningBinaryParserTest.class.getResource("bigv-2.0.bgv");
        File f = new File(bigv.toURI());
        FileChannel fch = FileChannel.open(f.toPath(), StandardOpenOption.READ);
        FileChannel fch2 = FileChannel.open(f.toPath(), StandardOpenOption.READ);
        GraphDocument checkDocument = new GraphDocument();

        FileContent fc = new FileContent(f.toPath(), null);
        BinarySource scanSource = new BinarySource(null, fc);

        ScanningModelBuilder mb = new ScanningModelBuilder(scanSource, 
                                                           fc, checkDocument, 
                                                           (GroupCallback)((g) -> checkDocument.addElement(g)), null,
                                                           RP, new StreamPool());
        reader = new BinaryReader(scanSource, mb);
        reader.parse();

        GraphDocument doc = mb.rootDocument();
        int count = 0;
        for (FolderElement tl : doc.getElements()) {
            if (tl instanceof Group) {
                Group g = (Group) tl;
                System.err.print(".");
                if ((++count) % 50 == 0) {
                    System.err.println("");
                }
                Assert.assertFalse(g.getElements().isEmpty());
            }
        }
    }
}
