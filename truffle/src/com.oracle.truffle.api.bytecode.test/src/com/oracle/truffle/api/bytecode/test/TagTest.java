/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.bytecode.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.graalvm.polyglot.Context;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.ContextThreadLocal;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.bytecode.BytecodeConfig;
import com.oracle.truffle.api.bytecode.BytecodeLabel;
import com.oracle.truffle.api.bytecode.BytecodeLocal;
import com.oracle.truffle.api.bytecode.BytecodeNode;
import com.oracle.truffle.api.bytecode.BytecodeParser;
import com.oracle.truffle.api.bytecode.BytecodeRootNode;
import com.oracle.truffle.api.bytecode.BytecodeRootNodes;
import com.oracle.truffle.api.bytecode.ConstantOperand;
import com.oracle.truffle.api.bytecode.ContinuationResult;
import com.oracle.truffle.api.bytecode.EpilogExceptional;
import com.oracle.truffle.api.bytecode.EpilogReturn;
import com.oracle.truffle.api.bytecode.GenerateBytecode;
import com.oracle.truffle.api.bytecode.Instruction;
import com.oracle.truffle.api.bytecode.Instrumentation;
import com.oracle.truffle.api.bytecode.Operation;
import com.oracle.truffle.api.bytecode.OperationProxy;
import com.oracle.truffle.api.bytecode.Prolog;
import com.oracle.truffle.api.bytecode.TagTree;
import com.oracle.truffle.api.bytecode.test.error_tests.ExpectError;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.EventContext;
import com.oracle.truffle.api.instrumentation.ExecutionEventListener;
import com.oracle.truffle.api.instrumentation.ExecutionEventNode;
import com.oracle.truffle.api.instrumentation.Instrumenter;
import com.oracle.truffle.api.instrumentation.ProvidedTags;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.instrumentation.StandardTags.CallTag;
import com.oracle.truffle.api.instrumentation.StandardTags.ExpressionTag;
import com.oracle.truffle.api.instrumentation.StandardTags.RootBodyTag;
import com.oracle.truffle.api.instrumentation.StandardTags.RootTag;
import com.oracle.truffle.api.instrumentation.StandardTags.StatementTag;
import com.oracle.truffle.api.instrumentation.Tag;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.NodeLibrary;
import com.oracle.truffle.api.nodes.ControlFlowException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.strings.TruffleString.Encoding;

public class TagTest extends AbstractInstructionTest {

    private static TagInstrumentationTestRootNode parseComplete(BytecodeParser<TagInstrumentationTestRootNodeGen.Builder> parser) {
        BytecodeRootNodes<TagInstrumentationTestRootNode> nodes = TagInstrumentationTestRootNodeGen.create(TagTestLanguage.REF.get(null), BytecodeConfig.COMPLETE, parser);
        TagInstrumentationTestRootNode root = nodes.getNode(0);
        return root;
    }

    private static TagInstrumentationTestRootNode parse(BytecodeParser<TagInstrumentationTestRootNodeGen.Builder> parser) {
        BytecodeRootNodes<TagInstrumentationTestRootNode> nodes = TagInstrumentationTestRootNodeGen.create(TagTestLanguage.REF.get(null), BytecodeConfig.DEFAULT, parser);
        TagInstrumentationTestRootNode root = nodes.getNode(0);
        return root;
    }

    private static TagInstrumentationTestWithPrologAndEpilogRootNode parseProlog(BytecodeParser<TagInstrumentationTestWithPrologAndEpilogRootNodeGen.Builder> parser) {
        BytecodeRootNodes<TagInstrumentationTestWithPrologAndEpilogRootNode> nodes = TagInstrumentationTestWithPrologAndEpilogRootNodeGen.create(TagTestLanguage.REF.get(null), BytecodeConfig.DEFAULT,
                        parser);
        TagInstrumentationTestWithPrologAndEpilogRootNode root = nodes.getNode(0);
        return root;
    }

    Context context;
    Instrumenter instrumenter;

    @Before
    public void setup() {
        context = Context.create(TagTestLanguage.ID);
        context.initialize(TagTestLanguage.ID);
        context.enter();
        instrumenter = context.getEngine().getInstruments().get(TagTestInstrumentation.ID).lookup(Instrumenter.class);
    }

    @After
    public void tearDown() {
        context.close();
    }

    enum EventKind {
        ENTER,
        RETURN_VALUE,
        UNWIND,
        EXCEPTIONAL,
        YIELD,
        RESUME,
    }

    @SuppressWarnings("unchecked")
    record Event(int id, EventKind kind, int startBci, int endBci, Object value, List<Class<?>> tags) {
        Event(EventKind kind, int startBci, int endBci, Object value, Class<?>... tags) {
            this(-1, kind, startBci, endBci, value, List.of(tags));
        }

        Event(int id, EventKind kind, int startBci, int endBci, Object value, Class<?>... tags) {
            this(id, kind, startBci, endBci, value, List.of(tags));
        }

        @Override
        public String toString() {
            return "Event [id=" + id + ", kind=" + kind + ", startBci=" + "0x" + Integer.toHexString(startBci) + ", endBci=" + "0x" + Integer.toHexString(endBci) + ", value=" + value + ", tags=" +
                            tags + "]";
        }

    }

    private List<Event> attachEventListener(SourceSectionFilter filter) {
        List<Event> events = new ArrayList<>();
        instrumenter.attachExecutionEventFactory(filter, (e) -> {
            TagTree tree = (TagTree) e.getInstrumentedNode();
            return new ExecutionEventNode() {

                @Override
                public void onEnter(VirtualFrame f) {
                    emitEvent(EventKind.ENTER, null);
                }

                @Override
                public void onReturnValue(VirtualFrame f, Object arg) {
                    emitEvent(EventKind.RETURN_VALUE, arg);
                }

                @Override
                protected Object onUnwind(VirtualFrame frame, Object info) {
                    emitEvent(EventKind.UNWIND, info);
                    return null;
                }

                @Override
                public void onReturnExceptional(VirtualFrame frame, Throwable t) {
                    emitEvent(EventKind.EXCEPTIONAL, t);
                }

                @Override
                protected void onYield(VirtualFrame frame, Object value) {
                    emitEvent(EventKind.YIELD, value);
                }

                @Override
                protected void onResume(VirtualFrame frame) {
                    emitEvent(EventKind.RESUME, null);
                }

                @TruffleBoundary
                private void emitEvent(EventKind kind, Object arg) {
                    events.add(new Event(TagTestLanguage.REF.get(this).threadLocal.get().newEvent(), kind, tree.getEnterBytecodeIndex(), tree.getReturnBytecodeIndex(), arg,
                                    tree.getTags().toArray(Class[]::new)));
                }

            };
        });
        return events;
    }

    @Test
    public void testStatementsCached() {
        TagInstrumentationTestRootNode node = parse((b) -> {
            b.beginRoot();

            var local = b.createLocal();
            b.beginBlock();

            b.beginTag(StatementTag.class);
            b.beginStoreLocal(local);
            b.beginTag(ExpressionTag.class);
            b.emitLoadConstant(42);
            b.endTag(ExpressionTag.class);
            b.endStoreLocal();
            b.endTag(StatementTag.class);

            b.beginReturn();
            b.beginTag(StatementTag.class, ExpressionTag.class);
            b.beginTag(ExpressionTag.class);
            b.emitLoadLocal(local);
            b.endTag(ExpressionTag.class);
            b.endTag(StatementTag.class, ExpressionTag.class);
            b.endReturn();

            b.endBlock();

            b.endRoot();
        });
        node.getBytecodeNode().setUncachedThreshold(0);

        assertInstructions(node,
                        "load.constant",
                        "store.local",
                        "load.local",
                        "return");
        assertEquals(42, node.getCallTarget().call());
        assertQuickenings(node, 3, 2);

        assertInstructions(node,
                        "load.constant$Int",
                        "store.local$Int$Int",
                        "load.local$Int",
                        "return");

        List<Event> events = attachEventListener(SourceSectionFilter.newBuilder().tagIs(StandardTags.StatementTag.class).build());

        assertInstructions(node,
                        "tag.enter",
                        "load.constant",
                        "store.local",
                        "tag.leaveVoid",
                        "tag.enter",
                        "load.local",
                        "tag.leave",
                        "return");

        boolean[] isInstrumentation = new boolean[]{true, false, false, true, true, false, true, false};
        List<Instruction> instructions = node.getBytecodeNode().getInstructionsAsList();
        for (int i = 0; i < instructions.size(); i++) {
            assertEquals(isInstrumentation[i], instructions.get(i).isInstrumentation());
        }

        assertEquals(42, node.getCallTarget().call());

        assertInstructions(node,
                        "tag.enter",
                        "load.constant$Int",
                        "store.local$Int$Int",
                        "tag.leaveVoid",
                        "tag.enter",
                        "load.local$Int$unboxed",
                        "tag.leave$Int",
                        "return");
        instructions = node.getBytecodeNode().getInstructionsAsList();
        for (int i = 0; i < instructions.size(); i++) {
            assertEquals(isInstrumentation[i], instructions.get(i).isInstrumentation());
        }

        QuickeningCounts counts = assertQuickenings(node, 8, 4);

        instructions = node.getBytecodeNode().getInstructionsAsList();
        int enter1 = instructions.get(0).getBytecodeIndex();
        int leave1 = instructions.get(3).getBytecodeIndex();

        int enter2 = instructions.get(4).getBytecodeIndex();
        int leave2 = instructions.get(6).getBytecodeIndex();

        assertEvents(node,
                        events,
                        new Event(EventKind.ENTER, enter1, leave1, null, StatementTag.class),
                        new Event(EventKind.RETURN_VALUE, enter1, leave1, null, StatementTag.class),
                        new Event(EventKind.ENTER, enter2, leave2, null, StatementTag.class),
                        new Event(EventKind.RETURN_VALUE, enter2, leave2, 42, StatementTag.class));

        assertStable(counts, node);

    }

    @Test
    public void testTagsEmptyErrors() {
        parse((b) -> {
            b.beginRoot();

            assertFails(() -> b.beginTag(), IllegalArgumentException.class);
            assertFails(() -> b.beginTag((Class<?>) null), NullPointerException.class);
            assertFails(() -> b.beginTag((Class<?>[]) null), NullPointerException.class);
            assertFails(() -> b.beginTag(CallTag.class), IllegalArgumentException.class);

            assertFails(() -> b.endTag(CallTag.class), IllegalArgumentException.class);
            assertFails(() -> b.endTag(), IllegalArgumentException.class);
            assertFails(() -> b.endTag((Class<?>) null), NullPointerException.class);
            assertFails(() -> b.endTag((Class<?>[]) null), NullPointerException.class);

            b.endRoot();
        });
    }

    @Test
    public void testTagsMismatchError() {
        TagInstrumentationTestRootNode node = parse((b) -> {
            b.beginRoot();
            b.beginReturn();
            b.beginTag(StatementTag.class, ExpressionTag.class);
            b.emitLoadConstant(42);
            b.endTag(StatementTag.class);
            b.endReturn();
            b.endRoot();
        });

        assertInstructions(node,
                        "load.constant",
                        "return");
        assertEquals(42, node.getCallTarget().call());

        // When we include statement tags, they balance, so there should be no errors.
        List<Event> events = attachEventListener(SourceSectionFilter.newBuilder().tagIs(StandardTags.StatementTag.class).build());
        assertInstructions(node,
                        "tag.enter",
                        "load.constant",
                        "tag.leave",
                        "return");
        assertEquals(42, node.getCallTarget().call());

        List<Instruction> instructions = node.getBytecodeNode().getInstructionsAsList();
        int enter = instructions.get(0).getBytecodeIndex();
        int leave = instructions.get(2).getBytecodeIndex();

        assertEvents(node,
                        events,
                        new Event(EventKind.ENTER, enter, leave, null, StatementTag.class),
                        new Event(EventKind.RETURN_VALUE, enter, leave, 42, StatementTag.class));

        // When we include expression tags, the mismatch should be detected.
        assertFails(() -> attachEventListener(SourceSectionFilter.newBuilder().tagIs(StandardTags.StatementTag.class,
                        StandardTags.ExpressionTag.class).build()), IllegalArgumentException.class);
    }

    @Test
    public void testStatementsUncached() {
        TagInstrumentationTestRootNode node = parse((b) -> {
            b.beginRoot();

            var local = b.createLocal();
            b.beginBlock();

            b.beginTag(StatementTag.class);
            b.beginStoreLocal(local);
            b.beginTag(ExpressionTag.class);
            b.emitLoadConstant(42);
            b.endTag(ExpressionTag.class);
            b.endStoreLocal();
            b.endTag(StatementTag.class);

            b.beginReturn();
            b.beginTag(StatementTag.class, ExpressionTag.class);
            b.beginTag(ExpressionTag.class);
            b.emitLoadLocal(local);
            b.endTag(ExpressionTag.class);
            b.endTag(StatementTag.class, ExpressionTag.class);
            b.endReturn();

            b.endBlock();

            b.endRoot();
        });
        node.getBytecodeNode().setUncachedThreshold(Integer.MAX_VALUE);

        assertInstructions(node,
                        "load.constant",
                        "store.local",
                        "load.local",
                        "return");
        assertEquals(42, node.getCallTarget().call());
        assertQuickenings(node, 0, 0);

        assertInstructions(node,
                        "load.constant",
                        "store.local",
                        "load.local",
                        "return");

        List<Event> events = attachEventListener(SourceSectionFilter.newBuilder().tagIs(StandardTags.StatementTag.class).build());

        assertInstructions(node,
                        "tag.enter",
                        "load.constant",
                        "store.local",
                        "tag.leaveVoid",
                        "tag.enter",
                        "load.local",
                        "tag.leave",
                        "return");

        assertEquals(42, node.getCallTarget().call());

        assertInstructions(node,
                        "tag.enter",
                        "load.constant",
                        "store.local",
                        "tag.leaveVoid",
                        "tag.enter",
                        "load.local",
                        "tag.leave",
                        "return");

        QuickeningCounts counts = assertQuickenings(node, 0, 0);

        List<Instruction> instructions = node.getBytecodeNode().getInstructionsAsList();
        int enter1 = instructions.get(0).getBytecodeIndex();
        int leave1 = instructions.get(3).getBytecodeIndex();
        int enter2 = instructions.get(4).getBytecodeIndex();
        int leave2 = instructions.get(6).getBytecodeIndex();

        assertEvents(node,
                        events,
                        new Event(EventKind.ENTER, enter1, leave1, null, StatementTag.class),
                        new Event(EventKind.RETURN_VALUE, enter1, leave1, null, StatementTag.class),
                        new Event(EventKind.ENTER, enter2, leave2, null, StatementTag.class),
                        new Event(EventKind.RETURN_VALUE, enter2, leave2, 42, StatementTag.class));

        assertStable(counts, node);
    }

    private static void assertEvents(BytecodeRootNode node, List<Event> actualEvents, Event... expectedEvents) {
        try {
            assertEquals("expectedEvents: " + Arrays.toString(expectedEvents) + " actualEvents:" + actualEvents, expectedEvents.length, actualEvents.size());

            for (int i = 0; i < expectedEvents.length; i++) {
                Event actualEvent = actualEvents.get(i);
                Event expectedEvent = expectedEvents[i];

                assertEquals("event kind at index " + i, expectedEvent.kind, actualEvent.kind);
                if (expectedEvent.value instanceof Class) {
                    assertEquals("event value at index " + i, expectedEvent.value, actualEvent.value.getClass());
                } else {
                    assertEquals("event value at index " + i, expectedEvent.value, actualEvent.value);
                }
                assertEquals("start bci at index " + i, "0x" + Integer.toHexString(expectedEvent.startBci), "0x" + Integer.toHexString(actualEvent.startBci));
                assertEquals("end bci at index " + i, "0x" + Integer.toHexString(expectedEvent.endBci), "0x" + Integer.toHexString(actualEvent.endBci));
                assertEquals("end bci at index " + i, Set.copyOf(expectedEvent.tags), Set.copyOf(actualEvent.tags));

                if (expectedEvent.id != -1) {
                    assertEquals("event id at index " + i, expectedEvent.id, actualEvent.id);
                }
            }
        } catch (AssertionError e) {
            System.err.println("Actual events:");
            for (Event event : actualEvents) {
                System.err.println(event);
            }

            System.err.println("Dump:");
            System.err.println(node.dump());
            throw e;
        }
    }

    @Test
    public void testStatementsAndExpressionUncached() {
        TagInstrumentationTestRootNode node = parse((b) -> {
            b.beginRoot();

            var local = b.createLocal();
            b.beginBlock();

            b.beginTag(StatementTag.class);
            b.beginStoreLocal(local);
            b.beginTag(ExpressionTag.class);
            b.emitLoadConstant(42);
            b.endTag(ExpressionTag.class);
            b.endStoreLocal();
            b.endTag(StatementTag.class);

            b.beginReturn();
            b.beginTag(StatementTag.class, ExpressionTag.class);
            b.beginTag(ExpressionTag.class);
            b.emitLoadLocal(local);
            b.endTag(ExpressionTag.class);
            b.endTag(StatementTag.class, ExpressionTag.class);
            b.endReturn();

            b.endBlock();

            b.endRoot();
        });
        node.getBytecodeNode().setUncachedThreshold(Integer.MAX_VALUE);

        assertInstructions(node,
                        "load.constant",
                        "store.local",
                        "load.local",
                        "return");
        assertEquals(42, node.getCallTarget().call());
        assertQuickenings(node, 0, 0);

        assertInstructions(node,
                        "load.constant",
                        "store.local",
                        "load.local",
                        "return");

        List<Event> events = attachEventListener(SourceSectionFilter.newBuilder().tagIs(StandardTags.StatementTag.class,
                        StandardTags.ExpressionTag.class).build());

        assertInstructions(node,
                        "tag.enter",
                        "tag.enter",
                        "load.constant",
                        "tag.leave",
                        "store.local",
                        "tag.leaveVoid",
                        "tag.enter",
                        "tag.enter",
                        "load.local",
                        "tag.leave",
                        "tag.leave",
                        "return");

        assertEquals(42, node.getCallTarget().call());
        assertInstructions(node,
                        "tag.enter",
                        "tag.enter",
                        "load.constant",
                        "tag.leave",
                        "store.local",
                        "tag.leaveVoid",
                        "tag.enter",
                        "tag.enter",
                        "load.local",
                        "tag.leave",
                        "tag.leave",
                        "return");

        QuickeningCounts counts = assertQuickenings(node, 0, 0);

        List<Instruction> instructions = node.getBytecodeNode().getInstructionsAsList();
        int enter1 = instructions.get(0).getBytecodeIndex();
        int enter2 = instructions.get(1).getBytecodeIndex();
        int leave2 = instructions.get(3).getBytecodeIndex();
        int leave1 = instructions.get(5).getBytecodeIndex();

        int enter3 = instructions.get(6).getBytecodeIndex();
        int enter4 = instructions.get(7).getBytecodeIndex();
        int leave4 = instructions.get(9).getBytecodeIndex();
        int leave3 = instructions.get(10).getBytecodeIndex();

        assertEvents(node,
                        events,
                        new Event(EventKind.ENTER, enter1, leave1, null, StatementTag.class),
                        new Event(EventKind.ENTER, enter2, leave2, null, ExpressionTag.class),
                        new Event(EventKind.RETURN_VALUE, enter2, leave2, 42, ExpressionTag.class),
                        new Event(EventKind.RETURN_VALUE, enter1, leave1, null, StatementTag.class),
                        new Event(EventKind.ENTER, enter3, leave3, null, StatementTag.class, ExpressionTag.class),
                        new Event(EventKind.ENTER, enter4, leave4, null, ExpressionTag.class),
                        new Event(EventKind.RETURN_VALUE, enter4, leave4, 42, ExpressionTag.class),
                        new Event(EventKind.RETURN_VALUE, enter3, leave3, 42, StatementTag.class, ExpressionTag.class));

        assertStable(counts, node);
    }

    @Test
    public void testStatementsAndExpressionCached() {
        TagInstrumentationTestRootNode node = parse((b) -> {
            b.beginRoot();

            var local = b.createLocal();
            b.beginBlock();

            b.beginTag(StatementTag.class);
            b.beginStoreLocal(local);
            b.beginTag(ExpressionTag.class);
            b.emitLoadConstant(42);
            b.endTag(ExpressionTag.class);
            b.endStoreLocal();
            b.endTag(StatementTag.class);

            b.beginReturn();
            b.beginTag(StatementTag.class, ExpressionTag.class);
            b.beginTag(ExpressionTag.class);
            b.emitLoadLocal(local);
            b.endTag(ExpressionTag.class);
            b.endTag(StatementTag.class, ExpressionTag.class);
            b.endReturn();

            b.endBlock();

            b.endRoot();
        });
        node.getBytecodeNode().setUncachedThreshold(0);

        assertInstructions(node,
                        "load.constant",
                        "store.local",
                        "load.local",
                        "return");
        assertEquals(42, node.getCallTarget().call());
        assertQuickenings(node, 3, 2);

        assertInstructions(node,
                        "load.constant$Int",
                        "store.local$Int$Int",
                        "load.local$Int",
                        "return");

        List<Event> events = attachEventListener(SourceSectionFilter.newBuilder().tagIs(StandardTags.StatementTag.class, StandardTags.ExpressionTag.class).build());

        assertInstructions(node,
                        "tag.enter",
                        "tag.enter",
                        "load.constant",
                        "tag.leave",
                        "store.local",
                        "tag.leaveVoid",
                        "tag.enter",
                        "tag.enter",
                        "load.local",
                        "tag.leave",
                        "tag.leave",
                        "return");

        assertEquals(42, node.getCallTarget().call());
        assertInstructions(node,
                        "tag.enter",
                        "tag.enter",
                        "load.constant$Int",
                        "tag.leave$Int$unboxed",
                        "store.local$Int$Int",
                        "tag.leaveVoid",
                        "tag.enter",
                        "tag.enter",
                        "load.local$Int$unboxed",
                        "tag.leave$Int$unboxed",
                        "tag.leave$Int",
                        "return");

        QuickeningCounts counts = assertQuickenings(node, 12, 4);

        List<Instruction> instructions = node.getBytecodeNode().getInstructionsAsList();
        int enter1 = instructions.get(0).getBytecodeIndex();
        int enter2 = instructions.get(1).getBytecodeIndex();
        int leave2 = instructions.get(3).getBytecodeIndex();
        int leave1 = instructions.get(5).getBytecodeIndex();

        int enter3 = instructions.get(6).getBytecodeIndex();
        int enter4 = instructions.get(7).getBytecodeIndex();
        int leave4 = instructions.get(9).getBytecodeIndex();
        int leave3 = instructions.get(10).getBytecodeIndex();

        assertEvents(node,
                        events,
                        new Event(EventKind.ENTER, enter1, leave1, null, StatementTag.class),
                        new Event(EventKind.ENTER, enter2, leave2, null, ExpressionTag.class),
                        new Event(EventKind.RETURN_VALUE, enter2, leave2, 42, ExpressionTag.class),
                        new Event(EventKind.RETURN_VALUE, enter1, leave1, null, StatementTag.class),
                        new Event(EventKind.ENTER, enter3, leave3, null, StatementTag.class, ExpressionTag.class),
                        new Event(EventKind.ENTER, enter4, leave4, null, ExpressionTag.class),
                        new Event(EventKind.RETURN_VALUE, enter4, leave4, 42, ExpressionTag.class),
                        new Event(EventKind.RETURN_VALUE, enter3, leave3, 42, StatementTag.class, ExpressionTag.class));

        assertStable(counts, node);
    }

    @Test
    public void testImplicitRootTagsNoProlog() {
        TagInstrumentationTestRootNode node = parse((b) -> {
            b.beginRoot();
            b.beginReturn();
            b.emitLoadConstant(42);
            b.endReturn();
            b.endRoot();
        });
        assertEquals(42, node.getCallTarget().call());

        assertInstructions(node,
                        "load.constant",
                        "return");

        List<Event> events = attachEventListener(SourceSectionFilter.newBuilder().tagIs(StandardTags.RootBodyTag.class, StandardTags.RootTag.class).build());
        assertInstructions(node,
                        "tag.enter",
                        "load.constant",
                        "tag.leave",
                        "return",
                        "tag.leave",
                        "return");

        assertEquals(42, node.getCallTarget().call());
        assertEvents(node, events,
                        new Event(EventKind.ENTER, 0x0000, 0x0018, null, RootTag.class, RootBodyTag.class),
                        new Event(EventKind.RETURN_VALUE, 0x0000, 0x0018, 42, RootTag.class, RootBodyTag.class));

    }

    @Test
    public void testRootExceptionHandler() {
        TagInstrumentationTestWithPrologAndEpilogRootNode node = parseProlog((b) -> {
            b.beginRoot();
            b.emitThrow();
            b.endRoot();
        });

        assertFails(() -> node.getCallTarget().call(), TestException.class);

        assertInstructions(node,
                        "c.EnterMethod",
                        "c.Throw",
                        "load.null",
                        "c.LeaveValue",
                        "return");

        List<Event> events = attachEventListener(SourceSectionFilter.newBuilder().tagIs(StandardTags.RootTag.class).build());
        assertInstructions(node,
                        "tag.enter",
                        "c.EnterMethod",
                        "c.Throw",
                        "load.null",
                        "c.LeaveValue",
                        "tag.leave",
                        "return");

        assertFails(() -> node.getCallTarget().call(), TestException.class);
        assertEvents(node,
                        events,
                        new Event(EventKind.ENTER, 0x0000, 0x001e, null, RootTag.class),
                        new Event(EventKind.EXCEPTIONAL, 0x0000, 0x001e, TestException.class, RootTag.class));
    }

    @Test
    public void testRootExceptionHandlerReturnValue() {
        TagInstrumentationTestWithPrologAndEpilogRootNode node = parseProlog((b) -> {
            b.beginRoot();
            b.beginReturn();
            b.emitLoadConstant(42);
            b.endReturn();
            b.endRoot();
        });

        assertEquals(42, node.getCallTarget().call());

        assertInstructions(node,
                        "c.EnterMethod",
                        "load.constant",
                        "c.LeaveValue",
                        "return");

        List<Event> events = attachEventListener(SourceSectionFilter.newBuilder().tagIs(StandardTags.RootTag.class).build());

        assertInstructions(node,
                        "tag.enter",
                        "c.EnterMethod",
                        "load.constant",
                        "c.LeaveValue",
                        "tag.leave",
                        "return",
                        "tag.leave",
                        "return");

        assertEquals(42, node.getCallTarget().call());
        assertEvents(node,
                        events,
                        new Event(EventKind.ENTER, 0x0000, 0x0028, null, RootTag.class),
                        new Event(EventKind.RETURN_VALUE, 0x0000, 0x0028, Integer.class, RootTag.class));

    }

    @Test
    public void testRootBodyExceptionHandler() {
        TagInstrumentationTestWithPrologAndEpilogRootNode node = parseProlog((b) -> {
            b.beginRoot();
            b.emitThrow();
            b.endRoot();
        });

        assertFails(() -> node.getCallTarget().call(), TestException.class);

        assertInstructions(node,
                        "c.EnterMethod",
                        "c.Throw",
                        "load.null",
                        "c.LeaveValue",
                        "return");

        List<Event> events = attachEventListener(SourceSectionFilter.newBuilder().tagIs(StandardTags.RootBodyTag.class).build());
        assertInstructions(node,
                        "c.EnterMethod",
                        "tag.enter",
                        "c.Throw",
                        "load.null",
                        "tag.leave",
                        "c.LeaveValue",
                        "return");

        assertFails(() -> node.getCallTarget().call(), TestException.class);
        assertEvents(node,
                        events,
                        new Event(EventKind.ENTER, 0x0006, 0x0014, null, RootBodyTag.class),
                        new Event(EventKind.EXCEPTIONAL, 0x0006, 0x0014, TestException.class, RootBodyTag.class));
    }

    @Test
    public void testUnwindInReturn() {
        TagInstrumentationTestRootNode node = parse((b) -> {
            b.beginRoot();
            b.beginReturn();
            b.beginTag(ExpressionTag.class);
            b.beginAdd();
            b.emitLoadConstant(20);
            b.emitLoadConstant(21);
            b.endAdd();
            b.endTag(ExpressionTag.class);
            b.endReturn();
            b.endRoot();
        });

        assertEquals(41, node.getCallTarget().call());
        assertInstructions(node,
                        "load.constant",
                        "load.constant",
                        "c.Add",
                        "return");

        instrumenter.attachExecutionEventFactory(SourceSectionFilter.newBuilder().tagIs(StandardTags.ExpressionTag.class).build(), (e) -> {
            return new ExecutionEventNode() {
                @Override
                public void onReturnValue(VirtualFrame f, Object arg) {
                    if (arg.equals(41)) {
                        throw e.createUnwind(42);
                    }
                }

                @Override
                protected Object onUnwind(VirtualFrame frame, Object info) {
                    return info;
                }
            };
        });

        assertInstructions(node,
                        "tag.enter",
                        "load.constant",
                        "load.constant",
                        "c.Add",
                        "tag.leave",
                        "return");

        assertEquals(42, node.getCallTarget().call());
    }

    @Test
    public void testUnwindInEnter() {
        TagInstrumentationTestRootNode node = parse((b) -> {
            b.beginRoot();
            b.beginReturn();
            b.beginTag(ExpressionTag.class);
            b.beginAdd();
            b.emitLoadConstant(20);
            b.emitLoadConstant(21);
            b.endAdd();
            b.endTag(ExpressionTag.class);
            b.endReturn();
            b.endRoot();
        });

        assertEquals(41, node.getCallTarget().call());
        assertInstructions(node,
                        "load.constant",
                        "load.constant",
                        "c.Add",
                        "return");

        instrumenter.attachExecutionEventFactory(SourceSectionFilter.newBuilder().tagIs(StandardTags.ExpressionTag.class).build(), (e) -> {
            return new ExecutionEventNode() {
                @Override
                protected void onEnter(VirtualFrame frame) {
                    throw e.createUnwind(42);
                }

                @Override
                protected Object onUnwind(VirtualFrame frame, Object info) {
                    return info;
                }
            };
        });

        assertInstructions(node,
                        "tag.enter",
                        "load.constant",
                        "load.constant",
                        "c.Add",
                        "tag.leave",
                        "return");

        assertEquals(42, node.getCallTarget().call());
    }

    @Test
    public void testUnwindInRootBody() {
        TagInstrumentationTestWithPrologAndEpilogRootNode node = parseProlog((b) -> {
            b.beginRoot();
            b.emitLoadConstant(40);
            b.emitLoadConstant(41);
            b.endRoot();
        });
        assertEquals(41, node.getCallTarget().call());
        assertInstructions(node,
                        "c.EnterMethod",
                        "load.constant",
                        "pop",
                        "load.constant",
                        "c.LeaveValue",
                        "return");

        instrumenter.attachExecutionEventFactory(SourceSectionFilter.newBuilder().tagIs(StandardTags.RootBodyTag.class).build(), (e) -> {
            return new ExecutionEventNode() {
                @Override
                protected void onEnter(VirtualFrame frame) {
                    throw e.createUnwind(42);
                }

                @Override
                protected Object onUnwind(VirtualFrame frame, Object info) {
                    return info;
                }
            };
        });

        assertInstructions(node,
                        "c.EnterMethod",
                        "tag.enter",
                        "load.constant",
                        "pop",
                        "load.constant",
                        "tag.leave",
                        "c.LeaveValue",
                        "return");

        assertEquals(42, node.getCallTarget().call());
    }

    @Test
    public void testUnwindInRoot() {
        TagInstrumentationTestRootNode node = parse((b) -> {
            b.beginRoot();
            b.beginTag(StatementTag.class);
            b.beginReturn();
            b.emitLoadConstant(41);
            b.endReturn();
            b.endTag(StatementTag.class);
            b.endRoot();
        });

        assertEquals(41, node.getCallTarget().call());
        assertInstructions(node,
                        "load.constant",
                        "return");

        instrumenter.attachExecutionEventFactory(SourceSectionFilter.newBuilder().tagIs(StandardTags.RootTag.class).build(), (e) -> {
            return new ExecutionEventNode() {
                @Override
                protected void onEnter(VirtualFrame frame) {
                    throw e.createUnwind(42);
                }

                @Override
                protected Object onUnwind(VirtualFrame frame, Object info) {
                    return info;
                }
            };
        });

        assertInstructions(node,
                        "tag.enter",
                        "load.constant",
                        "tag.leave",
                        "return",
                        "tag.leave",
                        "return"); // reachable only through instrumentation

        assertEquals(42, node.getCallTarget().call());
    }

    @Test
    public void testImplicitCustomTag() {
        TagInstrumentationTestRootNode node = parse((b) -> {
            b.beginRoot();
            b.beginReturn();
            b.beginImplicitExpressionAdd();
            b.emitLoadConstant(20);
            b.emitLoadConstant(22);
            b.endImplicitExpressionAdd();
            b.endReturn();
            b.endRoot();
        });
        assertEquals(42, node.getCallTarget().call());

        assertInstructions(node,
                        "load.constant",
                        "load.constant",
                        "c.ImplicitExpressionAdd",
                        "return");

        List<Event> events = attachEventListener(SourceSectionFilter.newBuilder().tagIs(StandardTags.ExpressionTag.class).build());
        assertInstructions(node,
                        "tag.enter",
                        "load.constant",
                        "load.constant",
                        "c.ImplicitExpressionAdd",
                        "tag.leave",
                        "return");

        assertEquals(42, node.getCallTarget().call());

        assertEvents(node,
                        events,
                        new Event(EventKind.ENTER, 0x0000, 0x001c, null, ExpressionTag.class),
                        new Event(EventKind.RETURN_VALUE, 0x0000, 0x001c, 42, ExpressionTag.class));

    }

    @Test
    public void testImplicitCustomProxyTag() {
        TagInstrumentationTestRootNode node = parse((b) -> {
            b.beginRoot();
            b.beginReturn();
            b.beginImplicitExpressionAddProxy();
            b.emitLoadConstant(20);
            b.emitLoadConstant(22);
            b.endImplicitExpressionAddProxy();
            b.endReturn();
            b.endRoot();
        });
        assertEquals(42, node.getCallTarget().call());

        assertInstructions(node,
                        "load.constant",
                        "load.constant",
                        "c.ImplicitExpressionAddProxy",
                        "return");

        List<Event> events = attachEventListener(SourceSectionFilter.newBuilder().tagIs(StandardTags.ExpressionTag.class).build());
        assertInstructions(node,
                        "tag.enter",
                        "load.constant",
                        "load.constant",
                        "c.ImplicitExpressionAddProxy",
                        "tag.leave",
                        "return");

        assertEquals(42, node.getCallTarget().call());

        assertEvents(node,
                        events,
                        new Event(EventKind.ENTER, 0x0000, 0x001c, null, ExpressionTag.class),
                        new Event(EventKind.RETURN_VALUE, 0x0000, 0x001c, 42, ExpressionTag.class));

    }

    @Test
    public void testImplicitRootBodyTagNoProlog() {
        TagInstrumentationTestRootNode node = parse((b) -> {
            b.beginRoot();
            b.beginReturn();
            b.emitLoadConstant(42);
            b.endReturn();
            b.endRoot();
        });
        assertEquals(42, node.getCallTarget().call());

        assertInstructions(node,
                        "load.constant",
                        "return");

        List<Event> events = attachEventListener(SourceSectionFilter.newBuilder().tagIs(StandardTags.RootBodyTag.class).build());
        assertInstructions(node,
                        "tag.enter",
                        "load.constant",
                        "tag.leave",
                        "return",
                        "tag.leave",
                        "return");

        assertEquals(42, node.getCallTarget().call());

        assertEvents(node,
                        events,
                        new Event(EventKind.ENTER, 0x0000, 0x0018, null, RootBodyTag.class),
                        new Event(EventKind.RETURN_VALUE, 0x0000, 0x0018, 42, RootBodyTag.class));

    }

    @Test
    public void testImplicitRootTagNoProlog() {
        TagInstrumentationTestRootNode node = parse((b) -> {
            b.beginRoot();
            b.beginReturn();
            b.emitLoadConstant(42);
            b.endReturn();
            b.endRoot();
        });
        assertEquals(42, node.getCallTarget().call());

        assertInstructions(node,
                        "load.constant",
                        "return");

        List<Event> events = attachEventListener(SourceSectionFilter.newBuilder().tagIs(StandardTags.RootTag.class).build());
        assertInstructions(node,
                        "tag.enter",
                        "load.constant",
                        "tag.leave",
                        "return",
                        "tag.leave",
                        "return");

        assertEquals(42, node.getCallTarget().call());

        assertEvents(node,
                        events,
                        new Event(EventKind.ENTER, 0x0000, 0x0018, null, RootTag.class),
                        new Event(EventKind.RETURN_VALUE, 0x0000, 0x0018, 42, RootTag.class));

    }

    @Test
    public void testImplicitRootTagProlog() {
        TagInstrumentationTestWithPrologAndEpilogRootNode node = parseProlog((b) -> {
            b.beginRoot();
            b.beginReturn();
            b.emitLoadConstant(42);
            b.endReturn();
            b.endRoot();
        });
        ThreadLocalData tl = TagTestLanguage.getThreadData(null);
        tl.trackProlog = true;

        assertEquals(42, node.getCallTarget().call());

        assertInstructions(node,
                        "c.EnterMethod",
                        "load.constant",
                        "c.LeaveValue",
                        "return");

        assertEquals(0, tl.prologIndex);
        assertEquals(1, tl.epilogValue);
        assertEquals(42, tl.epilogValueObject);
        assertEquals(-1, tl.epilogExceptional);
        tl.reset();

        List<Event> events = attachEventListener(SourceSectionFilter.newBuilder().tagIs(StandardTags.RootTag.class).build());
        assertInstructions(node,
                        "tag.enter",
                        "c.EnterMethod",
                        "load.constant",
                        "c.LeaveValue",
                        "tag.leave",
                        "return",
                        "tag.leave",
                        "return");

        assertEquals(42, node.getCallTarget().call());

        assertEquals(1, tl.prologIndex);
        assertEquals(2, tl.epilogValue);
        assertEquals(42, tl.epilogValueObject);
        assertEquals(-1, tl.epilogExceptional);

        assertEvents(node,
                        events,
                        new Event(0, EventKind.ENTER, 0x0000, 0x0028, null, RootTag.class),
                        new Event(3, EventKind.RETURN_VALUE, 0x0000, 0x0028, 42, RootTag.class));

    }

    @Test
    public void testImplicitRootBodyTagProlog() {
        TagInstrumentationTestWithPrologAndEpilogRootNode node = parseProlog((b) -> {
            b.beginRoot();
            b.beginReturn();
            b.emitLoadConstant(42);
            b.endReturn();
            b.endRoot();
        });
        ThreadLocalData tl = TagTestLanguage.getThreadData(null);
        tl.trackProlog = true;

        assertEquals(42, node.getCallTarget().call());

        assertInstructions(node,
                        "c.EnterMethod",
                        "load.constant",
                        "c.LeaveValue",
                        "return");

        assertEquals(0, tl.prologIndex);
        assertEquals(1, tl.epilogValue);
        assertEquals(42, tl.epilogValueObject);
        assertEquals(-1, tl.epilogExceptional);
        tl.reset();

        List<Event> events = attachEventListener(SourceSectionFilter.newBuilder().tagIs(StandardTags.RootBodyTag.class).build());
        assertInstructions(node,
                        "c.EnterMethod",
                        "tag.enter",
                        "load.constant",
                        "tag.leave",
                        "c.LeaveValue",
                        "return",
                        "tag.leave",
                        "c.LeaveValue",
                        "return");

        assertEquals(42, node.getCallTarget().call());

        assertEquals(0, tl.prologIndex);
        assertEquals(3, tl.epilogValue);
        assertEquals(42, tl.epilogValueObject);
        assertEquals(-1, tl.epilogExceptional);

        assertEvents(node,
                        events,
                        new Event(1, EventKind.ENTER, 0x0006, 0x0028, null, RootBodyTag.class),
                        new Event(2, EventKind.RETURN_VALUE, 0x0006, 0x0028, 42, RootBodyTag.class));
    }

    @Test
    public void testImplicitRootTagsProlog() {
        TagInstrumentationTestWithPrologAndEpilogRootNode node = parseProlog((b) -> {
            b.beginRoot();
            b.beginReturn();
            b.emitLoadConstant(42);
            b.endReturn();
            b.endRoot();
        });
        ThreadLocalData tl = TagTestLanguage.getThreadData(null);
        tl.trackProlog = true;

        assertEquals(42, node.getCallTarget().call());
        assertInstructions(node,
                        "c.EnterMethod",
                        "load.constant",
                        "c.LeaveValue",
                        "return");

        assertEquals(0, tl.prologIndex);
        assertEquals(1, tl.epilogValue);
        assertEquals(42, tl.epilogValueObject);
        assertEquals(-1, tl.epilogExceptional);
        tl.reset();

        List<Event> events = attachEventListener(SourceSectionFilter.newBuilder().tagIs(StandardTags.RootBodyTag.class, StandardTags.RootTag.class).build());
        assertInstructions(node,
                        "tag.enter",
                        "c.EnterMethod",
                        "tag.enter",
                        "load.constant",
                        "tag.leave",
                        "c.LeaveValue",
                        "tag.leave",
                        "return",
                        "tag.leave",
                        "c.LeaveValue",
                        "tag.leave",
                        "return");

        assertEquals(42, node.getCallTarget().call());

        assertEquals(1, tl.prologIndex);
        assertEquals(4, tl.epilogValue);
        assertEquals(42, tl.epilogValueObject);
        assertEquals(-1, tl.epilogExceptional);

        assertEvents(node,
                        events,
                        new Event(0, EventKind.ENTER, 0x0000, 0x004c, null, RootTag.class),
                        new Event(2, EventKind.ENTER, 0x000c, 0x0038, null, RootBodyTag.class),
                        new Event(3, EventKind.RETURN_VALUE, 0x000c, 0x0038, 42, RootBodyTag.class),
                        new Event(5, EventKind.RETURN_VALUE, 0x0000, 0x004c, 42, RootTag.class));

    }

    /*
     * Tests that return reachability optimization does not eliminate instrutions if there is a
     * jump.
     */
    @Test
    public void testImplicitJumpAfterReturn() {
        TagInstrumentationTestRootNode node = parse((b) -> {
            b.beginRoot();

            var l = b.createLabel();

            b.beginTag(ExpressionTag.class);
            b.emitBranch(l);
            b.endTag(ExpressionTag.class);
            b.beginReturn();
            b.emitLoadConstant(42);
            b.endReturn();

            b.emitLabel(l);

            b.endRoot();
        });

        assertInstructions(node,
                        "branch",
                        "load.null",
                        "return");

        List<Event> events = attachEventListener(SourceSectionFilter.newBuilder().tagIs(StandardTags.RootTag.class, StandardTags.ExpressionTag.class).build());
        Assert.assertNull(node.getCallTarget().call());

        assertInstructions(node,
                        "tag.enter",
                        "tag.enter",
                        "tag.leaveVoid",
                        "branch",
                        "tag.leaveVoid",
                        "load.constant",
                        "tag.leave",
                        "return",
                        "load.null",
                        "tag.leave",
                        "return");

        // instrumentation events should be correct even if we hit a trap
        assertEvents(node,
                        events,
                        new Event(EventKind.ENTER, 0x0000, 0x0032, null, RootTag.class),
                        new Event(EventKind.ENTER, 0x0006, 0x0018, null, ExpressionTag.class),
                        new Event(EventKind.RETURN_VALUE, 0x0006, 0x0018, null, ExpressionTag.class),
                        new Event(EventKind.RETURN_VALUE, 0x0000, 0x0032, null, RootTag.class));

    }

    @Test
    public void testSourceSections() {
        Source s = Source.newBuilder("test", "12345678", "name").build();
        TagInstrumentationTestRootNode node = parseComplete((b) -> {
            b.beginSource(s);
            b.beginSourceSection(0, 8);

            b.beginRoot();
            b.beginSourceSection(2, 4);
            b.beginTag(ExpressionTag.class);
            b.emitLoadConstant(42);
            b.endTag(ExpressionTag.class);
            b.endSourceSection();
            b.beginTag(ExpressionTag.class);
            b.emitLoadConstant(42);
            b.endTag(ExpressionTag.class);
            b.endRoot();

            b.endSourceSection();
            b.endSource();
        });
        TagTree tree = node.getBytecodeNode().getTagTree();
        assertSourceSection(0, 8, tree.getSourceSection());
        assertSourceSection(2, 4, tree.getTreeChildren().get(0).getSourceSection());
        assertSourceSection(0, 8, tree.getTreeChildren().get(1).getSourceSection());

        SourceSection[] sections = node.getBytecodeNode().getTagTree().getTreeChildren().get(0).getSourceSections();
        assertSourceSection(2, 4, sections[0]);
        assertSourceSection(0, 8, sections[1]);
        assertEquals(2, sections.length);

        sections = node.getBytecodeNode().getTagTree().getTreeChildren().get(1).getSourceSections();
        assertSourceSection(0, 8, sections[0]);
        assertEquals(1, sections.length);

    }

    @Test
    public void testDump() {
        // This test has locals, sources, and tags, which makes it good for code coverage of dumps.
        Source s = Source.newBuilder("test", "12345678", "name").build();
        TagInstrumentationTestRootNode node = parse((b) -> {
            b.beginSource(s);
            b.beginSourceSection(0, 8);

            b.beginRoot();
            b.createLocal();
            b.beginSourceSection(2, 4);
            b.beginTag(ExpressionTag.class);
            b.emitLoadConstant(42);
            b.endTag(ExpressionTag.class);
            b.endSourceSection();
            b.beginTag(ExpressionTag.class);
            b.emitLoadConstant(42);
            b.endTag(ExpressionTag.class);

            // Test printing of array constants.
            b.emitLoadConstant(new Object[]{"Hello", "world"});
            b.emitLoadConstant(new long[]{123L, 456L});
            b.emitLoadConstant(new int[]{123, 456});
            b.emitLoadConstant(new short[]{12, 34});
            b.emitLoadConstant(new char[]{'a', 'b'});
            b.emitLoadConstant(new byte[]{1, 2});
            b.emitLoadConstant(new double[]{3.14d, 12.3d});
            b.emitLoadConstant(new float[]{4.0f, 6.28f});
            b.emitLoadConstant(new boolean[]{true, false});

            b.endRoot();

            b.endSourceSection();
            b.endSource();
        });
        BytecodeNode bytecode = node.getBytecodeNode();
        assertNull(bytecode.getTagTree());
        String[] dumps = new String[]{node.dump(), bytecode.dump(0)};
        for (String dump : dumps) {
            assertTrue(dump.contains("constant(Object[] [Hello, world])"));
            assertTrue(dump.contains("constant(long[] [123, 456])"));
            assertTrue(dump.contains("constant(int[] [123, 456])"));
            assertTrue(dump.contains("constant(short[] [12, 34])"));
            assertTrue(dump.contains("constant(char[] [a, b])"));
            assertTrue(dump.contains("constant(byte[] [1, 2])"));
            assertTrue(dump.contains("constant(double[] [3.14, 12.3])"));
            assertTrue(dump.contains("constant(float[] [4.0, 6.28])"));
            assertTrue(dump.contains("constant(boolean[] [true, false])"));
            assertTrue(dump.contains("locals(1)"));
            // Dump should not have source or tag info.
            assertTrue(dump.contains("exceptionHandlers(0)"));
            assertTrue(dump.contains("sourceInformation(-) = Not Available"));
            assertTrue(dump.contains("tagTree = Not Available"));
        }

        node.getRootNodes().update(BytecodeConfig.COMPLETE);
        bytecode = node.getBytecodeNode();
        TagTree tree = bytecode.getTagTree();
        assertNotNull(tree);
        for (String dump : new String[]{node.dump(), bytecode.dump(tree.getEnterBytecodeIndex())}) {
            // On reparse, source and tag information becomes available. The tags introduce
            // exception handlers too.
            assertTrue(dump.contains("exceptionHandlers(3)"));
            assertTrue(dump.contains("sourceInformation(2)"));
            assertTrue(dump.contains("tagTree(3)"));
        }
    }

    @Test
    public void testNoSourceSections() {
        TagInstrumentationTestRootNode node = parseComplete((b) -> {
            b.beginRoot();
            b.beginTag(ExpressionTag.class);
            b.emitLoadConstant(42);
            b.endTag(ExpressionTag.class);
            b.beginTag(ExpressionTag.class);
            b.emitLoadConstant(42);
            b.endTag(ExpressionTag.class);
            b.endRoot();
        });

        TagTree tree = node.getBytecodeNode().getTagTree();
        assertNull(tree.getSourceSection());
        assertEquals(0, tree.getSourceSections().length);
        assertNull(tree.getTreeChildren().get(0).getSourceSection());
        assertEquals(0, tree.getTreeChildren().get(0).getSourceSections().length);
        assertNull(tree.getTreeChildren().get(1).getSourceSection());
        assertEquals(0, tree.getTreeChildren().get(1).getSourceSections().length);
    }

    private static void assertSourceSection(int startIndex, int length, SourceSection section) {
        assertEquals(startIndex, section.getCharIndex());
        assertEquals(length, section.getCharLength());
    }

    @Test
    public void testImplicitJump() {
        TagInstrumentationTestRootNode node = parse((b) -> {
            b.beginRoot();

            var l = b.createLabel();

            b.beginTag(ExpressionTag.class);
            b.emitBranch(l);
            b.endTag(ExpressionTag.class);

            b.emitLabel(l);

            b.beginReturn();
            b.emitLoadConstant(42);
            b.endReturn();

            b.endRoot();
        });

        assertInstructions(node,
                        "branch",
                        "load.constant",
                        "return");

        assertEquals(42, node.getCallTarget().call());

        List<Event> events = attachEventListener(SourceSectionFilter.newBuilder().tagIs(StandardTags.RootTag.class,
                        StandardTags.ExpressionTag.class).build());

        assertInstructions(node,
                        "tag.enter",
                        "tag.enter",
                        "tag.leaveVoid",
                        "branch",
                        "tag.leaveVoid",
                        "load.constant",
                        "tag.leave",
                        "return",
                        "tag.leave",
                        "return");

        assertEquals(42, node.getCallTarget().call());

        assertEvents(node,
                        events,
                        new Event(EventKind.ENTER, 0x0000, 0x0030, null, RootTag.class),
                        new Event(EventKind.ENTER, 0x0006, 0x0018, null, ExpressionTag.class),
                        new Event(EventKind.RETURN_VALUE, 0x0006, 0x0018, null, ExpressionTag.class),
                        new Event(EventKind.RETURN_VALUE, 0x0000, 0x0030, 42, RootTag.class));
    }

    @Test
    public void testNestedRoot() {
        TagInstrumentationTestRootNode node = parse((b) -> {
            b.beginRoot();

            b.beginRoot();
            b.emitNop(); // pad bytecode to differentiate inner bci's
            b.beginTag(StatementTag.class);
            b.beginReturn();
            b.beginTag(ExpressionTag.class);
            b.emitLoadConstant(42L);
            b.endTag(ExpressionTag.class);
            b.endReturn();
            b.endTag(StatementTag.class);
            TagInstrumentationTestRootNode inner = b.endRoot();

            b.beginTag(StatementTag.class);
            b.beginReturn();
            b.beginTag(ExpressionTag.class);
            b.emitInvokeRootNode(inner);
            b.endTag(ExpressionTag.class);
            b.endReturn();
            b.endTag(StatementTag.class);

            b.endRoot();
        });

        assertInstructions(node,
                        "c.InvokeRootNode",
                        "return");
        assertEquals(42L, node.getCallTarget().call());

        // First, just statements.
        List<Event> events = attachEventListener(SourceSectionFilter.newBuilder().tagIs(StatementTag.class).build());

        assertInstructions(node,
                        "tag.enter",
                        "c.InvokeRootNode",
                        "tag.leave",
                        "return",
                        "tag.leaveVoid",
                        "load.null",
                        "return");
        assertEquals(42L, node.getCallTarget().call());
        assertEvents(node,
                        events,
                        new Event(EventKind.ENTER, 0x0000, 0x0018, null, StatementTag.class),
                        new Event(EventKind.ENTER, 0x0002, 0x001a, null, StatementTag.class),
                        new Event(EventKind.RETURN_VALUE, 0x0002, 0x001a, 42L, StatementTag.class),
                        new Event(EventKind.RETURN_VALUE, 0x0000, 0x0018, 42L, StatementTag.class));

        // Now, add expressions.
        events = attachEventListener(SourceSectionFilter.newBuilder().tagIs(ExpressionTag.class, StatementTag.class).build());
        assertInstructions(node,
                        "tag.enter",
                        "tag.enter",
                        "c.InvokeRootNode",
                        "tag.leave",
                        "tag.leave",
                        "return",
                        "tag.leaveVoid",
                        "load.null",
                        "return");
        assertEquals(42L, node.getCallTarget().call());
        assertEvents(node,
                        events,
                        new Event(EventKind.ENTER, 0x0000, 0x0028, null, StatementTag.class),
                        new Event(EventKind.ENTER, 0x0006, 0x0012, null, ExpressionTag.class),
                        new Event(EventKind.ENTER, 0x0002, 0x002a, null, StatementTag.class),
                        new Event(EventKind.ENTER, 0x0008, 0x0014, null, ExpressionTag.class),
                        new Event(EventKind.RETURN_VALUE, 0x0008, 0x0014, 42L, ExpressionTag.class),
                        new Event(EventKind.RETURN_VALUE, 0x0002, 0x002a, 42L, StatementTag.class),
                        new Event(EventKind.RETURN_VALUE, 0x0006, 0x0012, 42L, ExpressionTag.class),
                        new Event(EventKind.RETURN_VALUE, 0x0000, 0x0028, 42L, StatementTag.class));
    }

    @Test
    public void testNestedRootDifferentTags() {
        TagInstrumentationTestRootNode node = parse((b) -> {
            b.beginRoot();

            b.beginRoot();
            b.beginReturn();
            b.beginTag(ExpressionTag.class);
            b.emitLoadConstant(42L);
            b.endTag(ExpressionTag.class);
            b.endReturn();
            TagInstrumentationTestRootNode inner = b.endRoot();

            b.beginTag(StatementTag.class);
            b.beginReturn();
            b.emitInvokeRootNode(inner);
            b.endReturn();
            b.endTag(StatementTag.class);

            b.endRoot();
        });

        assertInstructions(node,
                        "c.InvokeRootNode",
                        "return");
        assertEquals(42L, node.getCallTarget().call());

        // First, just expressions.
        List<Event> events = attachEventListener(SourceSectionFilter.newBuilder().tagIs(ExpressionTag.class).build());

        assertInstructions(node,
                        "c.InvokeRootNode",
                        "return");
        assertEquals(42L, node.getCallTarget().call());
        assertEvents(node,
                        events,
                        new Event(EventKind.ENTER, 0x0000, 0x000c, null, ExpressionTag.class),
                        new Event(EventKind.RETURN_VALUE, 0x0000, 0x000c, 42L, ExpressionTag.class));

        // Now, add statements.
        events = attachEventListener(SourceSectionFilter.newBuilder().tagIs(ExpressionTag.class, StatementTag.class).build());
        assertInstructions(node,
                        "tag.enter",
                        "c.InvokeRootNode",
                        "tag.leave",
                        "return",
                        "tag.leaveVoid",
                        "load.null",
                        "return");
        assertEquals(42L, node.getCallTarget().call());
        assertEvents(node,
                        events,
                        new Event(EventKind.ENTER, 0x0000, 0x0018, null, StatementTag.class),
                        new Event(EventKind.ENTER, 0x0000, 0x000c, null, ExpressionTag.class),
                        new Event(EventKind.RETURN_VALUE, 0x0000, 0x000c, 42L, ExpressionTag.class),
                        new Event(EventKind.RETURN_VALUE, 0x0000, 0x0018, 42L, StatementTag.class));
    }

    @Test
    public void testTryFinally() {
        TagInstrumentationTestRootNode node = parse((b) -> {
            b.beginRoot();
            var l = b.createLabel();

            b.beginTag(StatementTag.class);
            b.beginTryFinally(() -> {
                b.beginTag(StatementTag.class);
                b.emitBranch(l);
                b.endTag(StatementTag.class);
            });

            b.beginTag(ExpressionTag.class);
            b.beginReturn();
            b.beginValueOrThrow();
            b.emitLoadConstant(42L);
            b.emitLoadArgument(0);
            b.endValueOrThrow();
            b.endReturn();
            b.endTag(ExpressionTag.class);

            b.endTryFinally();
            b.endTag(StatementTag.class);

            b.emitLabel(l);
            b.emitLoadConstant(123L);

            b.endRoot();
        });

        assertInstructions(node,
                        "load.constant",
                        "load.argument",
                        "c.ValueOrThrow",
                        "pop", // inline finally handler
                        "branch",
                        "pop", // exception handler
                        "branch",
                        "load.constant",
                        "return");

        assertEquals(123L, node.getCallTarget().call(false));
        assertEquals(123L, node.getCallTarget().call(true));

        List<Event> events = attachEventListener(SourceSectionFilter.newBuilder().tagIs(ExpressionTag.class, StatementTag.class).build());

        assertEquals(123L, node.getCallTarget().call(false));
        assertEvents(node,
                        events,
                        new Event(EventKind.ENTER, 0x0000, 0x00a6, null, StatementTag.class),
                        new Event(EventKind.ENTER, 0x0006, 0x0056, null, ExpressionTag.class),
                        new Event(EventKind.RETURN_VALUE, 0x0006, 0x0056, 42L, ExpressionTag.class),
                        new Event(EventKind.ENTER, 0x0026, 0x0044, null, StatementTag.class),
                        new Event(EventKind.RETURN_VALUE, 0x0026, 0x0044, null, StatementTag.class),
                        new Event(EventKind.RETURN_VALUE, 0x0000, 0x00a6, null, StatementTag.class));

        events.clear();
        assertEquals(123L, node.getCallTarget().call(true));
        assertEvents(node,
                        events,
                        new Event(EventKind.ENTER, 0x0000, 0x00a6, null, StatementTag.class),
                        new Event(EventKind.ENTER, 0x0006, 0x0056, null, ExpressionTag.class),
                        new Event(EventKind.EXCEPTIONAL, 0x0006, 0x0056, TestException.class, ExpressionTag.class),
                        new Event(EventKind.ENTER, 0x0080, 0x009e, null, StatementTag.class),
                        new Event(EventKind.RETURN_VALUE, 0x0080, 0x009e, null, StatementTag.class),
                        new Event(EventKind.RETURN_VALUE, 0x0000, 0x00a6, null, StatementTag.class));
    }

    @Test
    public void testYield() {
        TagInstrumentationTestRootNode node = parse((b) -> {
            b.beginRoot();

            b.beginReturn();
            b.beginTag(ExpressionTag.class);
            b.beginYield();
            b.emitLoadConstant(42);
            b.endYield();
            b.endTag(ExpressionTag.class);
            b.endReturn();

            b.endRoot();
        });
        assertInstructions(node,
                        "load.constant",
                        "yield",
                        "return");

        ContinuationResult cont = (ContinuationResult) node.getCallTarget().call();
        assertEquals(42, cont.getResult());

        // Instrument while continuation is suspended. It should resume right after the yield.
        List<Event> events = attachEventListener(SourceSectionFilter.newBuilder().tagIs(StandardTags.ExpressionTag.class).build());
        assertInstructions(node,
                        "tag.enter",
                        "load.constant",
                        "tag.yield",
                        "yield",
                        "tag.resume",
                        "tag.leave",
                        "return");

        List<Instruction> instructions = node.getBytecodeNode().getInstructionsAsList();
        int enter = instructions.get(0).getBytecodeIndex();
        int leave = instructions.get(5).getBytecodeIndex();

        assertEquals(123, cont.continueWith(123));
        assertEvents(node,
                        events,
                        new Event(EventKind.RESUME, enter, leave, null, ExpressionTag.class),
                        new Event(EventKind.RETURN_VALUE, enter, leave, 123, ExpressionTag.class));

        events.clear();

        // Now, both events should fire.
        cont = (ContinuationResult) node.getCallTarget().call();
        assertEquals(42, cont.getResult());
        assertEquals(321, cont.continueWith(321));
        assertEvents(node,
                        events,
                        new Event(EventKind.ENTER, enter, leave, null, ExpressionTag.class),
                        new Event(EventKind.YIELD, enter, leave, 42, ExpressionTag.class),
                        new Event(EventKind.RESUME, enter, leave, null, ExpressionTag.class),
                        new Event(EventKind.RETURN_VALUE, enter, leave, 321, ExpressionTag.class));

        // Add a second instrumentation while the continuation is suspended. It should resume at the
        // proper location.
        cont = (ContinuationResult) node.getCallTarget().call();
        assertEquals(42, cont.getResult());

        events = attachEventListener(SourceSectionFilter.newBuilder().tagIs(StandardTags.RootBodyTag.class, StandardTags.ExpressionTag.class).build());
        assertInstructions(node,
                        "tag.enter",
                        "tag.enter",
                        "load.constant",
                        "tag.yield",
                        "tag.yield",
                        "yield",
                        "tag.resume",
                        "tag.resume",
                        "tag.leave",
                        "tag.leave",
                        "return",
                        "tag.leave",
                        "return");

        instructions = node.getBytecodeNode().getInstructionsAsList();
        int enter1 = instructions.get(0).getBytecodeIndex();
        int leave1 = instructions.get(11).getBytecodeIndex();
        int enter2 = instructions.get(1).getBytecodeIndex();
        int leave2 = instructions.get(8).getBytecodeIndex();

        assertEquals(456, cont.continueWith(456));
        assertEvents(node,
                        events,
                        new Event(EventKind.RESUME, enter1, leave1, null, RootBodyTag.class),
                        new Event(EventKind.RESUME, enter2, leave2, null, ExpressionTag.class),
                        new Event(EventKind.RETURN_VALUE, enter2, leave2, 456, ExpressionTag.class),
                        new Event(EventKind.RETURN_VALUE, enter1, leave1, 456, RootBodyTag.class));
        events.clear();

        // Now, four events should fire.
        cont = (ContinuationResult) node.getCallTarget().call();
        assertEquals(42, cont.getResult());
        assertEquals(333, cont.continueWith(333));
        assertEvents(node,
                        events,
                        new Event(EventKind.ENTER, enter1, leave1, null, RootBodyTag.class),
                        new Event(EventKind.ENTER, enter2, leave2, null, ExpressionTag.class),
                        new Event(EventKind.YIELD, enter2, leave2, 42, ExpressionTag.class),
                        new Event(EventKind.YIELD, enter1, leave1, 42, RootBodyTag.class),
                        new Event(EventKind.RESUME, enter1, leave1, null, RootBodyTag.class),
                        new Event(EventKind.RESUME, enter2, leave2, null, ExpressionTag.class),
                        new Event(EventKind.RETURN_VALUE, enter2, leave2, 333, ExpressionTag.class),
                        new Event(EventKind.RETURN_VALUE, enter1, leave1, 333, RootBodyTag.class));
    }

    @Test
    public void testTryFinallyYieldInTry() {
        TagInstrumentationTestRootNode node = parse((b) -> {
            b.beginRoot();

            b.beginTag(StatementTag.class);
            b.beginTryFinally(() -> {
                b.beginTag(StatementTag.class);
                b.emitLoadConstant(123L);
                b.endTag(StatementTag.class);
            });

            b.beginTag(ExpressionTag.class);
            b.beginReturn();
            b.beginValueOrThrow();
            b.beginYield();
            b.emitLoadConstant(42L);
            b.endYield();
            b.emitLoadArgument(0);
            b.endValueOrThrow();
            b.endReturn();
            b.endTag(ExpressionTag.class);

            b.endTryFinally();
            b.endTag(StatementTag.class);

            b.endRoot();
        });

        assertInstructions(node,
                        "load.constant",
                        "yield",
                        "load.argument",
                        "c.ValueOrThrow",
                        "load.constant", // inline finally handler
                        "pop",
                        "return",
                        "load.constant", // exception handler
                        "pop",
                        "throw");

        ContinuationResult cont;
        cont = (ContinuationResult) node.getCallTarget().call(false);
        assertEquals(42L, cont.getResult());
        assertEquals(456L, cont.continueWith(456L));

        cont = (ContinuationResult) node.getCallTarget().call(true);
        assertEquals(42L, cont.getResult());
        try {
            cont.continueWith(456L);
            fail("exception expected");
        } catch (TestException ex) {
            // pass
        }

        List<Event> events = attachEventListener(SourceSectionFilter.newBuilder().tagIs(ExpressionTag.class,
                        StatementTag.class).build());

        cont = (ContinuationResult) node.getCallTarget().call(false);
        assertEvents(node,
                        events,
                        new Event(EventKind.ENTER, 0x0000, 0x00b2, null, StatementTag.class),
                        new Event(EventKind.ENTER, 0x0006, 0x006c, null, ExpressionTag.class),
                        new Event(EventKind.YIELD, 0x0006, 0x006c, 42L, ExpressionTag.class),
                        new Event(EventKind.YIELD, 0x0000, 0x00b2, 42L, StatementTag.class));
        assertEquals(42L, cont.getResult());
        events.clear();
        assertEquals(456L, cont.continueWith(456L));
        assertEvents(node,
                        events,
                        new Event(EventKind.RESUME, 0x0000, 0x00b2, null, StatementTag.class),
                        new Event(EventKind.RESUME, 0x0006, 0x006c, null, ExpressionTag.class),
                        new Event(EventKind.RETURN_VALUE, 0x0006, 0x006c, 456L, ExpressionTag.class),
                        new Event(EventKind.ENTER, 0x0044, 0x0050, null, StatementTag.class),
                        new Event(EventKind.RETURN_VALUE, 0x0044, 0x0050, 123L, StatementTag.class),
                        new Event(EventKind.RETURN_VALUE, 0x0000, 0x00b2, 456L, StatementTag.class));

        events.clear();

        cont = (ContinuationResult) node.getCallTarget().call(true);
        assertEvents(node,
                        events,
                        new Event(EventKind.ENTER, 0x0000, 0x00b2, null, StatementTag.class),
                        new Event(EventKind.ENTER, 0x0006, 0x006c, null, ExpressionTag.class),
                        new Event(EventKind.YIELD, 0x0006, 0x006c, 42L, ExpressionTag.class),
                        new Event(EventKind.YIELD, 0x0000, 0x00b2, 42L, StatementTag.class));
        assertEquals(42L, cont.getResult());
        events.clear();
        try {
            cont.continueWith(456L);
            fail("exception expected");
        } catch (TestException ex) {
            // pass
        }
        assertEvents(node,
                        events,
                        new Event(EventKind.RESUME, 0x0000, 0x00b2, null, StatementTag.class),
                        new Event(EventKind.RESUME, 0x0006, 0x006c, null, ExpressionTag.class),
                        new Event(EventKind.EXCEPTIONAL, 0x0006, 0x006c, TestException.class, ExpressionTag.class),
                        new Event(EventKind.ENTER, 0x0094, 0x00a0, null, StatementTag.class),
                        new Event(EventKind.RETURN_VALUE, 0x0094, 0x00a0, 123L, StatementTag.class),
                        new Event(EventKind.EXCEPTIONAL, 0x0000, 0x00b2, TestException.class, StatementTag.class));
    }

    @Test
    public void testTryFinallyYieldInFinally() {
        TagInstrumentationTestRootNode node = parse((b) -> {
            b.beginRoot();

            b.beginTag(StatementTag.class);
            b.beginTryFinally(() -> {
                b.beginTag(StatementTag.class);
                b.beginYield();
                b.emitLoadConstant(123L);
                b.endYield();
                b.endTag(StatementTag.class);
            });

            b.beginTag(ExpressionTag.class);
            b.beginReturn();
            b.beginValueOrThrow();
            b.emitLoadConstant(42L);
            b.emitLoadArgument(0);
            b.endValueOrThrow();
            b.endReturn();
            b.endTag(ExpressionTag.class);

            b.endTryFinally();
            b.endTag(StatementTag.class);

            b.endRoot();
        });

        assertInstructions(node,
                        "load.constant",
                        "load.argument",
                        "c.ValueOrThrow",
                        "load.constant", // inline finally handler
                        "yield",
                        "pop",
                        "return",
                        "load.constant", // exception handler
                        "yield",
                        "pop",
                        "throw");

        ContinuationResult cont;
        cont = (ContinuationResult) node.getCallTarget().call(false);
        assertEquals(123L, cont.getResult());
        assertEquals(42L, cont.continueWith(456L));

        cont = (ContinuationResult) node.getCallTarget().call(true);
        assertEquals(123L, cont.getResult());
        try {
            cont.continueWith(456L);
            fail("exception expected");
        } catch (TestException ex) {
            // pass
        }

        List<Event> events = attachEventListener(SourceSectionFilter.newBuilder().tagIs(ExpressionTag.class,
                        StatementTag.class).build());

        cont = (ContinuationResult) node.getCallTarget().call(false);
        assertEvents(node,
                        events,
                        new Event(EventKind.ENTER, 0x0000, 0x00ee, null, StatementTag.class),
                        new Event(EventKind.ENTER, 0x0006, 0x006c, null, ExpressionTag.class),
                        new Event(EventKind.RETURN_VALUE, 0x0006, 0x006c, 42L, ExpressionTag.class),
                        new Event(EventKind.ENTER, 0x0026, 0x0050, null, StatementTag.class),
                        new Event(EventKind.YIELD, 0x0026, 0x0050, 123L, StatementTag.class),
                        new Event(EventKind.YIELD, 0x0000, 0x00ee, 123L, StatementTag.class));
        assertEquals(123L, cont.getResult());
        events.clear();
        assertEquals(42L, cont.continueWith(456L));
        assertEvents(node,
                        events,
                        new Event(EventKind.RESUME, 0x0000, 0x00ee, null, StatementTag.class),
                        new Event(EventKind.RESUME, 0x0026, 0x0050, null, StatementTag.class),
                        new Event(EventKind.RETURN_VALUE, 0x0026, 0x0050, 456L, StatementTag.class),
                        new Event(EventKind.RETURN_VALUE, 0x0000, 0x00ee, 42L, StatementTag.class));

        events.clear();

        cont = (ContinuationResult) node.getCallTarget().call(true);
        assertEvents(node,
                        events,
                        new Event(EventKind.ENTER, 0x0000, 0x00ee, null, StatementTag.class),
                        new Event(EventKind.ENTER, 0x0006, 0x006c, null, ExpressionTag.class),
                        new Event(EventKind.EXCEPTIONAL, 0x0006, 0x006c, TestException.class, ExpressionTag.class),
                        new Event(EventKind.ENTER, 0x00b2, 0x00dc, null, StatementTag.class),
                        new Event(EventKind.YIELD, 0x00b2, 0x00dc, 123L, StatementTag.class),
                        new Event(EventKind.YIELD, 0x0000, 0x00ee, 123L, StatementTag.class));
        assertEquals(123L, cont.getResult());
        events.clear();
        try {
            cont.continueWith(456L);
            fail("exception expected");
        } catch (TestException ex) {
            // pass
        }
        assertEvents(node,
                        events,
                        new Event(EventKind.RESUME, 0x0000, 0x00ee, null, StatementTag.class),
                        new Event(EventKind.RESUME, 0x00b2, 0x00dc, null, StatementTag.class),
                        new Event(EventKind.RETURN_VALUE, 0x00b2, 0x00dc, 456L, StatementTag.class),
                        new Event(EventKind.EXCEPTIONAL, 0x0000, 0x00ee, TestException.class, StatementTag.class));
    }

    @Test
    public void testYieldWithNestedRoots() {
        TagInstrumentationTestRootNode node = parse((b) -> {
            b.beginRoot();
            b.beginTag(StatementTag.class);

            b.beginRoot();
            b.beginTag(ExpressionTag.class);
            b.beginYield();
            b.emitLoadConstant(42L);
            b.endYield();
            b.endTag(ExpressionTag.class);
            TagInstrumentationTestRootNode inner = b.endRoot();

            b.beginReturn();
            b.beginResume(123L);
            b.emitInvokeRootNode(inner);
            b.endResume();
            b.endReturn();

            b.endTag(StatementTag.class);
            b.endRoot();
        });

        assertInstructions(node,
                        "c.InvokeRootNode",
                        "c.Resume",
                        "return");
        assertEquals(123L, node.getCallTarget().call());

        // First, just enable expression tags.
        List<Event> events = attachEventListener(SourceSectionFilter.newBuilder().tagIs(ExpressionTag.class).build());

        assertInstructions(node,
                        "c.InvokeRootNode",
                        "c.Resume",
                        "return");
        assertEquals(123L, node.getCallTarget().call());
        assertEvents(node, events,
                        new Event(EventKind.ENTER, 0x0000, 0x01e, null, ExpressionTag.class),
                        new Event(EventKind.YIELD, 0x0000, 0x01e, 42L, ExpressionTag.class),
                        new Event(EventKind.RESUME, 0x0000, 0x01e, null, ExpressionTag.class),
                        new Event(EventKind.RETURN_VALUE, 0x0000, 0x01e, 123L, ExpressionTag.class));
        events.clear();

        // Now, enable statement tags too.
        events = attachEventListener(SourceSectionFilter.newBuilder().tagIs(ExpressionTag.class, StatementTag.class).build());

        assertInstructions(node,
                        "tag.enter",
                        "c.InvokeRootNode",
                        "c.Resume",
                        "tag.leave",
                        "return",
                        "tag.leaveVoid",
                        "load.null",
                        "return");
        assertEquals(123L, node.getCallTarget().call());
        assertEvents(node, events,
                        new Event(EventKind.ENTER, 0x0000, 0x01e, null, StatementTag.class),
                        new Event(EventKind.ENTER, 0x0000, 0x01e, null, ExpressionTag.class),
                        new Event(EventKind.YIELD, 0x0000, 0x01e, 42L, ExpressionTag.class),
                        new Event(EventKind.RESUME, 0x0000, 0x01e, null, ExpressionTag.class),
                        new Event(EventKind.RETURN_VALUE, 0x0000, 0x01e, 123L, ExpressionTag.class),
                        new Event(EventKind.RETURN_VALUE, 0x0000, 0x01e, 123L, StatementTag.class));

    }

    @Test
    public void testNodeLibrary() {
        TagInstrumentationTestRootNode node = parse((b) -> {
            b.beginRoot();

            b.beginTag(StatementTag.class);
            BytecodeLocal l1 = b.createLocal("l1", "l1_info");
            b.beginStoreLocal(l1);
            b.beginTag(ExpressionTag.class);
            b.emitLoadConstant(42);
            b.endTag(ExpressionTag.class);
            b.endStoreLocal();
            b.endTag(StatementTag.class);

            b.beginBlock();

            b.beginTag(StatementTag.class);
            BytecodeLocal l2 = b.createLocal("l2", "l2_info");
            b.beginStoreLocal(l2);
            b.beginTag(ExpressionTag.class);
            b.emitLoadLocal(l1);
            b.endTag(ExpressionTag.class);
            b.endStoreLocal();
            b.endTag(StatementTag.class);

            b.endBlock();

            b.beginTag(StatementTag.class);
            BytecodeLocal l3 = b.createLocal(
                            TruffleString.fromJavaStringUncached("l3", Encoding.UTF_16), "l3_info");
            b.beginStoreLocal(l3);
            b.beginTag(ExpressionTag.class);
            b.emitLoadConstant(41);
            b.endTag(ExpressionTag.class);
            b.endStoreLocal();
            b.endTag(StatementTag.class);

            b.beginReturn();
            b.emitLoadLocal(l1);
            b.endReturn();

            b.endRoot();
        });

        List<List<ExpectedLocal>> onEnterLocalsExpression = List.of(
                        List.of(new ExpectedLocal("l1", null)),
                        List.of(new ExpectedLocal("l1", 42),
                                        new ExpectedLocal("l2", null)),
                        List.of(new ExpectedLocal("l1", 42),
                                        new ExpectedLocal("l3", null)));

        List<List<ExpectedLocal>> onReturnLocalsExpression = List.of(
                        List.of(new ExpectedLocal("l1", null)),
                        List.of(new ExpectedLocal("l1", 42),
                                        new ExpectedLocal("l2", null)),
                        List.of(new ExpectedLocal("l1", 42),
                                        new ExpectedLocal("l3", null)));
        assertLocals(SourceSectionFilter.newBuilder().tagIs(StandardTags.ExpressionTag.class).build(),
                        onEnterLocalsExpression,
                        onReturnLocalsExpression);

        List<List<ExpectedLocal>> onEnterLocalsStatement = List.of(
                        List.of(),
                        List.of(new ExpectedLocal("l1", 42)),
                        List.of(new ExpectedLocal("l1", 42)));

        List<List<ExpectedLocal>> onReturnLocalsStatement = List.of(
                        List.of(new ExpectedLocal("l1", 42)),
                        List.of(new ExpectedLocal("l1", 42),
                                        new ExpectedLocal("l2", 42)),
                        List.of(new ExpectedLocal("l1", 42),
                                        new ExpectedLocal("l3", 41)));

        assertLocals(SourceSectionFilter.newBuilder().tagIs(StandardTags.StatementTag.class).build(),
                        onEnterLocalsStatement,
                        onReturnLocalsStatement);

        node.getCallTarget().call();
    }

    private void assertLocals(SourceSectionFilter filter, List<List<ExpectedLocal>> onEnterLocals, List<List<ExpectedLocal>> onLeaveLocals) {
        AtomicInteger enterIndex = new AtomicInteger(0);
        AtomicInteger returnIndex = new AtomicInteger(0);
        instrumenter.attachExecutionEventFactory(filter, (c) -> {
            return new ExecutionEventNode() {

                @Child NodeLibrary nodeLibrary = insert(NodeLibrary.getFactory().create(c.getInstrumentedNode()));
                @Child InteropLibrary scopeLibrary = insert(InteropLibrary.getFactory().createDispatched(1));
                @Child InteropLibrary membersLibrary = insert(InteropLibrary.getFactory().createDispatched(1));
                @Child InteropLibrary memberLibrary = insert(InteropLibrary.getFactory().createDispatched(1));
                @Child InteropLibrary valueLibrary = insert(InteropLibrary.getFactory().createDispatched(1));

                @Override
                protected void onEnter(VirtualFrame frame) {
                    int index = enterIndex.getAndIncrement();
                    assertScope(frame.materialize(), index, true, onEnterLocals.get(index));
                }

                @Override
                protected void onReturnValue(VirtualFrame frame, Object result) {
                    int index = returnIndex.getAndIncrement();
                    assertScope(frame.materialize(), index, false, onLeaveLocals.get(index));
                }

                @TruffleBoundary
                private void assertScope(Frame frame, int index, boolean enter, List<ExpectedLocal> locals) {
                    try {
                        Node instrumentedScope = c.getInstrumentedNode();
                        Object scope = nodeLibrary.getScope(instrumentedScope, frame, enter);
                        Object members = scopeLibrary.getMembers(scope);
                        assertEquals(locals.size(), membersLibrary.getArraySize(members));

                        for (int i = 0; i < locals.size(); i++) {
                            ExpectedLocal expectedLocal = locals.get(i);

                            Object actualName = membersLibrary.readArrayElement(members, i);
                            assertEquals(expectedLocal.name(), memberLibrary.asString(actualName));
                            Object actualValue = scopeLibrary.readMember(scope, memberLibrary.asString(actualName));
                            if (expectedLocal.value() == null) {
                                assertTrue(valueLibrary.isNull(actualValue));
                            } else {
                                assertEquals(expectedLocal.value(), actualValue);
                            }
                        }
                    } catch (Throwable e) {
                        throw CompilerDirectives.shouldNotReachHere("Failed index " + index + " " + (enter ? "enter" : "return"), e);
                    }
                }
            };
        });
    }

    record ExpectedLocal(String name, Object value) {
    }

    @Test
    public void testOnStackTestInTagInstrumentationEnter1() {
        AtomicReference<List<Event>> events0 = new AtomicReference<>();
        triggerOnTag(StatementTag.class, () -> {
            events0.set(attachEventListener(SourceSectionFilter.newBuilder().tagIs(RootTag.class, StatementTag.class, ExpressionTag.class).build()));
        });
        TagInstrumentationTestRootNode node = parse((b) -> {
            b.beginRoot();

            b.beginTag(StatementTag.class);
            b.beginTag(ExpressionTag.class); // event collection is expected to begin here
            b.emitLoadConstant(42);
            b.endTag(ExpressionTag.class);
            b.endTag(StatementTag.class);

            b.endRoot();
        });

        node.getCallTarget().call();

        List<Instruction> instructions = node.getBytecodeNode().getInstructionsAsList();
        int expressionBegin = instructions.get(2).getBytecodeIndex();
        int expressionEnd = instructions.get(4).getBytecodeIndex();
        int statementBegin = instructions.get(1).getBytecodeIndex();
        int statementEnd = instructions.get(5).getBytecodeIndex();
        int rootBegin = instructions.get(0).getBytecodeIndex();
        int rootEnd = instructions.get(6).getBytecodeIndex();

        // make sure the first StatementTag is skipped
        assertEvents(node, events0.get(),
                        new Event(EventKind.ENTER, expressionBegin, expressionEnd, null, ExpressionTag.class),
                        new Event(EventKind.RETURN_VALUE, expressionBegin, expressionEnd, 42, ExpressionTag.class),
                        new Event(EventKind.RETURN_VALUE, statementBegin, statementEnd, 42, StatementTag.class),
                        new Event(EventKind.RETURN_VALUE, rootBegin, rootEnd, 42, RootTag.class));

    }

    @Test
    public void testOnStackTestInTagInstrumentationEnter2() {
        AtomicReference<List<Event>> events0 = new AtomicReference<>();
        triggerOnTag(StatementTag.class, () -> {
            events0.set(attachEventListener(SourceSectionFilter.newBuilder().tagIs(RootTag.class, StatementTag.class, ExpressionTag.class).build()));
        });
        TagInstrumentationTestRootNode node = parse((b) -> {
            b.beginRoot();

            b.beginTag(StatementTag.class);
            b.beginTag(StatementTag.class); // event collection is expected to begin here
            b.beginTag(ExpressionTag.class);
            b.beginTag(StatementTag.class);
            b.emitLoadConstant(42);
            b.endTag(StatementTag.class);
            b.endTag(ExpressionTag.class);
            b.endTag(StatementTag.class);
            b.endTag(StatementTag.class);

            b.endRoot();
        });

        assertEquals(42, node.getCallTarget().call());

        List<Instruction> instructions = node.getBytecodeNode().getInstructionsAsList();
        int expressionBegin = instructions.get(3).getBytecodeIndex();
        int expressionEnd = instructions.get(7).getBytecodeIndex();
        int statement0Begin = instructions.get(1).getBytecodeIndex();
        int statement0End = instructions.get(9).getBytecodeIndex();
        int statement1Begin = instructions.get(2).getBytecodeIndex();
        int statement1End = instructions.get(8).getBytecodeIndex();
        int statement2Begin = instructions.get(4).getBytecodeIndex();
        int statement2End = instructions.get(6).getBytecodeIndex();
        int rootBegin = instructions.get(0).getBytecodeIndex();
        int rootEnd = instructions.get(10).getBytecodeIndex();

        // make sure the first StatementTag is skipped
        assertEvents(node, events0.get(),
                        new Event(EventKind.ENTER, statement1Begin, statement1End, null, StatementTag.class),
                        new Event(EventKind.ENTER, expressionBegin, expressionEnd, null, ExpressionTag.class),
                        new Event(EventKind.ENTER, statement2Begin, statement2End, null, StatementTag.class),
                        new Event(EventKind.RETURN_VALUE, statement2Begin, statement2End, 42, StatementTag.class),
                        new Event(EventKind.RETURN_VALUE, expressionBegin, expressionEnd, 42, ExpressionTag.class),
                        new Event(EventKind.RETURN_VALUE, statement1Begin, statement1End, 42, StatementTag.class),
                        new Event(EventKind.RETURN_VALUE, statement0Begin, statement0End, 42, StatementTag.class),
                        new Event(EventKind.RETURN_VALUE, rootBegin, rootEnd, 42, RootTag.class));

    }

    private void triggerOnTag(Class<? extends Tag> tag, Runnable r) {
        instrumenter.attachExecutionEventListener(SourceSectionFilter.newBuilder().tagIs(tag).build(),
                        new ExecutionEventListener() {
                            private Runnable run = r;

                            public void onEnter(EventContext c, VirtualFrame frame) {
                                boundary();
                            }

                            @TruffleBoundary
                            private void boundary() {
                                if (run != null) {
                                    run.run();
                                }
                                run = null;
                            }

                            public void onReturnValue(EventContext c, VirtualFrame frame, Object result) {
                            }

                            public void onReturnExceptional(EventContext c, VirtualFrame frame, Throwable exception) {
                            }
                        });
    }

    @Test
    public void testOnStackTestInOperation() {
        AtomicReference<List<Event>> events0 = new AtomicReference<>();
        AtomicReference<List<Event>> events1 = new AtomicReference<>();
        AtomicReference<List<Event>> events2 = new AtomicReference<>();

        TagInstrumentationTestRootNode node = parse((b) -> {
            b.beginRoot();

            b.beginTag(StatementTag.class);
            b.beginTag(ExpressionTag.class);
            b.emitInvokeRunnable(() -> {
                events0.set(attachEventListener(SourceSectionFilter.newBuilder().tagIs(RootTag.class).build()));
            });
            b.endTag(ExpressionTag.class);
            b.endTag(StatementTag.class);

            b.beginTag(StatementTag.class);
            b.beginTag(ExpressionTag.class);
            b.emitInvokeRunnable(() -> {
                events1.set(attachEventListener(SourceSectionFilter.newBuilder().tagIs(RootTag.class, ExpressionTag.class).build()));
            });
            b.endTag(ExpressionTag.class);
            b.endTag(StatementTag.class);

            b.beginTag(StatementTag.class);
            b.beginTag(ExpressionTag.class);
            b.emitInvokeRunnable(() -> {
                events2.set(attachEventListener(SourceSectionFilter.newBuilder().tagIs(RootTag.class,
                                StatementTag.class, ExpressionTag.class).build()));
            });
            b.endTag(ExpressionTag.class);
            b.endTag(StatementTag.class);

            b.endRoot();
        });

        assertNull(node.getCallTarget().call());

        List<Instruction> instructions = node.getBytecodeNode().getInstructionsAsList();
        int enterRoot1 = instructions.get(0).getBytecodeIndex();
        int leaveRoot1 = instructions.get(17).getBytecodeIndex();

        assertEvents(node, events0.get(),
                        new Event(EventKind.RETURN_VALUE, enterRoot1, leaveRoot1, null, RootTag.class));

        assertEvents(node, events1.get(),
                        new Event(EventKind.RETURN_VALUE, 0x18, 0x24, null, ExpressionTag.class),
                        new Event(EventKind.ENTER, 0x2a, 0x36, null, ExpressionTag.class),
                        new Event(EventKind.RETURN_VALUE, 0x48, 0x54, null, ExpressionTag.class),
                        new Event(EventKind.RETURN_VALUE, enterRoot1, leaveRoot1, null, RootTag.class));

        assertEvents(node, events2.get(),
                        new Event(EventKind.RETURN_VALUE, 0x48, 0x54, null, ExpressionTag.class),
                        new Event(EventKind.RETURN_VALUE, 0x42, 0x5a, null, StatementTag.class),
                        new Event(EventKind.RETURN_VALUE, enterRoot1, leaveRoot1, null, RootTag.class));
    }

    /**
     * When reparsing with tags, an endTag instruction can make a previously-unreachable path
     * reachable. The following reachability tests are regression tests that ensure the frame and
     * constant pool layout do not change between parses.
     */
    @Test
    public void testReachabilityTryFinally() {
        TagInstrumentationTestRootNode node = parse((b) -> {
            b.beginRoot();
            BytecodeLabel lbl = b.createLabel();

            b.beginTag(ExpressionTag.class);
            b.emitBranch(lbl);
            b.endTag(ExpressionTag.class);

            b.beginTryFinally(() -> {
                b.emitLoadConstant(123L);
            });
            b.emitLoadConstant(555L);
            b.endTryFinally();

            b.emitLabel(lbl);

            b.beginReturn();
            b.emitLoadConstant(42L);
            b.endReturn();

            b.endRoot();
        });

        assertEquals(42L, node.getCallTarget().call());
        attachEventListener(SourceSectionFilter.newBuilder().tagIs(ExpressionTag.class,
                        StatementTag.class).build());

        assertEquals(42L, node.getCallTarget().call());
    }

    @Test
    public void testReachabilityTryFinallyEarlyExit() {
        TagInstrumentationTestRootNode node = parse((b) -> {
            b.beginRoot();
            BytecodeLabel lbl = b.createLabel();

            b.beginTag(ExpressionTag.class);
            b.emitBranch(lbl);
            b.endTag(ExpressionTag.class);

            b.beginTryFinally(() -> {
                b.emitLoadConstant(1L);
            });
            // early exit: when we emit the finally handler in-line, it should increase the max
            // stack height, even though it's unreachable.
            b.beginAdd();
            b.emitLoadConstant(2L);
            b.beginAdd();
            b.emitLoadConstant(3L);
            b.beginAdd();
            b.emitLoadConstant(4L);
            b.beginBlock();
            b.beginReturn();
            b.emitLoadConstant(5L);
            b.endReturn();
            b.emitLoadConstant(6L);
            b.endBlock();
            b.endAdd();
            b.endAdd();
            b.endAdd();
            b.endTryFinally();

            b.emitLabel(lbl);

            b.beginReturn();
            b.emitLoadConstant(42L);
            b.endReturn();

            b.endRoot();
        });

        assertEquals(42L, node.getCallTarget().call());
        attachEventListener(SourceSectionFilter.newBuilder().tagIs(ExpressionTag.class,
                        StatementTag.class).build());

        assertEquals(42L, node.getCallTarget().call());
    }

    @Test
    public void testReachabilityYield() {
        TagInstrumentationTestRootNode node = parse((b) -> {
            b.beginRoot();

            b.beginTryFinally(() -> {
                b.beginYield();
                b.emitLoadConstant(123L);
                b.endYield();
            });

            b.beginTag(ExpressionTag.class);
            b.beginReturn();
            b.beginBlock();
            b.emitThrow();
            b.emitLoadConstant(42L);
            b.endBlock();
            b.endReturn();
            b.endTag(ExpressionTag.class);

            b.endTryFinally();

            b.endRoot();
        });

        ContinuationResult cont;
        cont = (ContinuationResult) node.getCallTarget().call();
        assertEquals(123L, cont.getResult());
        try {
            cont.continueWith(456L);
            fail("exception expected");
        } catch (TestException ex) {
            // pass
        }

        attachEventListener(SourceSectionFilter.newBuilder().tagIs(ExpressionTag.class,
                        StatementTag.class).build());

        cont = (ContinuationResult) node.getCallTarget().call();
        assertEquals(123L, cont.getResult());
        try {
            cont.continueWith(456L);
            fail("exception expected");
        } catch (TestException ex) {
            // pass
        }
    }

    @SuppressWarnings("serial")
    static class TestException extends AbstractTruffleException {

        TestException(Node location) {
            super(location);
        }

    }

    @GenerateBytecode(languageClass = TagTestLanguage.class, //
                    enableQuickening = true, //
                    enableUncachedInterpreter = true,  //
                    enableTagInstrumentation = true, //
                    enableSerialization = true, //
                    enableYield = true, //
                    boxingEliminationTypes = {int.class})
    @OperationProxy(value = ExpressionAdd.class, name = "ImplicitExpressionAddProxy", tags = ExpressionTag.class)
    public abstract static class TagInstrumentationTestRootNode extends DebugBytecodeRootNode implements BytecodeRootNode {

        protected TagInstrumentationTestRootNode(TagTestLanguage language, FrameDescriptor frameDescriptor) {
            super(language, frameDescriptor);
        }

        @Operation
        static final class Add {
            @Specialization
            public static int doInt(int a, int b) {
                return a + b;
            }
        }

        public Throwable interceptInternalException(Throwable t, VirtualFrame frame, BytecodeNode bytecodeNode, int bci) {
            return super.interceptInternalException(t, frame, bytecodeNode, bci);
        }

        public AbstractTruffleException interceptTruffleException(AbstractTruffleException ex, VirtualFrame frame, BytecodeNode bytecodeNode, int bci) {
            return super.interceptTruffleException(ex, frame, bytecodeNode, bci);
        }

        public Object interceptControlFlowException(ControlFlowException ex, VirtualFrame frame, BytecodeNode bytecodeNode, int bci) throws Throwable {
            return super.interceptControlFlowException(ex, frame, bytecodeNode, bci);
        }

        @Operation(tags = ExpressionTag.class)
        static final class ImplicitExpressionAdd {
            @Specialization
            public static int doInt(int a, int b) {
                return a + b;
            }
        }

        @Operation
        static final class IsNot {
            @Specialization
            public static boolean doInt(int operand, int value) {
                return operand != value;
            }
        }

        @Operation
        static final class Is {

            @Specialization
            public static boolean doInt(int operand, int value) {
                return operand == value;
            }
        }

        @Operation
        @ConstantOperand(name = "runnable", type = Runnable.class)
        static final class InvokeRunnable {
            @Specialization
            public static void doRunnable(Runnable r) {
                r.run();
            }
        }

        @Operation
        @ConstantOperand(name = "rootNode", type = TagInstrumentationTestRootNode.class)
        static final class InvokeRootNode {
            @Specialization
            public static Object doRunnable(TagInstrumentationTestRootNode rootNode) {
                return rootNode.getCallTarget().call();
            }
        }

        @Operation
        static final class Throw {
            @Specialization
            public static void doInt(@Bind Node node) {
                throw new TestException(node);
            }
        }

        @Operation
        @ConstantOperand(name = "resumeValue", type = Object.class)
        static final class Resume {
            @Specialization
            public static Object doResume(Object resumeValue, ContinuationResult cont) {
                return cont.continueWith(resumeValue);
            }
        }

        @Operation
        static final class Nop {
            @Specialization
            public static void doNop() {
                // nop
            }
        }

        @Operation
        static final class ValueOrThrow {
            @Specialization
            public static Object doInt(Object value, boolean shouldThrow, @Bind Node node) {
                if (shouldThrow) {
                    throw new TestException(node);
                }
                return value;
            }
        }
    }

    @GenerateBytecode(languageClass = TagTestLanguage.class, //
                    enableQuickening = true, //
                    enableUncachedInterpreter = true,  //
                    enableTagInstrumentation = true, //
                    enableSerialization = true, boxingEliminationTypes = {int.class})
    public abstract static class TagInstrumentationTestWithPrologAndEpilogRootNode extends DebugBytecodeRootNode implements BytecodeRootNode {

        protected TagInstrumentationTestWithPrologAndEpilogRootNode(TagTestLanguage language, FrameDescriptor frameDescriptor) {
            super(language, frameDescriptor);
        }

        @Prolog
        static final class EnterMethod {
            @Specialization
            public static void doDefault(@Bind Node node) {
                TagTestLanguage.getThreadData(node).notifyProlog();
            }
        }

        @EpilogExceptional
        static final class LeaveExceptional {
            @Specialization
            public static void doDefault(@SuppressWarnings("unused") AbstractTruffleException t, @Bind Node node) {
                TagTestLanguage.getThreadData(node).notifyEpilogExceptional();
            }
        }

        @EpilogReturn
        static final class LeaveValue {
            @Specialization
            public static int doDefault(int a, @Bind Node node) {
                TagTestLanguage.getThreadData(node).notifyEpilogValue(a);
                return a;
            }
        }

        @Operation
        static final class InvokeRunnable {
            @Specialization
            public static void doRunnable(Runnable r) {
                r.run();
            }
        }

        @Operation
        static final class Throw {
            @Specialization
            public static void doInt(@Bind Node node) {
                throw new TestException(node);
            }
        }

        @Operation
        static final class Add {
            @Specialization
            public static int doInt(int a, int b) {
                return a + b;
            }
        }

        @Operation
        static final class IsNot {
            @Specialization
            public static boolean doInt(int operand, int value) {
                return operand != value;
            }
        }

        @Operation
        static final class Is {

            @Specialization
            public static boolean doInt(int operand, int value) {
                return operand == value;
            }
        }

    }

    @TruffleInstrument.Registration(id = TagTestInstrumentation.ID, services = Instrumenter.class)
    public static class TagTestInstrumentation extends TruffleInstrument {

        public static final String ID = "bytecode_TagTestInstrument";

        @Override
        protected void onCreate(Env env) {
            env.registerService(env.getInstrumenter());
        }
    }

    static class ThreadLocalData {

        private final AtomicInteger eventCount = new AtomicInteger(0);

        public int newEvent() {
            return eventCount.getAndIncrement();
        }

        boolean trackProlog;

        int prologIndex = -1;
        int epilogValue = -1;
        Object epilogValueObject;
        int epilogExceptional = -1;

        public void reset() {
            prologIndex = -1;
            epilogValue = -1;
            epilogExceptional = -1;
            eventCount.set(0);
            epilogValueObject = null;
        }

        public void notifyProlog() {
            if (!trackProlog) {
                return;
            }
            if (prologIndex != -1) {
                throw new AssertionError("already executed");
            }
            prologIndex = newEvent();
        }

        public void notifyEpilogValue(int a) {
            if (!trackProlog) {
                return;
            }
            if (epilogValue != -1) {
                throw new AssertionError("already executed");
            }
            epilogValue = newEvent();
            epilogValueObject = a;
        }

        public void notifyEpilogExceptional() {
            if (!trackProlog) {
                return;
            }
            if (epilogExceptional != -1) {
                throw new AssertionError("already executed");
            }
            epilogExceptional = newEvent();
        }

    }

    @TruffleLanguage.Registration(id = TagTestLanguage.ID)
    @ProvidedTags({StandardTags.RootBodyTag.class, StandardTags.ExpressionTag.class, StandardTags.StatementTag.class, StandardTags.RootTag.class})
    public static class TagTestLanguage extends TruffleLanguage<Object> {

        public static final String ID = "bytecode_TagTestLanguage";

        final ContextThreadLocal<ThreadLocalData> threadLocal = this.locals.createContextThreadLocal((c, t) -> new ThreadLocalData());

        @Override
        protected Object createContext(Env env) {
            return new Object();
        }

        static final LanguageReference<TagTestLanguage> REF = LanguageReference.create(TagTestLanguage.class);

        static ThreadLocalData getThreadData(Node node) {
            return TagTestLanguage.REF.get(node).threadLocal.get();
        }

    }

    @TruffleLanguage.Registration(id = NoRootTagTestLanguage.ID)
    @ProvidedTags({RootBodyTag.class, ExpressionTag.class})
    public static class NoRootTagTestLanguage extends TruffleLanguage<Object> {

        public static final String ID = "bytecode_NoRootTagTestLanguage";

        @Override
        protected Object createContext(Env env) {
            return new Object();
        }

    }

    @ExpectError("Tag instrumentation uses implicit root tagging, but the RootTag was not provided by the language class 'com.oracle.truffle.api.bytecode.test.TagTest.NoRootTagTestLanguage'. " +
                    "Specify the tag using @ProvidedTags(RootTag.class) on the language class or explicitly disable root tagging using @GenerateBytecode(.., enableRootTagging=false) to resolve this.")
    @GenerateBytecode(languageClass = NoRootTagTestLanguage.class, //
                    enableTagInstrumentation = true)
    public abstract static class ErrorNoRootTag extends DebugBytecodeRootNode implements BytecodeRootNode {

        protected ErrorNoRootTag(NoRootTagTestLanguage language, FrameDescriptor frameDescriptor) {
            super(language, frameDescriptor);
        }

        @Operation
        static final class Is {

            @Specialization
            public static boolean doInt(int operand, int value) {
                return operand == value;
            }
        }

    }

    @GenerateBytecode(languageClass = NoRootTagTestLanguage.class, //
                    enableTagInstrumentation = true, enableRootTagging = false)
    public abstract static class NoRootTagNoError extends DebugBytecodeRootNode implements BytecodeRootNode {

        protected NoRootTagNoError(NoRootTagTestLanguage language, FrameDescriptor frameDescriptor) {
            super(language, frameDescriptor);
        }

        @Operation
        static final class Is {

            @Specialization
            public static boolean doInt(int operand, int value) {
                return operand == value;
            }
        }

    }

    @TruffleLanguage.Registration(id = NoRootBodyTagTestLanguage.ID)
    @ProvidedTags({RootTag.class, ExpressionTag.class})
    public static class NoRootBodyTagTestLanguage extends TruffleLanguage<Object> {

        public static final String ID = "bytecode_NoRootBodyTagTestLanguage";

        @Override
        protected Object createContext(Env env) {
            return new Object();
        }

    }

    @ExpectError("Tag instrumentation uses implicit root body tagging, but the RootTag was not provided by the language class 'com.oracle.truffle.api.bytecode.test.TagTest.NoRootBodyTagTestLanguage'. " +
                    "Specify the tag using @ProvidedTags(RootBodyTag.class) on the language class or explicitly disable root tagging using @GenerateBytecode(.., enableRootBodyTagging=false) to resolve this.")
    @GenerateBytecode(languageClass = NoRootBodyTagTestLanguage.class, //
                    enableTagInstrumentation = true)
    public abstract static class ErrorNoRootBodyTag extends DebugBytecodeRootNode implements BytecodeRootNode {

        protected ErrorNoRootBodyTag(NoRootBodyTagTestLanguage language, FrameDescriptor frameDescriptor) {
            super(language, frameDescriptor);
        }

        @Operation
        static final class Is {

            @Specialization
            public static boolean doInt(int operand, int value) {
                return operand == value;
            }
        }

    }

    @GenerateBytecode(languageClass = NoRootBodyTagTestLanguage.class, //
                    enableTagInstrumentation = true, enableRootBodyTagging = false)
    public abstract static class NoRootBodyTagNoError extends DebugBytecodeRootNode implements BytecodeRootNode {

        protected NoRootBodyTagNoError(NoRootBodyTagTestLanguage language, FrameDescriptor frameDescriptor) {
            super(language, frameDescriptor);
        }

        @Operation
        static final class Is {

            @Specialization
            public static boolean doInt(int operand, int value) {
                return operand == value;
            }
        }

    }

    @GenerateBytecode(languageClass = NoRootBodyTagTestLanguage.class, //
                    enableTagInstrumentation = false)
    public abstract static class ErrorImplicitTag1 extends DebugBytecodeRootNode implements BytecodeRootNode {

        protected ErrorImplicitTag1(NoRootBodyTagTestLanguage language, FrameDescriptor frameDescriptor) {
            super(language, frameDescriptor);
        }

        @ExpectError("Tag instrumentation is not enabled. The tags attribute can only be used if tag instrumentation is enabled for the parent root node. " +
                        "Enable tag instrumentation using @GenerateBytecode(... enableTagInstrumentation = true) to resolve this or remove the tags attribute.")
        @Operation(tags = ExpressionTag.class)
        static final class Is {

            @Specialization
            public static boolean doInt(int operand, int value) {
                return operand == value;
            }
        }

    }

    @GenerateBytecode(languageClass = NoRootTagTestLanguage.class, //
                    enableTagInstrumentation = true, enableRootTagging = false)
    public abstract static class ErrorImplicitTag2 extends DebugBytecodeRootNode implements BytecodeRootNode {

        protected ErrorImplicitTag2(NoRootTagTestLanguage language, FrameDescriptor frameDescriptor) {
            super(language, frameDescriptor);
        }

        @ExpectError("Invalid tag 'StatementTag' specified. The tag is not provided by language 'com.oracle.truffle.api.bytecode.test.TagTest.NoRootTagTestLanguage'.")
        @Operation(tags = StatementTag.class)
        static final class Is {

            @Specialization
            public static boolean doInt(int operand, int value) {
                return operand == value;
            }
        }

    }

    @GenerateBytecode(languageClass = NoRootBodyTagTestLanguage.class, //
                    enableTagInstrumentation = false)
    @ExpectError("Tag instrumentation is not enabled. The tags attribute can only be used if tag instrumentation is enabled for the parent root node. " +
                    "Enable tag instrumentation using @GenerateBytecode(... enableTagInstrumentation = true) to resolve this or remove the tags attribute.")
    @OperationProxy(value = ExpressionAdd.class, tags = ExpressionTag.class)
    public abstract static class ErrorImplicitTag3 extends DebugBytecodeRootNode implements BytecodeRootNode {

        protected ErrorImplicitTag3(NoRootBodyTagTestLanguage language, FrameDescriptor frameDescriptor) {
            super(language, frameDescriptor);
        }

    }

    @GenerateBytecode(languageClass = NoRootTagTestLanguage.class, //
                    enableTagInstrumentation = true, enableRootTagging = false)
    @ExpectError("Invalid tag 'StatementTag' specified. The tag is not provided by language 'com.oracle.truffle.api.bytecode.test.TagTest.NoRootTagTestLanguage'.")
    @OperationProxy(value = ExpressionAdd.class, tags = StatementTag.class)
    public abstract static class ErrorImplicitTag4 extends DebugBytecodeRootNode implements BytecodeRootNode {

        protected ErrorImplicitTag4(NoRootTagTestLanguage language, FrameDescriptor frameDescriptor) {
            super(language, frameDescriptor);
        }

    }

    @OperationProxy.Proxyable(allowUncached = true)
    @SuppressWarnings("truffle-inlining")
    abstract static class ExpressionAdd extends Node {

        public abstract int execute(int a, int b);

        @Specialization
        public static int doInt(int a, int b) {
            return a + b;
        }
    }

    static class TestTag1 extends Tag {
    }

    static class TestTag2 extends Tag {
    }

    static class TestTag3 extends Tag {
    }

    static class TestTag4 extends Tag {
    }

    static class TestTag5 extends Tag {
    }

    static class TestTag6 extends Tag {
    }

    static class TestTag7 extends Tag {
    }

    static class TestTag8 extends Tag {
    }

    static class TestTag9 extends Tag {
    }

    static class TestTag10 extends Tag {
    }

    static class TestTag11 extends Tag {
    }

    static class TestTag12 extends Tag {
    }

    static class TestTag13 extends Tag {
    }

    static class TestTag14 extends Tag {
    }

    static class TestTag15 extends Tag {
    }

    static class TestTag16 extends Tag {
    }

    static class TestTag17 extends Tag {
    }

    static class TestTag18 extends Tag {
    }

    static class TestTag19 extends Tag {
    }

    static class TestTag20 extends Tag {
    }

    static class TestTag21 extends Tag {
    }

    static class TestTag22 extends Tag {
    }

    static class TestTag23 extends Tag {
    }

    static class TestTag24 extends Tag {
    }

    static class TestTag25 extends Tag {
    }

    static class TestTag26 extends Tag {
    }

    static class TestTag27 extends Tag {
    }

    static class TestTag28 extends Tag {
    }

    static class TestTag29 extends Tag {
    }

    static class TestTag30 extends Tag {
    }

    static class TestTag31 extends Tag {
    }

    static class TestTag32 extends Tag {
    }

    static class TestTag33 extends Tag {
    }

    @TruffleLanguage.Registration(id = ManyRootTagTestLanguage.ID)
    @ProvidedTags({TestTag1.class,
                    TestTag2.class,
                    TestTag3.class,
                    TestTag4.class,
                    TestTag5.class,
                    TestTag6.class,
                    TestTag7.class,
                    TestTag8.class,
                    TestTag9.class,
                    TestTag10.class,
                    TestTag11.class,
                    TestTag12.class,
                    TestTag13.class,
                    TestTag14.class,
                    TestTag15.class,
                    TestTag16.class,
                    TestTag17.class,
                    TestTag18.class,
                    TestTag19.class,
                    TestTag20.class,
                    TestTag21.class,
                    TestTag22.class,
                    TestTag23.class,
                    TestTag24.class,
                    TestTag25.class,
                    TestTag26.class,
                    TestTag27.class,
                    TestTag28.class,
                    TestTag29.class,
                    TestTag30.class,
                    TestTag31.class,
                    TestTag32.class,
    })
    public static class ManyRootTagTestLanguage extends TruffleLanguage<Object> {

        public static final String ID = "bytecode_ManyRootTagTestLanguage";

        @Override
        protected Object createContext(Env env) {
            return new Object();
        }

    }

    @GenerateBytecode(languageClass = ManyRootTagTestLanguage.class, //
                    enableTagInstrumentation = true, enableRootBodyTagging = false, enableRootTagging = false)
    public abstract static class ManyTagsRootNode extends DebugBytecodeRootNode implements BytecodeRootNode {

        protected ManyTagsRootNode(ManyRootTagTestLanguage language, FrameDescriptor frameDescriptor) {
            super(language, frameDescriptor);
        }

        @Operation
        static final class Is {

            @Specialization
            public static boolean doInt(int operand, int value) {
                return operand == value;
            }
        }

    }

    @TruffleLanguage.Registration(id = TooManyTagTestLanguage.ID)
    @ProvidedTags({TestTag1.class,
                    TestTag2.class,
                    TestTag3.class,
                    TestTag4.class,
                    TestTag5.class,
                    TestTag6.class,
                    TestTag7.class,
                    TestTag8.class,
                    TestTag9.class,
                    TestTag10.class,
                    TestTag11.class,
                    TestTag12.class,
                    TestTag13.class,
                    TestTag14.class,
                    TestTag15.class,
                    TestTag16.class,
                    TestTag17.class,
                    TestTag18.class,
                    TestTag19.class,
                    TestTag20.class,
                    TestTag21.class,
                    TestTag22.class,
                    TestTag23.class,
                    TestTag24.class,
                    TestTag25.class,
                    TestTag26.class,
                    TestTag27.class,
                    TestTag28.class,
                    TestTag29.class,
                    TestTag30.class,
                    TestTag31.class,
                    TestTag32.class,
                    TestTag33.class,
    })
    public static class TooManyTagTestLanguage extends TruffleLanguage<Object> {

        public static final String ID = "bytecode_TooManyTagTestLanguage";

        @Override
        protected Object createContext(Env env) {
            return new Object();
        }

    }

    @ExpectError("Tag instrumentation is currently limited to a maximum of 32 tags.%")
    @GenerateBytecode(languageClass = TooManyTagTestLanguage.class, //
                    enableTagInstrumentation = true, //
                    enableRootBodyTagging = false, enableRootTagging = false)
    public abstract static class TooManyTagsRootNode extends DebugBytecodeRootNode implements BytecodeRootNode {

        protected TooManyTagsRootNode(TooManyTagTestLanguage language, FrameDescriptor frameDescriptor) {
            super(language, frameDescriptor);
        }

        @Operation
        static final class Is {

            @Specialization
            public static boolean doInt(int operand, int value) {
                return operand == value;
            }
        }

    }

    @ExpectError("Too many @Instrumentation and provided tags specified. %")
    @GenerateBytecode(languageClass = ManyRootTagTestLanguage.class, //
                    enableTagInstrumentation = true, //
                    enableRootBodyTagging = false, enableRootTagging = false)
    public abstract static class TooManyTagsAndInstrumentsRootNode extends DebugBytecodeRootNode implements BytecodeRootNode {

        protected TooManyTagsAndInstrumentsRootNode(ManyRootTagTestLanguage language, FrameDescriptor frameDescriptor) {
            super(language, frameDescriptor);
        }

        @Operation
        static final class Is {

            @Specialization
            public static boolean doInt(int operand, int value) {
                return operand == value;
            }
        }

        @Instrumentation
        static final class Instrumentation1 {
            @Specialization
            public static void doDefault() {
            }
        }

        @Instrumentation
        static final class Instrumentation2 {
            @Specialization
            public static void doDefault() {
            }
        }

        @Instrumentation
        static final class Instrumentation3 {
            @Specialization
            public static void doDefault() {
            }
        }

        @Instrumentation
        static final class Instrumentation4 {
            @Specialization
            public static void doDefault() {
            }
        }

        @Instrumentation
        static final class Instrumentation5 {
            @Specialization
            public static void doDefault() {
            }
        }

        @Instrumentation
        static final class Instrumentation6 {
            @Specialization
            public static void doDefault() {
            }
        }

        @Instrumentation
        static final class Instrumentation7 {
            @Specialization
            public static void doDefault() {
            }
        }

        @Instrumentation
        static final class Instrumentation8 {
            @Specialization
            public static void doDefault() {
            }
        }

        @Instrumentation
        static final class Instrumentation9 {
            @Specialization
            public static void doDefault() {
            }
        }

        @Instrumentation
        static final class Instrumentation10 {
            @Specialization
            public static void doDefault() {
            }
        }

        @Instrumentation
        static final class Instrumentation11 {
            @Specialization
            public static void doDefault() {
            }
        }

        @Instrumentation
        static final class Instrumentation12 {
            @Specialization
            public static void doDefault() {
            }
        }

        @Instrumentation
        static final class Instrumentation13 {
            @Specialization
            public static void doDefault() {
            }
        }

        @Instrumentation
        static final class Instrumentation14 {
            @Specialization
            public static void doDefault() {
            }
        }

        @Instrumentation
        static final class Instrumentation15 {
            @Specialization
            public static void doDefault() {
            }
        }

        @Instrumentation
        static final class Instrumentation16 {
            @Specialization
            public static void doDefault() {
            }
        }

        @Instrumentation
        static final class Instrumentation17 {
            @Specialization
            public static void doDefault() {
            }
        }

        @Instrumentation
        static final class Instrumentation18 {
            @Specialization
            public static void doDefault() {
            }
        }

        @Instrumentation
        static final class Instrumentation19 {
            @Specialization
            public static void doDefault() {
            }
        }

    }

}
