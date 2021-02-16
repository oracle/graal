/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.api.instrumentation.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Instrument;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.AllocationEvent;
import com.oracle.truffle.api.instrumentation.AllocationEventFilter;
import com.oracle.truffle.api.instrumentation.AllocationListener;
import com.oracle.truffle.api.instrumentation.AllocationReporter;
import com.oracle.truffle.api.instrumentation.EventBinding;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.LanguageInfo;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.test.polyglot.ProxyLanguage;

/**
 * A test of {@link AllocationReporter}.
 */
public class AllocationReporterTest {

    private TestAllocationReporter allocation;
    private Engine engine;
    private org.graalvm.polyglot.Context context;
    private Instrument instrument;

    @Before
    public void setUp() {
        engine = Engine.create();
        instrument = engine.getInstruments().get("testAllocationReporter");
        allocation = instrument.lookup(TestAllocationReporter.class);
        context = org.graalvm.polyglot.Context.newBuilder().engine(engine).build();
    }

    @After
    public void tearDown() {
        engine.close();
    }

    /**
     * Test that {@link AllocationReporter} receives events with complete information about value
     * allocations. We test events passed to both
     * {@link AllocationListener#onEnter(com.oracle.truffle.api.nodes.LanguageInfo, java.lang.Object, long)}
     * and
     * {@link AllocationListener#onReturnValue(com.oracle.truffle.api.nodes.LanguageInfo, java.lang.Object, long)}
     * .
     */
    @Test
    public void testAllocationReport() {
        long u = AllocationReporter.SIZE_UNKNOWN;
        doTestAllocationReport(new long[]{u, 4, 8, 4, u}, new long[]{u, 4, 8, 4, 13});
    }

    private void doTestAllocationReport(long[] estimatedSizes, long[] computedSizes) {
        // Items to allocate:
        Source source = Source.create(AllocationReporterLanguage.ID,
                        "NEW\n" +
                                        "10\n" +
                                        "12345678901234\n" +
                                        "-1000\n" +
                                        "8767584273645748301282734657402983457843901293874657867582034875\n");
        AtomicInteger consumerCalls = new AtomicInteger(0);
        allocation.setAllocationConsumers(
                        // NEW
                        (info) -> {
                            assertTrue(info.will);
                            assertNull(info.value);
                            assertEquals(0, info.oldSize);
                            assertEquals(estimatedSizes[0], info.newSize);
                            consumerCalls.incrementAndGet();
                        },
                        (info) -> {
                            assertFalse(info.will);
                            assertEquals("NewObject", info.value.toString());
                            assertEquals(0, info.oldSize);
                            assertEquals(computedSizes[0], info.newSize);
                            consumerCalls.incrementAndGet();
                        },
                        // 10
                        (info) -> {
                            assertTrue(info.will);
                            assertNull(info.value);
                            assertEquals(0, info.oldSize);
                            assertEquals(estimatedSizes[1], info.newSize);
                            consumerCalls.incrementAndGet();
                        },
                        (info) -> {
                            assertFalse(info.will);
                            assertEquals("java.lang.Integer", info.value.getClass().getName());
                            assertEquals(10, info.value);
                            assertEquals(0, info.oldSize);
                            assertEquals(computedSizes[1], info.newSize);
                            consumerCalls.incrementAndGet();
                        },
                        // 12345678901234
                        (info) -> {
                            assertTrue(info.will);
                            assertNull(info.value);
                            assertEquals(0, info.oldSize);
                            assertEquals(estimatedSizes[2], info.newSize);
                            consumerCalls.incrementAndGet();
                        },
                        (info) -> {
                            assertFalse(info.will);
                            assertEquals("java.lang.Long", info.value.getClass().getName());
                            assertEquals(12345678901234L, info.value);
                            assertEquals(0, info.oldSize);
                            assertEquals(computedSizes[2], info.newSize);
                            consumerCalls.incrementAndGet();
                        },
                        // -1000
                        (info) -> {
                            assertTrue(info.will);
                            assertNull(info.value);
                            assertEquals(0, info.oldSize);
                            assertEquals(estimatedSizes[3], info.newSize);
                            consumerCalls.incrementAndGet();
                        },
                        (info) -> {
                            assertFalse(info.will);
                            assertEquals("java.lang.Integer", info.value.getClass().getName());
                            assertEquals(-1000, info.value);
                            assertEquals(0, info.oldSize);
                            assertEquals(computedSizes[3], info.newSize);
                            consumerCalls.incrementAndGet();
                        },
                        // 8767584273645748301282734657402983457843901293874657867582034875
                        (info) -> {
                            assertTrue(info.will);
                            assertNull(info.value);
                            assertEquals(0, info.oldSize);
                            assertEquals(estimatedSizes[4], info.newSize);
                            consumerCalls.incrementAndGet();
                        },
                        (info) -> {
                            assertFalse(info.will);
                            assertEquals(BigNumber.class, info.value.getClass());
                            assertEquals(0, info.oldSize);
                            assertEquals(computedSizes[4], info.newSize);
                            consumerCalls.incrementAndGet();
                        });
        context.eval(source);
        assertEquals(10, consumerCalls.get());
    }

    @Test
    public void testFailedAllocations() {
        // failed allocation
        Source source = Source.create(AllocationReporterLanguage.ID, "CanNotAllocateThisValue");
        AtomicInteger consumerCalls = new AtomicInteger(0);
        allocation.setAllocationConsumers(
                        (info) -> {
                            assertTrue(info.will);
                            assertNull(info.value);
                            assertEquals(0, info.oldSize);
                            assertEquals(AllocationReporter.SIZE_UNKNOWN, info.newSize);
                            consumerCalls.incrementAndGet();
                        });
        try {
            context.eval(source);
            fail();
        } catch (PolyglotException ex) {
            // O.K.
            assertTrue(ex.getMessage(), ex.getMessage().contains("NumberFormatException"));
        }
        assertEquals(1, consumerCalls.get());
        consumerCalls.set(0);

        // too big allocation
        source = Source.create(AllocationReporterLanguage.ID, "12345678901234");
        allocation.setAllocationConsumers(
                        (info) -> {
                            assertTrue(info.will);
                            assertNull(info.value);
                            assertEquals(0, info.oldSize);
                            assertEquals(8, info.newSize);
                            consumerCalls.incrementAndGet();
                            throw new OutOfMemoryError("Denied allocation of 8 bytes.");
                        },
                        (info) -> {
                            assertFalse(info.will);
                            assertNull(info.value);
                            consumerCalls.incrementAndGet();
                        });
        try {
            context.eval(source);
            fail();
        } catch (PolyglotException ex) {
            // O.K.
            assertTrue(ex.isResourceExhausted());
            assertTrue(ex.getMessage(), ex.getMessage().contains("Denied allocation of 8 bytes."));
        }
        assertEquals(1, consumerCalls.get());
        consumerCalls.set(0);

        // Too big reallocation
        source = Source.create(AllocationReporterLanguage.ID, "12345678901234->9876758023873465783492873465784938746502897345634897856");
        allocation.setAllocationConsumers(
                        (info) -> {
                            assertTrue(info.will);
                            assertEquals(12345678901234L, info.value);
                            assertEquals(8, info.oldSize);
                            assertEquals(AllocationReporter.SIZE_UNKNOWN, info.newSize);
                            consumerCalls.incrementAndGet();
                            throw new OutOfMemoryError("Denied an unknown reallocation.");
                        },
                        (info) -> {
                            consumerCalls.incrementAndGet();
                        });
        try {
            context.eval(source);
            fail();
        } catch (PolyglotException ex) {
            // O.K.
            assertTrue(ex.isResourceExhausted());
            assertTrue(ex.getMessage(), ex.getMessage().contains("Denied an unknown reallocation."));
        }
        assertEquals(1, consumerCalls.get());
    }

    @Test
    @Ignore
    public void testWrongAllocations() {
        // Test of wrong allocation reports
        // A call to allocated() without a prior notifyWill... fails:
        Source source = Source.create(AllocationReporterLanguage.ID, "WRONG");
        AtomicInteger consumerCalls = new AtomicInteger(0);
        allocation.setAllocationConsumers((info) -> consumerCalls.incrementAndGet());
        try {
            context.eval(source);
            fail();
        } catch (AssertionError err) {
            assertEquals("onEnter() was not called", err.getMessage());
        }
        assertEquals(0, consumerCalls.get());

        // Have one notifyWillReallocate() call caused by NEW,
        // but denied to suppress the notifyAllocated().
        // Then call notifyAllocated() alone:
        source = Source.create(AllocationReporterLanguage.ID, "10->10");
        allocation.setAllocationConsumers(
                        (info) -> {
                            consumerCalls.incrementAndGet();
                            throw new OutOfMemoryError("Denied one allocation.");
                        });
        try {
            context.eval(source);
            fail();
        } catch (OutOfMemoryError err) {
            // O.K.
        }
        assertEquals(1, consumerCalls.get());
        source = Source.create(AllocationReporterLanguage.ID, "WRONG");
        allocation.setAllocationConsumers(
                        (info) -> consumerCalls.incrementAndGet(),
                        (info) -> consumerCalls.incrementAndGet());
        try {
            context.eval(source);
            fail();
        } catch (AssertionError err) {
            assertEquals("A different reallocated value. Was: 10 now is: NewObject", err.getMessage());
        }
        assertEquals(1, consumerCalls.get());
        consumerCalls.set(0);

        // Exposal of internal values is not allowed
        source = Source.create(AllocationReporterLanguage.ID, "INTERNAL");
        allocation.setAllocationConsumers(
                        (info) -> consumerCalls.incrementAndGet(),
                        (info) -> consumerCalls.incrementAndGet());
        try {
            context.eval(source);
            fail();
        } catch (AssertionError err) {
            assertEquals("Wrong value class, TruffleObject is required. Was: " + AllocationReporterLanguage.AllocValue.class.getName(), err.getMessage());
        }
        assertEquals(1, consumerCalls.get());
    }

    @Test
    public void testReallocationReport() {
        long u = AllocationReporter.SIZE_UNKNOWN;
        doTestReallocationReport(new long[]{u, 4, 13, u}, new long[]{u, 4, 13, 6});
    }

    private void doTestReallocationReport(long[] estimatedSizes, long[] computedSizes) {
        Source source = Source.create(AllocationReporterLanguage.ID,
                        "NEW->10\n" + "8767584273645748301282734657402983457843901293874657867582034875->987364758928736457840187265789\n");
        AtomicInteger consumerCalls = new AtomicInteger(0);
        allocation.setAllocationConsumers(
                        // NEW -> 10
                        (info) -> {
                            assertTrue(info.will);
                            assertEquals("NewObject", info.value.toString());
                            assertEquals(estimatedSizes[0], info.oldSize);
                            assertEquals(estimatedSizes[1], info.newSize);
                            consumerCalls.incrementAndGet();
                        },
                        (info) -> {
                            assertFalse(info.will);
                            assertEquals("NewObject", info.value.toString());
                            assertEquals(computedSizes[0], info.oldSize);
                            assertEquals(computedSizes[1], info.newSize);
                            consumerCalls.incrementAndGet();
                        },
                        // BigNumber -> BigNumber
                        (info) -> {
                            assertTrue(info.will);
                            assertEquals(BigNumber.class, info.value.getClass());
                            assertEquals(estimatedSizes[2], info.oldSize);
                            assertEquals(estimatedSizes[3], info.newSize);
                            consumerCalls.incrementAndGet();
                        },
                        (info) -> {
                            assertFalse(info.will);
                            assertEquals(BigNumber.class, info.value.getClass());
                            assertEquals(computedSizes[2], info.oldSize);
                            assertEquals(computedSizes[3], info.newSize);
                            consumerCalls.incrementAndGet();
                        });
        context.eval(source);
        assertEquals(4, consumerCalls.get());
    }

    @Test
    public void testNestedAllocations() {
        Source source = Source.create(AllocationReporterLanguage.ID,
                        "NEW { NEW }\n" +
                                        "10 { 20 30 { 1234567890123456789 } }\n" +
                                        "12345678901234->897654123210445621235489 { 10->NEW { 20->NEW } 30->NEW }\n");
        AtomicInteger consumerCalls = new AtomicInteger(0);
        allocation.setAllocationConsumers(
                        // NEW { NEW }
                        (info) -> {
                            assertTrue(info.will);
                            assertNull(info.value);
                            consumerCalls.incrementAndGet();
                        },
                        (info) -> {
                            assertTrue(info.will);
                            assertNull(info.value);
                            consumerCalls.incrementAndGet();
                        },
                        (info) -> {
                            assertFalse(info.will);
                            assertEquals("NewObject", info.value.toString());
                            consumerCalls.incrementAndGet();
                        },
                        (info) -> {
                            assertFalse(info.will);
                            assertEquals("NewObject", info.value.toString());
                            consumerCalls.incrementAndGet();
                        },
                        // | 10 { 20 ...
                        (info) -> {
                            assertTrue(info.will);
                            assertNull(info.value);
                            assertEquals(0, info.oldSize);
                            assertEquals(4, info.newSize);
                            consumerCalls.incrementAndGet();
                        },
                        // 10 { | 20 ...
                        (info) -> {
                            assertTrue(info.will);
                            assertNull(info.value);
                            assertEquals(0, info.oldSize);
                            assertEquals(4, info.newSize);
                            consumerCalls.incrementAndGet();
                        },
                        // 10 { 20 | 30 ...
                        (info) -> {
                            assertFalse(info.will);
                            assertEquals(20, info.value);
                            assertEquals(0, info.oldSize);
                            assertEquals(4, info.newSize);
                            consumerCalls.incrementAndGet();
                        },
                        (info) -> {
                            assertTrue(info.will);
                            assertNull(info.value);
                            assertEquals(0, info.oldSize);
                            assertEquals(4, info.newSize);
                            consumerCalls.incrementAndGet();
                        },
                        // 10 { 20 30 { | 1234567890123456789 } }
                        (info) -> {
                            assertTrue(info.will);
                            assertNull(info.value);
                            assertEquals(0, info.oldSize);
                            assertEquals(8, info.newSize);
                            consumerCalls.incrementAndGet();
                        },
                        // 10 { 20 30 { 1234567890123456789 | } }
                        (info) -> {
                            assertFalse(info.will);
                            assertEquals(1234567890123456789L, info.value);
                            assertEquals(0, info.oldSize);
                            assertEquals(8, info.newSize);
                            consumerCalls.incrementAndGet();
                        },
                        // 10 { 20 30 { 1234567890123456789 } | }
                        (info) -> {
                            assertFalse(info.will);
                            assertEquals(30, info.value);
                            assertEquals(0, info.oldSize);
                            assertEquals(4, info.newSize);
                            consumerCalls.incrementAndGet();
                        },
                        // 10 { 20 30 { 1234567890123456789 } } |
                        (info) -> {
                            assertFalse(info.will);
                            assertEquals(10, info.value);
                            assertEquals(0, info.oldSize);
                            assertEquals(4, info.newSize);
                            consumerCalls.incrementAndGet();
                        },
                        // | 12345678901234->897654123210445621235489 { 10->NEW { 20->NEW } 30->NEW
                        // }
                        (info) -> {
                            assertTrue(info.will);
                            assertEquals(12345678901234L, info.value);
                            assertEquals(8, info.oldSize);
                            assertEquals(AllocationReporter.SIZE_UNKNOWN, info.newSize);
                            consumerCalls.incrementAndGet();
                        },
                        (info) -> {
                            assertTrue(info.will);
                            assertEquals(10, info.value);
                            assertEquals(4, info.oldSize);
                            assertEquals(AllocationReporter.SIZE_UNKNOWN, info.newSize);
                            consumerCalls.incrementAndGet();
                        },
                        (info) -> {
                            assertTrue(info.will);
                            assertEquals(20, info.value);
                            assertEquals(4, info.oldSize);
                            assertEquals(AllocationReporter.SIZE_UNKNOWN, info.newSize);
                            consumerCalls.incrementAndGet();
                        },
                        (info) -> {
                            assertFalse(info.will);
                            assertEquals(20, info.value);
                            assertEquals(4, info.oldSize);
                            assertEquals(AllocationReporter.SIZE_UNKNOWN, info.newSize);
                            consumerCalls.incrementAndGet();
                        },
                        (info) -> {
                            assertFalse(info.will);
                            assertEquals(10, info.value);
                            assertEquals(4, info.oldSize);
                            assertEquals(AllocationReporter.SIZE_UNKNOWN, info.newSize);
                            consumerCalls.incrementAndGet();
                        },
                        (info) -> {
                            assertTrue(info.will);
                            assertEquals(30, info.value);
                            assertEquals(4, info.oldSize);
                            assertEquals(AllocationReporter.SIZE_UNKNOWN, info.newSize);
                            consumerCalls.incrementAndGet();
                        },
                        (info) -> {
                            assertFalse(info.will);
                            assertEquals(30, info.value);
                            assertEquals(4, info.oldSize);
                            assertEquals(AllocationReporter.SIZE_UNKNOWN, info.newSize);
                            consumerCalls.incrementAndGet();
                        },
                        (info) -> {
                            assertFalse(info.will);
                            assertEquals(12345678901234L, info.value);
                            assertEquals(8, info.oldSize);
                            assertEquals(5, info.newSize);
                            consumerCalls.incrementAndGet();
                        });
        context.eval(source);
        assertEquals(20, consumerCalls.get());
    }

    @Test
    public void testUnregister() {
        Source source = Source.create(AllocationReporterLanguage.ID,
                        "NEW\n" +
                                        "10\n" +
                                        "12345678901234\n" +
                                        "-1000\n" +
                                        "8767584273645748301282734657402983457843901293874657867582034875\n");
        AtomicInteger consumerCalls = new AtomicInteger(0);
        allocation.setAllocationConsumers(
                        (info) -> consumerCalls.incrementAndGet(),
                        (info) -> consumerCalls.incrementAndGet(),

                        (info) -> {
                            allocation.getAllocationEventBinding().dispose();
                            consumerCalls.incrementAndGet();
                        },
                        (info) -> consumerCalls.incrementAndGet(),

                        (info) -> consumerCalls.incrementAndGet(),
                        (info) -> consumerCalls.incrementAndGet(),

                        (info) -> consumerCalls.incrementAndGet(),
                        (info) -> consumerCalls.incrementAndGet(),

                        (info) -> consumerCalls.incrementAndGet(),
                        (info) -> consumerCalls.incrementAndGet());
        context.eval(source);
        assertEquals(3, consumerCalls.get());
    }

    @Test
    public void testReporterChangeListener() {
        // Test of AllocationReporter property change listener notifications
        allocation.setEnabled(false);
        Source source = Source.create(AllocationReporterLanguage.ID, "NEW");
        allocation.setAllocationConsumers((info) -> {
        }, (info) -> {
        });
        context.eval(source);
        context.enter();
        try {
            AllocationReporter reporter = AllocationReporterLanguage.getCurrentContext().getEnv().lookup(AllocationReporter.class);
            AtomicInteger listenerCalls = new AtomicInteger(0);
            AllocationReporterListener activatedListener = AllocationReporterListener.register(listenerCalls, reporter);
            assertEquals(0, listenerCalls.get());
            assertFalse(reporter.isActive());
            allocation.setEnabled(true);
            assertEquals(1, listenerCalls.get());
            activatedListener.unregister();
            listenerCalls.set(0);

            AllocationDeactivatedListener deactivatedListener = AllocationDeactivatedListener.register(listenerCalls, reporter);
            assertEquals(0, listenerCalls.get());
            assertTrue(reporter.isActive());
            allocation.setEnabled(false);
            assertEquals(1, listenerCalls.get());
            deactivatedListener.unregister();
        } finally {
            context.leave();
        }
    }

    /**
     * A test allocation language. Parses allocation commands separated by white spaces.
     * <ul>
     * <li>new - allocation of an unknown size</li>
     * <li>&lt;int number&gt; - allocation of an integer (4 bytes)</li>
     * <li>&lt;long number&gt; - allocation of a long (8 bytes)</li>
     * <li>&lt;big number&gt; - allocation of a big number (unknown size in advance, computed from
     * BigInteger bit length afterwards)</li>
     * <li>&lt;command&gt;-&gt;&lt;command&gt; - re-allocation</li>
     * <li>{ &lt;command&gt; ... } allocations nested under the previous command</li>
     * </ul>
     */
    @TruffleLanguage.Registration(id = AllocationReporterLanguage.ID, name = "Allocation Reporter Language", version = "1.0")
    public static class AllocationReporterLanguage extends ProxyLanguage {

        public static final String ID = "truffle-allocation-reporter-language";
        public static final String PROP_SIZE_CALLS = "sizeCalls";

        @Override
        protected CallTarget parse(ParsingRequest request) throws Exception {
            final com.oracle.truffle.api.source.Source code = request.getSource();
            return Truffle.getRuntime().createCallTarget(new RootNode(this) {

                @Node.Child private AllocNode alloc = parse(code.getCharacters().toString());

                @Override
                public Object execute(VirtualFrame frame) {
                    return alloc.execute(frame);
                }

            });
        }

        private static AllocNode parse(String code) {
            String[] allocations = code.split("\\s");
            LinkedList<FutureNode> futures = new LinkedList<>();
            FutureNode parent = new FutureNode(null, null);
            FutureNode last = null;
            futures.add(parent);
            for (String allocCommand : allocations) {
                if ("{".equals(allocCommand)) {
                    futures.add(last);
                    parent = last;
                    last = null;
                    continue;
                }
                if (last != null) {
                    parent.addChild(last.toNode());
                    last = null;
                }
                if ("}".equals(allocCommand)) {
                    AllocNode node = parent.toNode();
                    futures.removeLast(); // the "parent" removed
                    parent = futures.getLast();
                    parent.addChild(node);
                    continue;
                }
                int reallocIndex = allocCommand.indexOf("->");
                if (reallocIndex < 0) { // pure allocation
                    AllocValue newValue = parseValue(allocCommand);
                    last = new FutureNode(null, newValue);
                } else {
                    AllocValue oldValue = parseValue(allocCommand.substring(0, reallocIndex));
                    AllocValue newValue = parseValue(allocCommand.substring(reallocIndex + 2));
                    last = new FutureNode(oldValue, newValue);
                }
            }
            if (last != null) {
                parent.addChild(last.toNode());
            }
            return futures.removeLast().toNode();
        }

        private static AllocValue parseValue(String allocCommand) {
            AllocValue newValue;
            if ("new".equalsIgnoreCase(allocCommand)) {
                newValue = new AllocValue(AllocValue.Kind.UNKNOWN, null);
            } else if ("internal".equalsIgnoreCase(allocCommand)) {
                newValue = new AllocValue(AllocValue.Kind.INTERNAL, null);
            } else if ("wrong".equalsIgnoreCase(allocCommand)) {
                newValue = new AllocValue(AllocValue.Kind.WRONG, null);
            } else {
                try {
                    Integer.parseInt(allocCommand);
                    newValue = new AllocValue(AllocValue.Kind.INT, allocCommand);
                } catch (NumberFormatException exi) {
                    try {
                        Long.parseLong(allocCommand);
                        newValue = new AllocValue(AllocValue.Kind.LONG, allocCommand);
                    } catch (NumberFormatException exl) {
                        newValue = new AllocValue(AllocValue.Kind.BIG, allocCommand);
                    }
                }
            }
            return newValue;
        }

        private static class AllocValue {

            enum Kind {
                UNKNOWN,
                INT,
                LONG,
                BIG,
                INTERNAL,   // Exposes an internal object impl (for error handling test)
                WRONG,      // Test of a wrong allocation report
            }

            final Kind kind;
            final String text;

            AllocValue(Kind kind, String text) {
                this.kind = kind;
                this.text = text;
            }
        }

        private static class FutureNode {

            private final AllocValue oldValue;
            private final AllocValue newValue;
            private List<AllocNode> children;

            FutureNode(AllocValue oldValue, AllocValue newValue) {
                this.oldValue = oldValue;
                this.newValue = newValue;
            }

            void addChild(AllocNode node) {
                if (children == null) {
                    children = new ArrayList<>();
                }
                children.add(node);
            }

            AllocNode toNode() {
                AllocationReporter reporter = getCurrentContext().getEnv().lookup(AllocationReporter.class);
                if (children == null) {
                    return new AllocNode(oldValue, newValue, reporter);
                } else {
                    return new AllocNode(oldValue, newValue, reporter, children.toArray(new AllocNode[children.size()]));
                }
            }
        }

        private static class AllocNode extends Node {

            private final AllocValue oldValue;
            private final AllocValue newValue;
            @Children private final AllocNode[] children;
            private final AllocationReporter reporter;

            AllocNode(AllocValue oldValue, AllocValue newValue, AllocationReporter reporter) {
                this(oldValue, newValue, reporter, null);
            }

            AllocNode(AllocValue oldValue, AllocValue newValue, AllocationReporter reporter, AllocNode[] children) {
                this.oldValue = oldValue;
                this.newValue = newValue;
                this.children = children;
                this.reporter = reporter;
            }

            public Object execute(VirtualFrame frame) {
                Object value;
                if (newValue == null) { // No allocation
                    value = InstrumentationTestLanguage.Null.INSTANCE;
                    execChildren(frame);
                } else if (oldValue == null) {
                    // new allocation
                    if (reporter.isActive()) {
                        if (newValue.kind != AllocValue.Kind.WRONG) {
                            // Test that it's wrong not to report will allocate
                            reporter.onEnter(null, 0, getAllocationSizeEstimate(newValue));
                        }
                    }
                    execChildren(frame);
                    value = allocateValue(newValue);
                    if (reporter.isActive()) {
                        reporter.onReturnValue(value, 0, computeValueSize(newValue, value));
                    }
                } else {
                    // re-allocation
                    value = allocateValue(oldValue);    // pretend that it was allocated already
                    long oldSize = AllocationReporter.SIZE_UNKNOWN;
                    long newSize = AllocationReporter.SIZE_UNKNOWN;
                    if (reporter.isActive()) {
                        oldSize = computeValueSize(oldValue, value);
                        newSize = getAllocationSizeEstimate(newValue);
                        reporter.onEnter(value, oldSize, newSize);
                    }
                    execChildren(frame);
                    // Re-allocate, oldValue -> newValue
                    if (reporter.isActive()) {
                        if (newSize == AllocationReporter.SIZE_UNKNOWN) {
                            if (AllocValue.Kind.BIG == newValue.kind) {
                                newSize = ((BigNumber) allocateValue(newValue)).getSize();
                            } else {
                                newSize = getAllocationSizeEstimate(newValue);
                            }
                        }
                        reporter.onReturnValue(value, oldSize, newSize);
                    }
                }
                return value;
            }

            @ExplodeLoop
            private void execChildren(VirtualFrame frame) {
                if (children != null) {
                    for (AllocNode ch : children) {
                        ch.execute(frame);
                    }
                }
            }

            private static long getAllocationSizeEstimate(AllocValue value) {
                switch (value.kind) {
                    case INT:
                        return 4;
                    case LONG:
                        return 8;
                    case BIG:
                    case UNKNOWN:
                    default:
                        return AllocationReporter.SIZE_UNKNOWN;
                }
            }

            private static long computeValueSize(AllocValue aValue, Object value) {
                switch (aValue.kind) {
                    case INT:
                        return 4;
                    case LONG:
                        return 8;
                    case BIG:
                        return ((BigNumber) value).getSize();
                    case UNKNOWN:
                    default:
                        return AllocationReporter.SIZE_UNKNOWN;
                }
            }

            private static Object allocateValue(AllocValue value) {
                switch (value.kind) {
                    case INT:
                        return Integer.parseInt(value.text);
                    case LONG:
                        return Long.parseLong(value.text);
                    case BIG:
                        return new BigNumber(value.text);
                    case INTERNAL:
                        return value;   // to test that it's wrong to expose an internal object
                    case UNKNOWN:
                    default:
                        return new TruffleObject() {
                            @Override
                            public String toString() {
                                return "NewObject";
                            }
                        };
                }
            }

        }

        public static LanguageContext getCurrentContext() {
            return getCurrentContext(AllocationReporterLanguage.class);
        }
    }

    private static class BigNumber implements TruffleObject {

        private BigInteger integer;

        @TruffleBoundary
        BigNumber(String value) {
            this.integer = new BigInteger(value);
        }

        @TruffleBoundary
        long getSize() {
            return integer.bitCount() / 8;
        }

    }

    private static final class AllocationInfo {

        private final Object value;
        private final long oldSize;
        private final long newSize;
        private final boolean will;

        private AllocationInfo(AllocationEvent event, boolean will) {
            this.value = event.getValue();
            this.oldSize = event.getOldSize();
            this.newSize = event.getNewSize();
            this.will = will;
        }
    }

    @TruffleInstrument.Registration(id = "testAllocationReporter", services = TestAllocationReporter.class)
    public static class TestAllocationReporter extends EnableableInstrument implements AllocationListener {

        private EventBinding<TestAllocationReporter> allocationEventBinding;
        private Consumer<AllocationInfo>[] allocationConsumers;
        private int consumersIndex = 0;

        @Override
        public void setEnabled(boolean enabled) {
            if (enabled) {
                LanguageInfo testLanguage = getEnv().getLanguages().get(AllocationReporterLanguage.ID);
                allocationEventBinding = getEnv().getInstrumenter().attachAllocationListener(AllocationEventFilter.newBuilder().languages(testLanguage).build(), this);
            } else {
                allocationEventBinding.dispose();
            }
        }

        @SafeVarargs
        @SuppressWarnings("varargs")
        final void setAllocationConsumers(Consumer<AllocationInfo>... allocationConsumers) {
            consumersIndex = 0;
            this.allocationConsumers = allocationConsumers;
        }

        EventBinding<TestAllocationReporter> getAllocationEventBinding() {
            return allocationEventBinding;
        }

        @Override
        @TruffleBoundary
        public void onEnter(AllocationEvent event) {
            Consumer<AllocationInfo> consumer = allocationConsumers[consumersIndex++];
            consumer.accept(new AllocationInfo(event, true));
        }

        @Override
        @TruffleBoundary
        public void onReturnValue(AllocationEvent event) {
            Consumer<AllocationInfo> consumer = allocationConsumers[consumersIndex++];
            consumer.accept(new AllocationInfo(event, false));
        }

    }

}
