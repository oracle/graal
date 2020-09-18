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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.EventContext;
import com.oracle.truffle.api.instrumentation.ExecutionEventListener;
import com.oracle.truffle.api.instrumentation.GenerateWrapper;
import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.instrumentation.ProbeNode;
import com.oracle.truffle.api.instrumentation.ProvidedTags;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.instrumentation.Tag;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.NodeLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.test.polyglot.ProxyLanguage;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;

/**
 * Test of {@link Scope}.
 */
public class VariablesScopeTest extends AbstractInstrumentationTest {

    private static final InteropLibrary INTEROP = InteropLibrary.getFactory().getUncached();

    @Test
    public void testILScope() throws Throwable {
        assureEnabled(engine.getInstruments().get("testVariablesScopeInstrument"));
        TestScopeInstrument.INSTANCE.setTester(new ILScopeTester());
        run("ROOT(DEFINE(\ntestFunction,ROOT(\nVARIABLE(a, 10),\nVARIABLE(b, 20),\nSTATEMENT)),\nCALL(testFunction))");
        TestScopeInstrument.INSTANCE.checkForFailure();
    }

    @Test
    public void testDefaultScope() throws Throwable {
        assureEnabled(engine.getInstruments().get("testVariablesScopeInstrument"));
        createDefaultScopeLanguage(context);
        DefaultScopeTester scopeTester = new DefaultScopeTester();
        TestScopeInstrument.INSTANCE.setTester(scopeTester);
        org.graalvm.polyglot.Source source = org.graalvm.polyglot.Source.newBuilder(ProxyLanguage.ID, "test", "file").build();
        Value program = context.parse(source);
        assertEquals(9, program.execute(4, 5).asInt());
        TestScopeInstrument.INSTANCE.checkForFailure();
        assertEquals("1Enter2Enter2Exit1Exit", scopeTester.getVisitedLocations());
    }

    @TruffleInstrument.Registration(id = "testVariablesScopeInstrument", services = Object.class)
    public static class TestScopeInstrument extends TruffleInstrument {

        static TestScopeInstrument INSTANCE;

        private Tester tester;
        private boolean scopeTested;
        private Throwable failure;

        @Override
        protected void onCreate(TruffleInstrument.Env env) {
            INSTANCE = this;
            env.getInstrumenter().attachExecutionEventListener(SourceSectionFilter.newBuilder().tagIs(StandardTags.StatementTag.class).build(), new ExecutionEventListener() {
                @Override
                @TruffleBoundary
                public void onEnter(EventContext context, VirtualFrame frame) {
                    scopeTested = true;
                    try {
                        tester.doTestScope(env, context.getInstrumentedNode(), frame, true);
                    } catch (Throwable t) {
                        failure = t;
                    }
                }

                @Override
                @TruffleBoundary
                public void onReturnValue(EventContext context, VirtualFrame frame, Object result) {
                    try {
                        tester.doTestScope(env, context.getInstrumentedNode(), frame, false);
                    } catch (Throwable t) {
                        failure = t;
                    }
                }

                @Override
                public void onReturnExceptional(EventContext context, VirtualFrame frame, Throwable exception) {
                }
            });
        }

        void setTester(Tester tester) {
            scopeTested = false;
            this.tester = tester;
        }

        void checkForFailure() throws Throwable {
            tester = null;
            assertTrue("Scope instrument not triggered", scopeTested);
            if (failure != null) {
                throw failure;
            }
        }

        interface Tester {
            void doTestScope(TruffleInstrument.Env env, Node node, VirtualFrame frame, boolean nodeEnter) throws Exception;
        }
    }

    private static int getKeySize(Object object) {
        try {
            Object keys = INTEROP.getMembers(object);
            return (int) INTEROP.getArraySize(keys);
        } catch (UnsupportedMessageException e) {
            throw CompilerDirectives.shouldNotReachHere(e);
        }
    }

    private static boolean contains(Object object, String key) {
        return INTEROP.isMemberReadable(object, key);
    }

    private static Object read(Object object, String key) {
        try {
            return INTEROP.readMember(object, key);
        } catch (UnknownIdentifierException | UnsupportedMessageException e) {
            throw CompilerDirectives.shouldNotReachHere(e);
        }
    }

    private static boolean isNull(Object object) {
        return INTEROP.isNull(object);
    }

    private static class ILScopeTester implements TestScopeInstrument.Tester {

        public void doTestScope(TruffleInstrument.Env env, Node node, VirtualFrame frame, boolean nodeEnter) throws Exception {
            assertTrue(NodeLibrary.getUncached().hasScope(node, null));
            assertTrue(NodeLibrary.getUncached().hasScope(node, frame));
            Object lexicalScope = NodeLibrary.getUncached().getScope(node, null, true);
            Object dynamicScope = NodeLibrary.getUncached().getScope(node, frame, true);
            assertFalse(INTEROP.hasScopeParent(lexicalScope));
            assertFalse(INTEROP.hasScopeParent(dynamicScope));
            String scopeName = INTEROP.asString(INTEROP.toDisplayString(lexicalScope));

            int line = node.getSourceSection().getStartLine();
            if (line == 1 || line == 6) {
                assertEquals("Line = " + line + ", function name: ", "", scopeName);
            } else {
                assertEquals("Line = " + line + ", function name: ", "testFunction", scopeName);

                // Lexical access:
                TruffleObject vars = (TruffleObject) lexicalScope;
                final int numVars = Math.max(line - 3, 0);
                int varSize = getKeySize(vars);

                assertEquals("Line = " + line + ", num vars:", numVars, varSize);
                if (numVars >= 1) {
                    assertTrue("Var a: ", contains(vars, "a"));
                    assertTrue(isNull(read(vars, "a")));
                }
                if (numVars >= 2) {
                    assertTrue("Var b: ", contains(vars, "b"));
                    assertTrue(isNull(read(vars, "b")));
                }

                // Dynamic access:
                vars = (TruffleObject) dynamicScope;
                varSize = getKeySize(vars);
                assertEquals("Line = " + line + ", num vars:", numVars, varSize);
                if (numVars >= 1) {
                    assertTrue("Var a: ", contains(vars, "a"));
                    assertEquals("Var a: ", 10, read(vars, "a"));
                }
                if (numVars >= 2) {
                    assertTrue("Var b: ", contains(vars, "b"));
                    assertEquals("Var b: ", 20, read(vars, "b"));
                }
            }
            if (line == 6) {
                doTestTopScope(env);
            }
        }

        private static void doTestTopScope(TruffleInstrument.Env env) throws UnsupportedMessageException, UnknownIdentifierException, InvalidArrayIndexException {
            Object scope = env.getScope(env.getLanguages().get(InstrumentationTestLanguage.ID));
            assertFalse(INTEROP.hasScopeParent(scope));
            String scopeName = INTEROP.asString(INTEROP.toDisplayString(scope));
            assertEquals("global", scopeName);
            assertFalse(INTEROP.hasSourceLocation(scope));
            Object keys = INTEROP.getMembers(scope);
            assertNotNull(keys);
            Number size = INTEROP.getArraySize(keys);
            assertEquals(1, size.intValue());
            String functionName = (String) INTEROP.readArrayElement(keys, 0);
            assertEquals("testFunction", functionName);
            Object function = INTEROP.readMember(scope, functionName);
            assertTrue(INTEROP.isExecutable(function));
        }
    }

    private static class DefaultScopeTester implements TestScopeInstrument.Tester {

        private final StringBuilder visitedLocations = new StringBuilder();

        public void doTestScope(TruffleInstrument.Env env, Node node, VirtualFrame frame, boolean nodeEnter) throws Exception {
            assertTrue(NodeLibrary.getUncached().hasScope(node, null));
            assertTrue(NodeLibrary.getUncached().hasScope(node, frame));
            Object lexicalScope = NodeLibrary.getUncached().getScope(node, null, nodeEnter);
            Object dynamicScope = NodeLibrary.getUncached().getScope(node, frame, nodeEnter);
            assertFalse(INTEROP.hasScopeParent(lexicalScope));
            assertFalse(INTEROP.hasScopeParent(dynamicScope));
            String scopeName = INTEROP.asString(INTEROP.toDisplayString(lexicalScope));

            int line = node.getSourceSection().getStartLine();
            if (line == 1) {
                assertEquals("Line = " + line + ", scope name: ", "local", scopeName);
                assertEquals("Lexical arguments", 0, getKeySize(lexicalScope));
                assertEquals("Dunamic arguments", 2, getKeySize(dynamicScope));
                assertTrue("Argument 0: ", contains(dynamicScope, "0"));
                assertTrue("Argument 1: ", contains(dynamicScope, "1"));
                assertEquals("Argument 0: ", 4, read(dynamicScope, "0"));
                assertEquals("Argument 1: ", 5, read(dynamicScope, "1"));
            } else {
                assertEquals("Line = " + line + ", scope name: ", "local", scopeName);

                // Lexical access:
                TruffleObject vars = (TruffleObject) lexicalScope;
                int numVars = nodeEnter ? 2 : 3;
                int varSize = getKeySize(vars);

                assertEquals("Line = " + line + ", num vars:", numVars, varSize);
                if (numVars >= 1) {
                    assertTrue("Var a: ", contains(vars, "a"));
                    assertTrue(isNull(read(vars, "a")));
                }
                if (numVars >= 2) {
                    assertTrue("Var b: ", contains(vars, "b"));
                    assertTrue(isNull(read(vars, "b")));
                }
                if (numVars >= 3) {
                    assertTrue("Var n: ", contains(vars, "n"));
                    assertTrue(isNull(read(vars, "n")));
                }

                // Dynamic access:
                vars = (TruffleObject) dynamicScope;
                numVars = nodeEnter ? 1 : 2;
                varSize = getKeySize(vars);
                assertEquals("Line = " + line + ", num vars:", numVars, varSize);
                if (numVars >= 1) {
                    assertTrue("Var a: ", contains(vars, "a"));
                    assertEquals("Var a: ", 10, read(vars, "a"));
                }
                if (numVars >= 2) {
                    assertTrue("Var n: ", contains(vars, "n"));
                    assertEquals("Var n: ", 2, read(vars, "n"));
                }
            }
            if (line == 2) {
                doTestTopScope(env);
            }
            visitedLocations.append(line);
            visitedLocations.append(nodeEnter ? "Enter" : "Exit");
        }

        private static void doTestTopScope(TruffleInstrument.Env env) throws UnsupportedMessageException {
            Object scope = env.getScope(env.getLanguages().get(InstrumentationTestLanguage.ID));
            assertFalse(INTEROP.hasScopeParent(scope));
            String scopeName = INTEROP.asString(INTEROP.toDisplayString(scope));
            assertEquals("global", scopeName);
            assertFalse(INTEROP.hasSourceLocation(scope));
            Object keys = INTEROP.getMembers(scope);
            assertNotNull(keys);
            Number size = INTEROP.getArraySize(keys);
            assertEquals(0, size.intValue());
        }

        private String getVisitedLocations() {
            return visitedLocations.toString();
        }
    }

    @Test
    public void testSPIScopeCalls() throws Throwable {
        org.graalvm.polyglot.Source source = org.graalvm.polyglot.Source.create("test-custom-variables-scope-language", "test");
        assureEnabled(engine.getInstruments().get("testVariablesScopeInstrument"));
        TestScopeInstrument.INSTANCE.setTester(new CustomScopeTester());
        context.eval(source);
        TestScopeInstrument.INSTANCE.checkForFailure();
    }

    private static void createDefaultScopeLanguage(Context context) {
        ProxyLanguage language = new ProxyLanguage() {
            @Override
            protected CallTarget parse(TruffleLanguage.ParsingRequest request) throws Exception {
                return Truffle.getRuntime().createCallTarget(new RootNode(ProxyLanguage.getCurrentLanguage()) {

                    @Node.Child private DefaultRootBlockNode block = insert(new DefaultRootBlockNode());

                    @Override
                    protected boolean isInstrumentable() {
                        return true;
                    }

                    @Override
                    public Object execute(VirtualFrame frame) {
                        return block.execute(frame);
                    }
                });
            }
        };
        ProxyLanguage.setDelegate(language);
        context.initialize(ProxyLanguage.ID);
    }

    @GenerateWrapper
    static class DefaultRootBlockNode extends Node implements InstrumentableNode {

        @Child private DefaultStatementNode statementNode = new DefaultStatementNode();
        @CompilationFinal private FrameSlot a;
        @CompilationFinal private FrameSlot b;

        @Override
        public boolean isInstrumentable() {
            return true;
        }

        @Override
        public boolean hasTag(Class<? extends Tag> tag) {
            return StandardTags.RootTag.class.equals(tag) || StandardTags.StatementTag.class.equals(tag);
        }

        @Override
        public SourceSection getSourceSection() {
            return Source.newBuilder(ProxyLanguage.ID, "RootBlock", "file").build().createSection(1);
        }

        @Override
        public WrapperNode createWrapper(ProbeNode probe) {
            return new DefaultRootBlockNodeWrapper(this, probe);
        }

        Object execute(VirtualFrame frame) {
            if (a == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                a = frame.getFrameDescriptor().findOrAddFrameSlot("a");
                b = frame.getFrameDescriptor().findOrAddFrameSlot("b");
            }
            frame.setInt(a, 10);
            int ret = statementNode.execute(frame);
            frame.setBoolean(b, true);
            return ret;
        }
    }

    @GenerateWrapper
    static class DefaultStatementNode extends Node implements InstrumentableNode {

        @CompilationFinal private FrameSlot n;

        @Override
        public boolean isInstrumentable() {
            return true;
        }

        @Override
        public boolean hasTag(Class<? extends Tag> tag) {
            return StandardTags.StatementTag.class.equals(tag);
        }

        @Override
        public SourceSection getSourceSection() {
            return Source.newBuilder(ProxyLanguage.ID, "\nStatement", "file").build().createSection(2);
        }

        @Override
        public WrapperNode createWrapper(ProbeNode probe) {
            return new DefaultStatementNodeWrapper(this, probe);
        }

        int execute(VirtualFrame frame) {
            if (n == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                n = frame.getFrameDescriptor().findOrAddFrameSlot("n");
            }
            Object[] arguments = frame.getArguments();
            frame.setInt(n, arguments.length);
            int s = 0;
            for (int i = 0; i < arguments.length; i++) {
                s += (int) arguments[i];
            }
            return s;
        }
    }

    @TruffleLanguage.Registration(name = "", id = "test-custom-variables-scope-language")
    @ProvidedTags({StandardTags.StatementTag.class, StandardTags.RootTag.class})
    public static class CustomScopeLanguage extends TruffleLanguage<Env> {

        @Override
        protected Env createContext(Env env) {
            return env;
        }

        @Override
        protected CallTarget parse(ParsingRequest request) throws Exception {
            return Truffle.getRuntime().createCallTarget(new CustomRoot(this));
        }

        @Override
        protected Object getScope(Env context) {
            return new TopScopeObject();
        }

        @ExportLibrary(InteropLibrary.class)
        static final class TopScopeObject implements TruffleObject {

            @ExportMessage
            @SuppressWarnings("static-method")
            boolean hasLanguage() {
                return true;
            }

            @ExportMessage
            @SuppressWarnings("static-method")
            Class<? extends TruffleLanguage<?>> getLanguage() {
                return CustomScopeLanguage.class;
            }

            @ExportMessage
            @SuppressWarnings("static-method")
            boolean isScope() {
                return true;
            }

            @ExportMessage
            @SuppressWarnings("static-method")
            boolean hasMembers() {
                return true;
            }

            @ExportMessage
            @SuppressWarnings("static-method")
            Object getMembers(@SuppressWarnings("unused") boolean includeInternal) {
                throw new UnsupportedOperationException();
            }

            @ExportMessage
            @SuppressWarnings("static-method")
            Object toDisplayString(@SuppressWarnings("unused") boolean allowSideEffects) {
                return "TopCustomScope";
            }
        }

        public static class CustomRoot extends RootNode {

            @Child private CustomRootBlockNode scopeNode = new CustomRootBlockNode();

            public CustomRoot(TruffleLanguage<?> language) {
                super(language);
            }

            @Override
            public SourceSection getSourceSection() {
                return scopeNode.getSourceSection();
            }

            @Override
            public Object execute(VirtualFrame frame) {
                return scopeNode.execute(frame);
            }

        }

        @GenerateWrapper
        @ExportLibrary(NodeLibrary.class)
        public static class CustomRootBlockNode extends Node implements InstrumentableNode {

            @Child private CustomScopeNode scopeNode = new CustomScopeNode();

            public CustomRootBlockNode() {
            }

            @SuppressWarnings("unused")
            public Object execute(VirtualFrame frame) {
                return scopeNode.execute(frame);
            }

            @Override
            public SourceSection getSourceSection() {
                return scopeNode.getSourceSection();
            }

            public WrapperNode createWrapper(ProbeNode probe) {
                return new CustomRootBlockNodeWrapper(this, probe);
            }

            public boolean isInstrumentable() {
                return true;
            }

            public boolean hasTag(Class<? extends Tag> tag) {
                return StandardTags.RootTag.class.equals(tag);
            }

            @ExportMessage
            public boolean hasScope(@SuppressWarnings("unused") Frame frame) {
                return true;
            }

            @ExportMessage
            final Object getScope(Frame frame, @SuppressWarnings("unused") boolean nodeEnter) {
                if (frame == null) {
                    return new TestObject(this, "V1");
                } else {
                    return new TestObject(this, "V1V2V3");
                }
            }
        }

        @GenerateWrapper
        @ExportLibrary(NodeLibrary.class)
        public static class CustomScopeNode extends Node implements InstrumentableNode {

            public CustomScopeNode() {
            }

            @SuppressWarnings("unused")
            public Object execute(VirtualFrame frame) {
                return 1;
            }

            @Override
            public SourceSection getSourceSection() {
                return Source.newBuilder("test-custom-variables-scope-language", "test", "unknown").build().createSection(1);
            }

            public WrapperNode createWrapper(ProbeNode probe) {
                return new CustomScopeNodeWrapper(this, probe);
            }

            public boolean isInstrumentable() {
                return true;
            }

            public boolean hasTag(Class<? extends Tag> tag) {
                return StandardTags.StatementTag.class.equals(tag);
            }

            @ExportMessage
            public boolean hasScope(@SuppressWarnings("unused") Frame frame) {
                return true;
            }

            @ExportMessage
            final Object getScope(Frame frame, @SuppressWarnings("unused") boolean nodeEnter) {
                if (frame == null) {
                    return new TestObject(this, "V1");
                } else {
                    return new TestObject(this, "V1V2V3");
                }
            }
        }
    }

    private static class CustomScopeTester implements TestScopeInstrument.Tester {

        @Override
        public void doTestScope(TruffleInstrument.Env env, Node node, VirtualFrame frame, boolean nodeEnter) {
            assertTrue(NodeLibrary.getUncached().hasScope(node, null));
            assertTrue(NodeLibrary.getUncached().hasScope(node, frame));
            try {
                Object lexicalScope = NodeLibrary.getUncached().getScope(node, null, nodeEnter);
                Object dynamicScope = NodeLibrary.getUncached().getScope(node, frame, nodeEnter);
                assertTrue(INTEROP.isScope(lexicalScope));
                assertTrue(INTEROP.isScope(dynamicScope));
                testScopeContent(lexicalScope, node, null);
                testScopeContent(dynamicScope, node, frame);

                assertTrue(INTEROP.hasScopeParent(lexicalScope));
                assertTrue(INTEROP.hasScopeParent(dynamicScope));
                lexicalScope = INTEROP.getScopeParent(lexicalScope);
                dynamicScope = INTEROP.getScopeParent(dynamicScope);
                testScopeContent(lexicalScope, node, null);
                testScopeContent(dynamicScope, node, frame);
                assertFalse(INTEROP.hasScopeParent(lexicalScope));
                assertFalse(INTEROP.hasScopeParent(dynamicScope));
                doTestTopScope(env);
            } catch (UnsupportedMessageException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
        }

        private static void testScopeContent(Object scope, Node node, Frame frame) throws UnsupportedMessageException {
            assertEquals("CustomScope.getName", INTEROP.asString(INTEROP.toDisplayString(scope)));
            assertTrue(INTEROP.hasSourceLocation(scope));
            assertEquals(node.getSourceSection(), INTEROP.getSourceLocation(scope));

            try {
                if (frame == null) {
                    assertEquals("V1", InteropLibrary.getUncached().readMember(scope, "value"));
                } else {
                    assertEquals("V1V2V3", InteropLibrary.getUncached().readMember(scope, "value"));
                }
            } catch (InteropException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
        }

        private static void doTestTopScope(TruffleInstrument.Env env) throws UnsupportedMessageException {
            Object scope = env.getScope(env.getLanguages().get("test-custom-variables-scope-language"));
            assertTrue(INTEROP.isScope(scope));
            assertFalse(INTEROP.hasScopeParent(scope));
            assertEquals("TopCustomScope", INTEROP.asString(INTEROP.toDisplayString(scope)));
            assertFalse(INTEROP.hasSourceLocation(scope));
            assertTrue(INTEROP.hasMembers(scope));
        }

    }

    @ExportLibrary(InteropLibrary.class)
    static final class TestObject implements TruffleObject {

        private static final Object MEMBERS = new Members();

        private final Node node;
        final String value;

        TestObject(Node node, String value) {
            this.node = node;
            this.value = value;
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        boolean hasLanguage() {
            return true;
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        Class<? extends TruffleLanguage<?>> getLanguage() {
            return CustomScopeLanguage.class;
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        boolean isScope() {
            return true;
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        boolean hasMembers() {
            return true;
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        Object getMembers(@SuppressWarnings("unused") boolean includeInternal) {
            return MEMBERS;
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        boolean isMemberReadable(String member) {
            return "value".equals(member);
        }

        @ExportMessage
        Object readMember(String member) throws UnknownIdentifierException {
            if ("value".equals(member)) {
                return value;
            } else {
                throw UnknownIdentifierException.create(member);
            }
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        boolean hasSourceLocation() {
            return true;
        }

        @ExportMessage
        @TruffleBoundary
        SourceSection getSourceLocation() {
            return node.getSourceSection();
        }

        @ExportMessage
        boolean hasScopeParent() {
            return findParent() != null;
        }

        @ExportMessage
        Object getScopeParent() throws UnsupportedMessageException {
            Node parent = findParent();
            if (parent != null) {
                return new TestObject(parent, value);
            } else {
                throw UnsupportedMessageException.create();
            }
        }

        private Node findParent() {
            Node parent = node.getParent();
            if (parent != null && parent instanceof InstrumentableNode.WrapperNode) {
                parent = parent.getParent();
            }
            if (parent != null && !(parent instanceof RootNode)) {
                return parent;
            } else {
                return null;
            }
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        Object toDisplayString(@SuppressWarnings("unused") boolean allowSideEffects) {
            return "CustomScope.getName";
        }

        @ExportLibrary(InteropLibrary.class)
        static final class Members implements TruffleObject {

            @ExportMessage
            @SuppressWarnings("static-method")
            boolean hasArrayElements() {
                return true;
            }

            @ExportMessage
            @SuppressWarnings("static-method")
            long getArraySize() {
                return 1;
            }

            @ExportMessage
            @SuppressWarnings("static-method")
            boolean isArrayElementReadable(long index) {
                return index == 0;
            }

            @ExportMessage
            @SuppressWarnings("static-method")
            Object readArrayElement(long index) throws InvalidArrayIndexException {
                if (index == 0) {
                    return "value";
                }
                throw InvalidArrayIndexException.create(index);
            }

        }
    }

}
