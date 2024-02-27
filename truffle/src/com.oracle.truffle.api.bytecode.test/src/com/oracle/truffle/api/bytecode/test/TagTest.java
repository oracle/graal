/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.graalvm.polyglot.Context;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.ContextThreadLocal;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.bytecode.BytecodeConfig;
import com.oracle.truffle.api.bytecode.BytecodeParser;
import com.oracle.truffle.api.bytecode.BytecodeRootNode;
import com.oracle.truffle.api.bytecode.BytecodeRootNodes;
import com.oracle.truffle.api.bytecode.GenerateBytecode;
import com.oracle.truffle.api.bytecode.Operation;
import com.oracle.truffle.api.bytecode.introspection.TagTree;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.ExecutionEventNode;
import com.oracle.truffle.api.instrumentation.Instrumenter;
import com.oracle.truffle.api.instrumentation.ProvidedTags;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.instrumentation.StandardTags.ExpressionTag;
import com.oracle.truffle.api.instrumentation.StandardTags.StatementTag;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;

public class TagTest extends AbstractQuickeningTest {

    private static TagInstrumentationTestRootNode parse(BytecodeParser<TagInstrumentationTestRootNodeGen.Builder> parser) {
        BytecodeRootNodes<TagInstrumentationTestRootNode> nodes = TagInstrumentationTestRootNodeGen.create(BytecodeConfig.DEFAULT, parser);
        TagInstrumentationTestRootNode root = nodes.getNodes().get(nodes.getNodes().size() - 1);
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
        RETURN_VALUE
    }

    record Event(EventKind kind, int startBci, int endBci, Object value) {
    }

    private List<Event> attachEventListener(SourceSectionFilter filter) {
        List<Event> events = new ArrayList<>();
        instrumenter.attachExecutionEventFactory(filter, (e) -> {
            TagTree tree = (TagTree) e.getInstrumentedNode();
            return new ExecutionEventNode() {

                @Override
                public void onEnter(VirtualFrame f) {
                    events.add(new Event(EventKind.ENTER, tree.getStartBci(), tree.getEndBci(), null));
                }

                @Override
                public void onReturnValue(VirtualFrame f, Object arg) {
                    events.add(new Event(EventKind.RETURN_VALUE, tree.getStartBci(), tree.getEndBci(), arg));
                }
            };
        });
        return events;
    }

    @Test
    public void testStatementsCached() {
        TagInstrumentationTestRootNode node = parse((b) -> {
            b.beginRoot(TagTestLanguage.REF.get(null));

            var local = b.createLocal();
            b.beginBlock();

            b.beginTag(StatementTag.class);
            b.beginStoreLocal(local);
            b.beginTag(ExpressionTag.class);
            b.emitLoadConstant(42);
            b.endTag(ExpressionTag.class);
            b.endStoreLocal();
            b.endTag(StatementTag.class);

            b.beginTag(StatementTag.class, ExpressionTag.class);
            b.beginReturn();
            b.beginTag(ExpressionTag.class);
            b.emitLoadLocal(local);
            b.endTag(ExpressionTag.class);
            b.endReturn();
            b.endTag(StatementTag.class, ExpressionTag.class);

            b.endBlock();

            b.endRoot();
        });
        node.getBytecodeNode().setUncachedThreshold(0);

        assertInstructions(node,
                        "load.constant",
                        "store.local",
                        "load.local",
                        "return",
                        "pop");
        assertEquals(42, node.getCallTarget().call());
        assertQuickenings(node, 3, 2);

        assertInstructions(node,
                        "load.constant$Int",
                        "store.local$Int$unboxed",
                        "load.local$Int",
                        "return",
                        "pop");

        List<Event> events = attachEventListener(SourceSectionFilter.newBuilder().tagIs(StandardTags.StatementTag.class).build());

        assertInstructions(node,
                        "tag.enter",
                        "load.constant",
                        "store.local",
                        "tag.leaveVoid",
                        "tag.enter",
                        "load.local",
                        "tag.leave",
                        "return",
                        "tag.leave",
                        "pop",
                        "trap");

        assertEquals(42, node.getCallTarget().call());

        assertInstructions(node,
                        "tag.enter",
                        "load.constant$Int",
                        "store.local$Int$unboxed",
                        "tag.leaveVoid",
                        "tag.enter",
                        "load.local$Int$unboxed",
                        "tag.leave$Int",
                        "return",
                        "tag.leave",
                        "pop",
                        "trap");

        QuickeningCounts counts = assertQuickenings(node, 8, 4);

        assertEvents(events,
                        new Event(EventKind.ENTER, 0, 7, null),
                        new Event(EventKind.RETURN_VALUE, 0, 7, null),
                        new Event(EventKind.ENTER, 9, 17, null),
                        new Event(EventKind.RETURN_VALUE, 9, 17, 42));

        assertStable(counts, node);

    }

    @Test
    public void testStatementsUncached() {
        TagInstrumentationTestRootNode node = parse((b) -> {
            b.beginRoot(TagTestLanguage.REF.get(null));

            var local = b.createLocal();
            b.beginBlock();

            b.beginTag(StatementTag.class);
            b.beginStoreLocal(local);
            b.beginTag(ExpressionTag.class);
            b.emitLoadConstant(42);
            b.endTag(ExpressionTag.class);
            b.endStoreLocal();
            b.endTag(StatementTag.class);

            b.beginTag(StatementTag.class, ExpressionTag.class);
            b.beginReturn();
            b.beginTag(ExpressionTag.class);
            b.emitLoadLocal(local);
            b.endTag(ExpressionTag.class);
            b.endReturn();
            b.endTag(StatementTag.class, ExpressionTag.class);

            b.endBlock();

            b.endRoot();
        });
        node.getBytecodeNode().setUncachedThreshold(Integer.MAX_VALUE);

        assertInstructions(node,
                        "load.constant",
                        "store.local",
                        "load.local",
                        "return",
                        "pop");
        assertEquals(42, node.getCallTarget().call());
        assertQuickenings(node, 0, 0);

        assertInstructions(node,
                        "load.constant",
                        "store.local",
                        "load.local",
                        "return",
                        "pop");

        List<Event> events = attachEventListener(SourceSectionFilter.newBuilder().tagIs(StandardTags.StatementTag.class).build());

        assertInstructions(node,
                        "tag.enter",
                        "load.constant",
                        "store.local",
                        "tag.leaveVoid",
                        "tag.enter",
                        "load.local",
                        "tag.leave",
                        "return",
                        "tag.leave",
                        "pop",
                        "trap");

        assertEquals(42, node.getCallTarget().call());

        assertInstructions(node,
                        "tag.enter",
                        "load.constant",
                        "store.local",
                        "tag.leaveVoid",
                        "tag.enter",
                        "load.local",
                        "tag.leave",
                        "return",
                        "tag.leave",
                        "pop",
                        "trap");

        QuickeningCounts counts = assertQuickenings(node, 0, 0);

        assertEvents(events,
                        new Event(EventKind.ENTER, 0, 7, null),
                        new Event(EventKind.RETURN_VALUE, 0, 7, null),
                        new Event(EventKind.ENTER, 9, 17, null),
                        new Event(EventKind.RETURN_VALUE, 9, 17, 42));

        assertStable(counts, node);
    }

    private static void assertEvents(List<Event> actualEvents, Event... expectedEvents) {
        assertEquals("expectedEvents: " + Arrays.toString(expectedEvents) + " actualEvents:" + actualEvents, expectedEvents.length, actualEvents.size());

        for (int i = 0; i < expectedEvents.length; i++) {
            Event actualEvent = actualEvents.get(i);
            Event expectedEvent = expectedEvents[i];
            assertEquals("event kind at at index" + i, actualEvent.kind, expectedEvent.kind);
            assertEquals("event value at at index" + i, actualEvent.value, expectedEvent.value);
            assertEquals("start bci at at index" + i, actualEvent.startBci, expectedEvent.startBci);
            assertEquals("end bci at at index" + i, actualEvent.endBci, expectedEvent.endBci);
        }

    }

    @Test
    public void testStatementsAndExpressionUncached() {
        TagInstrumentationTestRootNode node = parse((b) -> {
            b.beginRoot(TagTestLanguage.REF.get(null));

            var local = b.createLocal();
            b.beginBlock();

            b.beginTag(StatementTag.class);
            b.beginStoreLocal(local);
            b.beginTag(ExpressionTag.class);
            b.emitLoadConstant(42);
            b.endTag(ExpressionTag.class);
            b.endStoreLocal();
            b.endTag(StatementTag.class);

            b.beginTag(StatementTag.class, ExpressionTag.class);
            b.beginReturn();
            b.beginTag(ExpressionTag.class);
            b.emitLoadLocal(local);
            b.endTag(ExpressionTag.class);
            b.endReturn();
            b.endTag(StatementTag.class, ExpressionTag.class);

            b.endBlock();

            b.endRoot();
        });
        node.getBytecodeNode().setUncachedThreshold(Integer.MAX_VALUE);

        assertInstructions(node,
                        "load.constant",
                        "store.local",
                        "load.local",
                        "return",
                        "pop");
        assertEquals(42, node.getCallTarget().call());
        assertQuickenings(node, 0, 0);

        assertInstructions(node,
                        "load.constant",
                        "store.local",
                        "load.local",
                        "return",
                        "pop");

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
                        "return",
                        "tag.leave",
                        "pop",
                        "trap");

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
                        "return",
                        "tag.leave",
                        "pop",
                        "trap");
        QuickeningCounts counts = assertQuickenings(node, 0, 0);

        assertEvents(events,
                        new Event(EventKind.ENTER, 0, 12, null),
                        new Event(EventKind.ENTER, 2, 6, null),
                        new Event(EventKind.RETURN_VALUE, 2, 6, 42),
                        new Event(EventKind.RETURN_VALUE, 0, 12, null),
                        new Event(EventKind.ENTER, 0x000e, 0x001b, null),
                        new Event(EventKind.ENTER, 0x0010, 0x0014, null),
                        new Event(EventKind.RETURN_VALUE, 0x0010, 0x0014, 42),
                        new Event(EventKind.RETURN_VALUE, 0x000e, 0x001b, 42));

        assertStable(counts, node);
    }

    @Test
    public void testImplicitRootBodyTag() {
        SourceSectionFilter filter = SourceSectionFilter.newBuilder().tagIs(StandardTags.RootBodyTag.class).build();
        instrumenter.attachExecutionEventFactory(filter, (e) -> {
            return new ExecutionEventNode() {
            };
        });

        TagInstrumentationTestRootNode node = parse((b) -> {
            b.beginRoot(TagTestLanguage.REF.get(null));

            b.beginTag(ExpressionTag.class);

            b.beginBlock();
            b.endBlock();

            b.endTag(ExpressionTag.class);

            b.beginReturn();
            b.emitLoadConstant(42);
            b.endReturn();
            b.endRoot();
        });
        System.out.println(node.dump());
        assertEquals(42, node.getCallTarget().call());
    }

    @Test
    public void testImplicitJump() {
        SourceSectionFilter filter = SourceSectionFilter.newBuilder().tagIs(StandardTags.RootBodyTag.class).build();
        instrumenter.attachExecutionEventFactory(filter, (e) -> {
            return new ExecutionEventNode() {
            };
        });

        TagInstrumentationTestRootNode node = parse((b) -> {
            b.beginRoot(TagTestLanguage.REF.get(null));

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
        System.out.println(node.dump());
        assertEquals(42, node.getCallTarget().call());
    }

    // TODO test relocation
    @GenerateBytecode(languageClass = TagTestLanguage.class, //
                    enableQuickening = true, //
                    enableUncachedInterpreter = true,  //
                    enableTagInstrumentation = true, //
                    enableSerialization = true, boxingEliminationTypes = {int.class})
    public abstract static class TagInstrumentationTestRootNode extends DebugBytecodeRootNode implements BytecodeRootNode {

        protected TagInstrumentationTestRootNode(TruffleLanguage<?> language,
                        FrameDescriptor frameDescriptor) {
            super(language, frameDescriptor);
        }

        public void executeProlog(VirtualFrame frame) {
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

    static class ThreadLocalData {

        final List<Class<?>> events = new ArrayList<>();

        @TruffleBoundary
        void add(Class<?> c, Object operand) {
            events.add(c);
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
    }

}
