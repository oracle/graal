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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Collections;
import java.util.Iterator;
import java.util.NoSuchElementException;

import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.frame.Frame;
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

/**
 * Test of {@link com.oracle.truffle.api.Scope}.
 */
@SuppressWarnings("deprecation")
public class VariablesScopeLegacyTest extends AbstractInstrumentationTest {

    private static final InteropLibrary INTEROP = InteropLibrary.getUncached();

    @Test
    public void testDefaultScope() throws Throwable {
        assureEnabled(engine.getInstruments().get("testVariablesScopeLegacyInstrument"));
        TestScopeLegacyInstrument.INSTANCE.setTester(new DefaultScopeTester());
        run("ROOT(DEFINE(\ntestFunction,ROOT(\nVARIABLE(a, 10),\nVARIABLE(b, 20),\nSTATEMENT)),\nCALL(testFunction))");
        TestScopeLegacyInstrument.INSTANCE.checkForFailure();
    }

    @TruffleInstrument.Registration(id = "testVariablesScopeLegacyInstrument", services = Object.class)
    public static class TestScopeLegacyInstrument extends TruffleInstrument {

        static TestScopeLegacyInstrument INSTANCE;

        private Tester tester;
        private boolean scopeTested;
        private Throwable failure;

        @Override
        protected void onCreate(TruffleInstrument.Env env) {
            INSTANCE = this;
            env.getInstrumenter().attachExecutionEventListener(SourceSectionFilter.newBuilder().tagIs(StandardTags.StatementTag.class).build(), new ExecutionEventListener() {
                @Override
                public void onEnter(EventContext context, VirtualFrame frame) {
                    scopeTested = true;
                    try {
                        tester.doTestScope(env, context.getInstrumentedNode(), frame);
                        if (tester.isTestOnRoot()) {
                            tester.doTestScope(env, context.getInstrumentedNode().getRootNode(), frame);
                        }
                    } catch (Throwable t) {
                        failure = t;
                    }
                }

                @Override
                public void onReturnValue(EventContext context, VirtualFrame frame, Object result) {
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

            boolean isTestOnRoot();

            void doTestScope(TruffleInstrument.Env env, Node node, VirtualFrame frame) throws Exception;
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

    private static class DefaultScopeTester implements TestScopeLegacyInstrument.Tester {

        @Override
        public boolean isTestOnRoot() {
            return false;
        }

        public void doTestScope(TruffleInstrument.Env env, Node node, VirtualFrame frame) throws Exception {
            Iterable<com.oracle.truffle.api.Scope> lscopes = env.findLocalScopes(node, null); // lexical
            Iterable<com.oracle.truffle.api.Scope> dscopes = env.findLocalScopes(node, frame); // dynamic
            assertNotNull(lscopes);
            assertNotNull(dscopes);
            Iterator<com.oracle.truffle.api.Scope> iterator = lscopes.iterator();
            assertTrue(iterator.hasNext());
            com.oracle.truffle.api.Scope lscope = iterator.next();
            assertFalse(iterator.hasNext());
            iterator = dscopes.iterator();
            assertTrue(iterator.hasNext());
            com.oracle.truffle.api.Scope dscope = iterator.next();
            assertFalse(iterator.hasNext());
            int line = node.getSourceSection().getStartLine();
            if (line == 1 || line == 6) {
                assertEquals("Line = " + line + ", function name: ", "", lscope.getName());
            } else {
                assertEquals("Line = " + line + ", function name: ", "testFunction", lscope.getName());

                // Lexical access:
                TruffleObject vars = (TruffleObject) lscope.getVariables();
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
                vars = (TruffleObject) dscope.getVariables();
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
            Iterable<com.oracle.truffle.api.Scope> topScopes = env.findTopScopes(InstrumentationTestLanguage.ID);
            Iterator<com.oracle.truffle.api.Scope> iterator = topScopes.iterator();
            assertTrue(iterator.hasNext());
            com.oracle.truffle.api.Scope scope = iterator.next();
            assertEquals("global", scope.getName());
            assertNull(scope.getNode());
            assertNull(scope.getArguments());
            Object variables = scope.getVariables();
            Object keys = INTEROP.getMembers(variables);
            assertNotNull(keys);
            Number size = INTEROP.getArraySize(keys);
            assertEquals(1, size.intValue());
            String functionName = (String) INTEROP.readArrayElement(keys, 0);
            assertEquals("testFunction", functionName);
            Object function = INTEROP.readMember(variables, functionName);
            assertTrue(INTEROP.isExecutable(function));
        }
    }

    @Test
    public void testSPIScopeCalls() throws Throwable {
        org.graalvm.polyglot.Source source = org.graalvm.polyglot.Source.create("test-custom-variables-scope-legacy-language", "test");
        assureEnabled(engine.getInstruments().get("testVariablesScopeLegacyInstrument"));
        TestScopeLegacyInstrument.INSTANCE.setTester(new CustomScopeTester());
        context.eval(source);
        TestScopeLegacyInstrument.INSTANCE.checkForFailure();
        TestScopeLegacyInstrument.INSTANCE.setTester(new CustomScopeLibraryTester());
        context.eval(source);
        TestScopeLegacyInstrument.INSTANCE.checkForFailure();
    }

    @TruffleLanguage.Registration(name = "", id = "test-custom-variables-scope-legacy-language")
    @ProvidedTags({StandardTags.StatementTag.class, StandardTags.RootTag.class})
    public static class CustomScopeLegacyLanguage extends TruffleLanguage<Env> {

        @Override
        protected Env createContext(Env env) {
            return env;
        }

        @Override
        protected CallTarget parse(ParsingRequest request) throws Exception {
            return Truffle.getRuntime().createCallTarget(new CustomRoot(this));
        }

        @Override
        public Iterable<com.oracle.truffle.api.Scope> findLocalScopes(Env context, Node node, Frame frame) {
            return new Iterable<com.oracle.truffle.api.Scope>() {
                @Override
                public Iterator<com.oracle.truffle.api.Scope> iterator() {
                    return new Iterator<com.oracle.truffle.api.Scope>() {
                        CustomScope previous = null;
                        CustomScope next = new CustomScope(node);

                        @Override
                        public boolean hasNext() {
                            if (next == null) {
                                next = previous.findParent();
                            }
                            return next != null;
                        }

                        @Override
                        public com.oracle.truffle.api.Scope next() {
                            if (!hasNext()) {
                                throw new NoSuchElementException();
                            }
                            com.oracle.truffle.api.Scope.Builder scopeBuilder = com.oracle.truffle.api.Scope.newBuilder(next.getName(), next.getVariables(frame));
                            scopeBuilder.node(next.getNode());
                            scopeBuilder.receiver(next.getReceiverName(), next.getReceiverValue());
                            scopeBuilder.arguments(next.getArguments(frame));
                            com.oracle.truffle.api.Scope scope = scopeBuilder.build();
                            previous = next;
                            next = null;
                            return scope;
                        }
                    };
                }
            };
        }

        @Override
        protected Iterable<com.oracle.truffle.api.Scope> findTopScopes(Env context) {
            return Collections.singleton(
                            com.oracle.truffle.api.Scope.newBuilder("TopCustomScope", context.asGuestValue(new TopScopeJavaObject())).build());
        }

        public static final class TopScopeJavaObject {
            public long l = 42;
            public String s = "top";
        }

        public static class CustomRoot extends RootNode {

            @Child private CustomRootBlockLegacyNode scopeNode = new CustomRootBlockLegacyNode();

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
        public static class CustomRootBlockLegacyNode extends Node implements InstrumentableNode {

            @Child private CustomScopeLegacyNode scopeNode = new CustomScopeLegacyNode();

            public CustomRootBlockLegacyNode() {
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
                return new CustomRootBlockLegacyNodeWrapper(this, probe);
            }

            public boolean isInstrumentable() {
                return true;
            }

            public boolean hasTag(Class<? extends Tag> tag) {
                return StandardTags.RootTag.class.equals(tag);
            }

        }

        @GenerateWrapper
        public static class CustomScopeLegacyNode extends Node implements InstrumentableNode {

            public CustomScopeLegacyNode() {
            }

            @SuppressWarnings("unused")
            public Object execute(VirtualFrame frame) {
                return 1;
            }

            @Override
            public SourceSection getSourceSection() {
                return Source.newBuilder("test-custom-variables-scope-legacy-language", "test", "unknown").build().createSection(1);
            }

            public WrapperNode createWrapper(ProbeNode probe) {
                return new CustomScopeLegacyNodeWrapper(this, probe);
            }

            public boolean isInstrumentable() {
                return true;
            }

            public boolean hasTag(Class<? extends Tag> tag) {
                return StandardTags.StatementTag.class.equals(tag);
            }

        }
    }

    private static class CustomScope {

        // Checkstyle: stop
        static CustomScope LAST_INSTANCE;
        // Checkstyle: resume

        private final Node node;
        private final boolean hasReceiver;

        CustomScope(Node node) {
            this(node, true);
        }

        private CustomScope(Node node, boolean hasReceiver) {
            this.node = node;
            this.hasReceiver = hasReceiver;
            LAST_INSTANCE = this;
        }

        String getName() {
            return "CustomScope.getName";
        }

        private boolean isAtRoot() {
            return node instanceof RootNode;
        }

        Node getNode() {
            if (isAtRoot()) {
                return node.getRootNode();
            } else {
                return node;
            }
        }

        Object getVariables(Frame f) {
            if (f == null) {
                return new TestLegacyObject("V1");
            } else {
                return new TestLegacyObject("V1V2V3");
            }
        }

        Object getArguments(Frame f) {
            if (f == null) {
                return new TestLegacyObject("A1");
            } else {
                return new TestLegacyObject("A1A2A3");
            }
        }

        String getReceiverName() {
            return "THIS";
        }

        Object getReceiverValue() {
            if (hasReceiver) {
                return "thisValue";
            } else {
                return null;
            }
        }

        CustomScope findParent() {
            Node parent = node.getParent();
            if (parent != null) {
                // The legacy bridge need to work with all nodes, including the RootNode.
                return new CustomScope(parent, false);
            } else {
                return null;
            }
        }
    }

    private static class CustomScopeTester implements TestScopeLegacyInstrument.Tester {

        @Override
        public boolean isTestOnRoot() {
            return true;
        }

        @Override
        public void doTestScope(TruffleInstrument.Env env, Node node, VirtualFrame frame) {
            if (node instanceof RootNode) {
                Iterable<com.oracle.truffle.api.Scope> lscopes = env.findLocalScopes(node, null);
                Iterator<com.oracle.truffle.api.Scope> literator = lscopes.iterator();
                assertTrue(literator.hasNext());
                com.oracle.truffle.api.Scope lscope = literator.next();
                try {
                    assertEquals("V1", InteropLibrary.getUncached().readMember(lscope.getVariables(), "value"));
                } catch (InteropException e) {
                    throw CompilerDirectives.shouldNotReachHere(e);
                }
                assertFalse(literator.hasNext());
                return;
            }

            assertNull(CustomScope.LAST_INSTANCE);
            Iterable<com.oracle.truffle.api.Scope> lscopes = env.findLocalScopes(node, null);
            Iterator<com.oracle.truffle.api.Scope> literator = lscopes.iterator();
            assertNotNull(CustomScope.LAST_INSTANCE);
            assertTrue(literator.hasNext());
            com.oracle.truffle.api.Scope lscope = literator.next();
            testScopeContent(lscope, null, true);

            Iterable<com.oracle.truffle.api.Scope> dscopes = env.findLocalScopes(node, frame);
            Iterator<com.oracle.truffle.api.Scope> diterator = dscopes.iterator();
            com.oracle.truffle.api.Scope dscope = diterator.next();
            testScopeContent(dscope, frame, true);

            assertTrue(literator.hasNext());
            lscope = literator.next();
            testScopeContent(lscope, null, false);

            assertTrue(diterator.hasNext());
            dscope = diterator.next();
            testScopeContent(dscope, frame, false);

            assertTrue(literator.hasNext());
            lscope = literator.next();
            testScopeContent(lscope, null, false);

            assertTrue(literator.hasNext());
            lscope = literator.next();
            testScopeContent(lscope, null, false);

            assertFalse(literator.hasNext());
            try {
                literator.next();
                fail();
            } catch (Exception ex) {
                // next should fail
            }
            doTestTopScope(env);
        }

        private static void testScopeContent(com.oracle.truffle.api.Scope scope, Frame frame, boolean hasReceiver) {
            assertEquals("CustomScope.getName", scope.getName());

            try {
                if (frame == null) {
                    assertEquals("V1", InteropLibrary.getUncached().readMember(scope.getVariables(), "value"));
                } else {
                    assertEquals("V1V2V3", InteropLibrary.getUncached().readMember(scope.getVariables(), "value"));
                }

                if (frame == null) {
                    assertEquals("A1", InteropLibrary.getUncached().readMember(scope.getArguments(), "value"));
                } else {
                    assertEquals("A1A2A3", InteropLibrary.getUncached().readMember(scope.getArguments(), "value"));
                }
                assertEquals("THIS", scope.getReceiverName());
                if (hasReceiver) {
                    assertEquals("thisValue", scope.getReceiver());
                } else {
                    assertNull(scope.getReceiver());
                }
            } catch (InteropException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
        }

        private static void doTestTopScope(TruffleInstrument.Env env) {
            Iterable<com.oracle.truffle.api.Scope> topScopes = env.findTopScopes("test-custom-variables-scope-legacy-language");
            Iterator<com.oracle.truffle.api.Scope> iterator = topScopes.iterator();
            assertTrue(iterator.hasNext());
            com.oracle.truffle.api.Scope topScope = iterator.next();
            assertFalse(iterator.hasNext());

            assertEquals("TopCustomScope", topScope.getName());
            assertNull(topScope.getNode());
            TruffleObject arguments = (TruffleObject) topScope.getArguments();
            TruffleObject variables = (TruffleObject) topScope.getVariables();
            assertNull(arguments);
            assertTrue(INTEROP.hasMembers(variables));
        }

    }

    private static class CustomScopeLibraryTester implements TestScopeLegacyInstrument.Tester {

        @Override
        public boolean isTestOnRoot() {
            return false;
        }

        @Override
        public void doTestScope(TruffleInstrument.Env env, Node node, VirtualFrame frame) {
            NodeLibrary nodeLibrary = NodeLibrary.getUncached(node);
            assertTrue(nodeLibrary.hasScope(node, frame));
            assertTrue(nodeLibrary.hasReceiverMember(node, frame));
            try {
                assertEquals("THIS", nodeLibrary.getReceiverMember(node, frame));
                Object scope = nodeLibrary.getScope(node, frame, true);
                InteropLibrary interop = InteropLibrary.getUncached();
                assertTrue(interop.isScope(scope));
                if (frame == null) {
                    assertEquals("V1", interop.readMember(scope, "value"));
                } else {
                    assertEquals("V1V2V3", interop.readMember(scope, "value"));
                }
                assertEquals("thisValue", interop.readMember(scope, "THIS"));
                assertTrue(interop.hasScopeParent(scope));
            } catch (InteropException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
        }
    }

    @ExportLibrary(InteropLibrary.class)
    static final class TestLegacyObject implements TruffleObject {

        private static final Object MEMBERS = new LegacyMembers();
        final String value;

        TestLegacyObject(String value) {
            this.value = value;
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

        @ExportLibrary(InteropLibrary.class)
        static final class LegacyMembers implements TruffleObject {

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
