/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.tools.insight.test.heap;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.function.Consumer;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Instrument;
import org.graalvm.polyglot.Value;
import org.graalvm.tools.insight.test.InsightObjectFactory;
import org.graalvm.tools.insight.test.heap.HeapApi.At;
import org.graalvm.tools.insight.test.heap.HeapApi.Event;
import org.graalvm.tools.insight.test.heap.HeapApi.Source;
import org.graalvm.tools.insight.test.heap.HeapApi.StackElement;
import org.graalvm.tools.insight.test.heap.HeapApi.StackEvent;
import static org.graalvm.tools.insight.test.heap.HeapApi.invokeDump;
import static org.graalvm.tools.insight.test.heap.HeapApi.invokeFlush;
import static org.graalvm.tools.insight.test.heap.HeapObjectTest.createDumpObject;
import org.graalvm.tools.insight.test.heap.HeapResourceRule.HeapParams;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import org.junit.Rule;
import org.junit.Test;

public class HeapObjectStreamTest {

    private Value heap;
    private ByteArrayOutputStream heapOutput;

    @Rule public HeapResourceRule heapResource = new HeapResourceRule() {
        @Override
        protected void before(int cacheSize, String cacheReplacement) throws Throwable {
            Context.Builder b = Context.newBuilder();
            b.option("heap.cacheSize", Integer.toString(cacheSize));
            if (cacheReplacement != null) {
                b.option("heap.cacheReplacement", cacheReplacement);
            }
            b.allowHostAccess(HostAccess.EXPLICIT);
            Context ctx = InsightObjectFactory.newContext(b);

            // BEGIN: org.graalvm.tools.insight.test.heap.HeapObjectStreamTest
            Map<String, Instrument> instruments = ctx.getEngine().getInstruments();
            Instrument heapInstrument = instruments.get("heap");

            @SuppressWarnings("unchecked")
            Consumer<OutputStream> consumeOutput = heapInstrument.lookup(Consumer.class);

            heapOutput = new ByteArrayOutputStream();
            consumeOutput.accept(heapOutput);
            // END: org.graalvm.tools.insight.test.heap.HeapObjectStreamTest

            heap = InsightObjectFactory.readObject(ctx, "heap");
            assertFalse("Heap object is defined", heap.isNull());
        }
    };

    @Test
    public void cannotSetNullStream() throws Exception {
        @SuppressWarnings("unchecked")
        Consumer<OutputStream> osSetup = heap.getContext().getEngine().getInstruments().get("heap").lookup(Consumer.class);
        assertNotNull("Consumer for OutputStream", osSetup);
        assertTrue("Hidden behind proxy: " + osSetup, Proxy.isProxyClass(osSetup.getClass()));

        try {
            osSetup.accept(null);
            fail("Stream cannot be null!");
        } catch (NullPointerException ex) {
            // OK
        }
    }

    @Test
    public void dumpToStream() throws Exception {
        Source nullSource = new Source("no.src", null, null, null, null);
        invokeDump(heap, 1, new Event[]{
                        new StackEvent(new StackElement[]{new StackElement(new At(null, nullSource, 1, 0, 5), createDumpObject())})
        });

        Source source = new Source("a.text", "application/x-test", "test", "file://a.test", "aaaaa");
        invokeDump(heap, 1, new Event[]{
                        new StackEvent(new StackElement[]{new StackElement(new At("a", source, null, null, null), createDumpObject())})
        });

        String header = new String(heapOutput.toByteArray(), 0, 18);
        assertEquals("JAVA PROFILE 1.0.1", header);

        final int heapSize = heapOutput.size();
        if (heapSize != 3964) {
            fail("Heap dump should be generated to the stream: " + heapOutput.size());
        }

        @SuppressWarnings("unchecked")
        Consumer<OutputStream> osSetup = heap.getContext().getEngine().getInstruments().get("heap").lookup(Consumer.class);
        assertNotNull("Consumer for OutputStream", osSetup);
        final ByteArrayOutputStream anotherStream = new ByteArrayOutputStream();

        osSetup.accept(anotherStream);
        invokeDump(heap, 1, new Event[]{
                        new StackEvent(new StackElement[]{new StackElement(new At("a", source, null, null, null), createDumpObject())})
        });

        assertEquals("Size of first stream remains unchaged", heapSize, heapOutput.size());

        String anotherHeader = new String(anotherStream.toByteArray(), 0, 18);
        assertEquals("JAVA PROFILE 1.0.1", anotherHeader);
    }

    @Test(timeout = 10_000)
    @HeapParams(cacheSize = 10_000, cacheReplacement = "flush")
    public void dumpToStreamCached() throws Exception {
        Source nullSource = new Source("no.src", null, null, null, null);
        invokeDump(heap, 1, new Event[]{
                        new StackEvent(new StackElement[]{new StackElement(new At(null, nullSource, 1, 0, 5), createDumpObject())})
        });

        Source source = new Source("a.text", "application/x-test", "test", "file://a.test", "aaaaa");
        invokeDump(heap, 1, new Event[]{
                        new StackEvent(new StackElement[]{new StackElement(new At("a", source, null, null, null), createDumpObject())})
        });

        if (heapOutput.size() > 0) {
            fail("Heap dump should not be written to the stream yet: " + heapOutput.size());
        }

        invokeFlush(heap);

        String header = new String(heapOutput.toByteArray(), 0, 18);
        assertEquals("JAVA PROFILE 1.0.1", header);

        final int heapSize = heapOutput.size();
        if (heapSize != 2509) {
            fail("Heap dump should be generated to the stream: " + heapOutput.size());
        }
    }

    @Test
    @HeapParams(cacheSize = 2, cacheReplacement = "flush")
    public void dumpToStreamCachedDumps() throws Exception {
        Source source = new Source("a.text", "application/x-test", "test", "file://a.test", "aaaaa");
        invokeDump(heap, 1, new Event[]{
                        new StackEvent(new StackElement[]{new StackElement(new At("a", source, 1, 0, 2), createDumpObject())})
        });
        invokeDump(heap, 1, new Event[]{
                        new StackEvent(new StackElement[]{new StackElement(new At("a", source, 2, 2, 2), createDumpObject())})
        });

        if (heapOutput.size() > 0) {
            fail("Heap dump should not be written to the stream yet: " + heapOutput.size());
        }

        invokeDump(heap, 1, new Event[]{
                        new StackEvent(new StackElement[]{new StackElement(new At("a", source, 3, 4, 2), createDumpObject())})
        });

        final int heapSize = heapOutput.size();
        if (heapSize != 2500) {
            fail("Heap dump should be generated to the stream: " + heapOutput.size());
        }

        invokeFlush(heap);
        if (heapOutput.size() <= heapSize) {
            fail("Flush should write the remaining cached events.");
        }
    }

    @Test
    @HeapParams(cacheSize = 2, cacheReplacement = "lru")
    public void dumpToStreamCachedLRU() throws Exception {
        Source source = new Source("a.text", "application/x-test", "test", "file://a.test", "aaaaa");
        invokeDump(heap, 1, new Event[]{
                        new StackEvent(new StackElement[]{new StackElement(new At("a", source, 1, 0, 2), createDumpObject())})
        });
        invokeDump(heap, 1, new Event[]{
                        new StackEvent(new StackElement[]{new StackElement(new At("a", source, 2, 2, 2), createDumpObject())})
        });

        if (heapOutput.size() > 0) {
            fail("Heap dump should not be written to the stream yet: " + heapOutput.size());
        }

        invokeFlush(heap);

        final int heapSize = heapOutput.size();
        if (heapSize != 2286) {
            fail("Heap dump should be generated to the stream: " + heapOutput.size());
        }

        @SuppressWarnings("unchecked")
        Consumer<OutputStream> osSetup = heap.getContext().getEngine().getInstruments().get("heap").lookup(Consumer.class);
        assertNotNull("Consumer for OutputStream", osSetup);
        final ByteArrayOutputStream anotherStream = new ByteArrayOutputStream();

        osSetup.accept(anotherStream);
        for (int i = 0; i < 4; i++) {
            invokeDump(heap, 1, new Event[]{
                            new StackEvent(new StackElement[]{new StackElement(new At("a", source, i, 2 * i, 2), createDumpObject())})
            });
        }
        invokeFlush(heap);

        assertEquals("Size of first stream remains unchaged", heapSize, heapOutput.size());
        assertEquals("Size of the second stream is the same due to LRU caching", heapSize, anotherStream.size());
        assertNotEquals("Heap contents differ", true, equals(heapOutput.toByteArray(), anotherStream.toByteArray()));
    }

    private static boolean equals(byte[] arr1, byte[] arr2) {
        if (arr1.length != arr2.length) {
            return false;
        }
        for (int i = 0; i < arr1.length; i++) {
            if (arr1[i] != arr2[i]) {
                return false;
            }
        }
        return true;
    }

}
