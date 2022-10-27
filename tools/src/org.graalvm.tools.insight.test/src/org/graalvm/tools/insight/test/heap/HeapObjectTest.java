/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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
import java.io.File;
import java.io.OutputStream;
import java.util.function.Consumer;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Instrument;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.io.IOAccess;
import org.graalvm.tools.insight.test.InsightObjectFactory;
import org.graalvm.tools.insight.test.heap.HeapApi.At;
import org.graalvm.tools.insight.test.heap.HeapApi.Event;
import org.graalvm.tools.insight.test.heap.HeapApi.Source;
import org.graalvm.tools.insight.test.heap.HeapApi.StackElement;
import org.graalvm.tools.insight.test.heap.HeapApi.StackEvent;
import org.graalvm.tools.insight.test.heap.HeapApi.UnrelatedEvent;
import static org.graalvm.tools.insight.test.heap.HeapApi.invokeDump;
import static org.graalvm.tools.insight.test.heap.HeapApi.invokeFlush;
import org.graalvm.tools.insight.test.heap.HeapResourceRule.HeapParams;
import org.junit.After;
import org.junit.Assert;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import org.junit.Rule;
import org.junit.Test;

public class HeapObjectTest {
    private Context context;
    private Value heap;
    private File dumpFile;

    @Rule public HeapResourceRule heapResource = new HeapResourceRule() {
        @Override
        protected void before(int cacheSize, String cacheReplacement) throws Throwable {
            Context.Builder b = Context.newBuilder();
            b.option("heap.cacheSize", Integer.toString(cacheSize));
            if (cacheReplacement != null) {
                b.option("heap.cacheReplacement", cacheReplacement);
            }
            dumpFile = File.createTempFile("heap", ".hprof");
            dumpFile.deleteOnExit();
            b.option("heap.dump", dumpFile.getAbsolutePath());
            b.allowIO(IOAccess.ALL);
            b.allowHostAccess(HostAccess.EXPLICIT);
            Context ctx = InsightObjectFactory.newContext(b);
            context = ctx;
            heap = InsightObjectFactory.readObject(ctx, "heap");
            assertFalse("Heap object is defined", heap.isNull());
        }
    };

    @After
    public void after() {
        context.close();
    }

    @Test
    public void noEvents() throws Exception {
        invokeDump(heap, null, new Event[0]);
    }

    @Test
    public void noEventsAndDepth() throws Exception {
        invokeDump(heap, 10, new Event[0]);
    }

    @Test
    public void noArguments() throws Exception {
        try {
            heap.invokeMember("dump");
            fail("Exception shall be raised");
        } catch (IllegalArgumentException ex) {
            assertMessage(ex, "Instructive error message provided", " Use as dump({ format: '', events: []})");
        }
    }

    @Test
    public void stackMustBeThere() throws Exception {
        try {
            invokeDump(heap, Integer.MAX_VALUE, new Event[]{
                            new UnrelatedEvent()
            });
            fail("Should fail");
        } catch (PolyglotException ex) {
            assertMessage(ex, "Member stack must be present", "'stack' among [a, b, c]");
        }
    }

    @Test
    public void stackNeedsToBeAnArray() throws Exception {
        try {
            invokeDump(heap, 1, new Event[]{
                            new StackEvent("any")
            });
            fail("Should fail");
        } catch (PolyglotException ex) {
            assertMessage(ex, "Member stack must be an array", "'stack' shall be an array");
        }
    }

    @Test
    public void atMustBePresent() throws Exception {
        invokeDump(heap, 1, new Event[]{
                        new StackEvent(new Object[0])
        });
        try {
            invokeDump(heap, 1, new Event[]{
                            new StackEvent(new Object[]{new UnrelatedEvent()})
            });
            fail("Should fail");
        } catch (PolyglotException ex) {
            assertMessage(ex, "Expecting 'at' ", "'at' among [a, b, c]");
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    public void cannotAssignStreamWhenPathProvided() throws Exception {
        Instrument heapInstrument = heap.getContext().getEngine().getInstruments().get("heap");
        Consumer<OutputStream> consumer = heapInstrument.lookup(Consumer.class);
        assertNotNull("Consumer of OutputStream found", consumer);
        try {
            consumer.accept(new ByteArrayOutputStream());
            fail("There should be an exception when setting output stream");
        } catch (IllegalStateException ex) {
            assertNotEquals("Right message found", -1, ex.getMessage().indexOf("Cannot use path"));
            assertNotEquals("Right message found", -1, ex.getMessage().indexOf("and stream"));
            assertNotEquals("Right message found", -1, ex.getMessage().indexOf("at once"));
        }
    }

    @Test
    public void nonNullAt() throws Exception {
        try {
            invokeDump(heap, 1, new Event[]{
                            new StackEvent(new StackElement[]{new StackElement(null, null)})
            });
            fail("Expeting failure");
        } catch (PolyglotException ex) {
            assertMessage(ex, "Expecting non-null 'at' ", "'at' should be defined");
        }
    }

    @Test
    public void everythingIsOK() throws Exception {
        Source nullSource = new Source("no.source", null, null, null, null);
        invokeDump(heap, 1, new Event[]{
                        new StackEvent(new StackElement[]{new StackElement(new At(null, nullSource, 1, 0, 5), createDumpObject())})
        });
        long heapSize = dumpFile.length();
        if (heapSize != 2295) {
            fail("Heap dump should be generated. Size = " + heapSize);
        }

        Source source = new Source("a.text", "application/x-test", "test", "file://a.test", "aaaaa");
        invokeDump(heap, 1, new Event[]{
                        new StackEvent(new StackElement[]{new StackElement(new At("a", source, null, null, null), createDumpObject())})
        });
        heapSize = dumpFile.length() - heapSize;
        if (heapSize != 3973 - 2295) {
            fail("Heap dump should be generated. Size = " + dumpFile.length());
        }
    }

    private static void assertMessage(Throwable ex, String msg, String exp) {
        int found = ex.getMessage().indexOf(exp);
        if (found == -1) {
            fail(msg +
                            "\nexpecting:" + exp +
                            "\nwas      : " + ex.getMessage());
        }
    }

    @Test
    @HeapParams(cacheSize = 2, cacheReplacement = "flush")
    public void memoryDumpTest1() throws Exception {
        checkDumpAfter(2, 10);
    }

    @Test
    @HeapParams(cacheSize = 3, cacheReplacement = "flush")
    public void memoryDumpTest2() throws Exception {
        checkDumpAfter(3, 10);
    }

    @Test
    @HeapParams(cacheSize = 1, cacheReplacement = "flush")
    public void memoryDumpTest3() throws Exception {
        checkDumpAfter(1, 10);
    }

    @Test
    @HeapParams(cacheSize = -1, cacheReplacement = "flush")
    public void memoryDumpTest4() throws Exception {
        checkDumpAfter(-1, 100);
    }

    @Test
    @HeapParams(cacheSize = 0, cacheReplacement = "flush")
    public void memoryDumpTest5() throws Exception {
        checkDumpAfter(0, 10);
    }

    private void checkDumpAfter(int period, int count) {
        Source source = new Source("a.text", "application/x-test", "test", "file://a.test", "aaaaa");
        int p = 0;
        long oldSize = dumpFile.length();
        for (int i = 0; i < count; i++) {
            invokeDump(heap, 1, new Event[]{
                            new StackEvent(new StackElement[]{new StackElement(new At("a" + p, source, i, i * 10, 10), createDumpObject())})
            });
            long heapSize = dumpFile.length() - oldSize;
            if (p++ == period) {
                if (heapSize < 1000) {
                    fail("Heap dump should be generated. p = " + p + ", i = " + i + ", size = " + heapSize);
                }
                p = 0;
            } else {
                if (heapSize > 0) {
                    fail("No heap dump should be generated yet. p = " + p + ", i = " + i + ", size = " + heapSize);
                }
            }
            oldSize += heapSize;
        }
    }

    @Test
    @HeapParams(cacheSize = 5, cacheReplacement = "lru")
    public void memoryLRUTest() throws Exception {
        Source source = new Source("a.text", "application/x-test", "test", "file://a.test", "aaaaa");
        long oldSize = dumpFile.length();
        for (int i = 0; i < 100; i++) {
            invokeDump(heap, 1, new Event[]{
                            new StackEvent(new StackElement[]{new StackElement(new At("a" + i, source, i, i * 10, 10), createDumpObject())})
            });
        }
        long heapSize = dumpFile.length() - oldSize;
        Assert.assertEquals(0L, heapSize);
        invokeFlush(heap);
        heapSize = dumpFile.length() - oldSize;
        if (heapSize != 3016) {
            fail("Heap dump should be generated. Size = " + dumpFile.length());
        }
    }

    static Object createDumpObject() {
        class FrameData {
            @HostAccess.Export public final String var = "variable";
        }
        return new FrameData();
    }

}
