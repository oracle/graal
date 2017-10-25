/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.instrumentation.test;

import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.Scope;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.EventContext;
import com.oracle.truffle.api.instrumentation.ExecutionEventListener;
import com.oracle.truffle.api.instrumentation.Instrumentable;
import com.oracle.truffle.api.instrumentation.ProvidedTags;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.java.JavaInterop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

/**
 * Test of {@link Scope}.
 */
public class VariablesScopeTest extends AbstractInstrumentationTest {

    @Test
    public void testDefaultScope() throws Throwable {
        assureEnabled(engine.getInstruments().get("testVariablesScopeInstrument"));
        TestScopeInstrument.INSTANCE.setTester(new DefaultScopeTester());
        run("ROOT(DEFINE(\ntestFunction,ROOT(\nVARIABLE(a, 10),\nVARIABLE(b, 20),\nSTATEMENT)),\nCALL(testFunction))");
        TestScopeInstrument.INSTANCE.checkForFailure();
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
            env.getInstrumenter().attachListener(SourceSectionFilter.ANY, new ExecutionEventListener() {
                @Override
                public void onEnter(EventContext context, VirtualFrame frame) {
                    scopeTested = true;
                    try {
                        tester.doTestScope(env, context.getInstrumentedNode(), frame);
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
            void doTestScope(TruffleInstrument.Env env, Node node, VirtualFrame frame) throws Exception;
        }
    }

    private static class DefaultScopeTester implements TestScopeInstrument.Tester {

        @SuppressWarnings("rawtypes")
        public void doTestScope(TruffleInstrument.Env env, Node node, VirtualFrame frame) throws Exception {
            Iterable<Scope> lscopes = env.findLocalScopes(node, null); // lexical
            Iterable<Scope> dscopes = env.findLocalScopes(node, frame); // dynamic
            assertNotNull(lscopes);
            assertNotNull(dscopes);
            Iterator<Scope> iterator = lscopes.iterator();
            assertTrue(iterator.hasNext());
            Scope lscope = iterator.next();
            assertFalse(iterator.hasNext());
            iterator = dscopes.iterator();
            assertTrue(iterator.hasNext());
            Scope dscope = iterator.next();
            assertFalse(iterator.hasNext());
            int line = node.getSourceSection().getStartLine();
            if (line == 1 || line == 6) {
                assertEquals("Line = " + line + ", function name: ", "", lscope.getName());
            } else {
                assertEquals("Line = " + line + ", function name: ", "testFunction", lscope.getName());

                // Lexical access:
                TruffleObject vars = (TruffleObject) lscope.getVariables();
                Map varsMap = JavaInterop.asJavaObject(Map.class, vars);
                final int numVars = Math.max(line - 3, 0);
                assertEquals("Line = " + line + ", num vars:", numVars, varsMap.size());
                if (numVars >= 1) {
                    assertTrue("Var a: ", varsMap.containsKey("a"));
                    try {
                        varsMap.get("a");
                        fail();
                    } catch (Exception ex) {
                        // variable value can not be read in the static access
                    }
                }
                if (numVars >= 2) {
                    assertTrue("Var b: ", varsMap.containsKey("b"));
                    try {
                        varsMap.get("b");
                        fail();
                    } catch (Exception ex) {
                        // variable value can not be read in the static access
                    }
                }

                // Dynamic access:
                vars = (TruffleObject) dscope.getVariables();
                varsMap = JavaInterop.asJavaObject(Map.class, vars);
                assertEquals("Line = " + line + ", num vars:", numVars, varsMap.size());
                if (numVars >= 1) {
                    assertTrue("Var a: ", varsMap.containsKey("a"));
                    assertEquals("Var a: ", 10, varsMap.get("a"));
                }
                if (numVars >= 2) {
                    assertTrue("Var b: ", varsMap.containsKey("b"));
                    assertEquals("Var b: ", 20, varsMap.get("b"));
                }
            }
            if (line == 6) {
                doTestTopScope(env);
            }
        }

        private static void doTestTopScope(TruffleInstrument.Env env) throws UnsupportedMessageException, UnknownIdentifierException {
            Iterable<Scope> topScopes = env.findTopScopes(InstrumentationTestLanguage.ID);
            Iterator<Scope> iterator = topScopes.iterator();
            assertTrue(iterator.hasNext());
            Scope scope = iterator.next();
            assertEquals("global", scope.getName());
            assertNull(scope.getNode());
            assertNull(scope.getArguments());
            TruffleObject variables = (TruffleObject) scope.getVariables();
            TruffleObject keys = ForeignAccess.sendKeys(Message.KEYS.createNode(), variables);
            assertNotNull(keys);
            Number size = (Number) ForeignAccess.sendGetSize(Message.GET_SIZE.createNode(), keys);
            assertEquals(1, size.intValue());
            String functionName = (String) ForeignAccess.sendRead(Message.READ.createNode(), keys, 0);
            assertEquals("testFunction", functionName);
            TruffleObject function = (TruffleObject) ForeignAccess.sendRead(Message.READ.createNode(), variables, functionName);
            assertTrue(ForeignAccess.sendIsExecutable(Message.IS_EXECUTABLE.createNode(), function));
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

    @TruffleLanguage.Registration(name = "", version = "", id = "test-custom-variables-scope-language", mimeType = "x-testCustomVariablesScope")
    @ProvidedTags({StandardTags.StatementTag.class})
    public static class CustomScopeLanguage extends TruffleLanguage<Object> {

        @Override
        protected Object createContext(Env env) {
            return new Object();
        }

        @Override
        protected Object getLanguageGlobal(Object context) {
            return null;
        }

        @Override
        protected boolean isObjectOfLanguage(Object object) {
            return true;
        }

        @Override
        protected CallTarget parse(ParsingRequest request) throws Exception {
            return Truffle.getRuntime().createCallTarget(new CustomRoot(this));
        }

        @Override
        public Iterable<Scope> findLocalScopes(Object context, Node node, Frame frame) {
            return new Iterable<Scope>() {
                @Override
                public Iterator<Scope> iterator() {
                    return new Iterator<Scope>() {
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
                        public Scope next() {
                            if (!hasNext()) {
                                throw new NoSuchElementException();
                            }
                            Scope scope = Scope.newBuilder(next.getName(), next.getVariables(frame)).node(next.getNode()).arguments(next.getArguments(frame)).build();
                            previous = next;
                            next = null;
                            return scope;
                        }
                    };
                }
            };
        }

        @Override
        protected Iterable<Scope> findTopScopes(Object context) {
            return Collections.singleton(
                            Scope.newBuilder("TopCustomScope", JavaInterop.asTruffleObject(new TopScopeJavaObject())).arguments(JavaInterop.asTruffleObject(new double[]{11.0, 22.0})).build());
        }

        public static final class TopScopeJavaObject {
            public long l = 42;
            public String s = "top";
        }

        public static class CustomRoot extends RootNode {

            @Child private CustomScopeNode scopeNode = new CustomScopeNode();

            public CustomRoot(TruffleLanguage<?> language) {
                super(language);
            }

            @Override
            public Object execute(VirtualFrame frame) {
                return scopeNode.execute(frame);
            }

            @Override
            protected boolean isInstrumentable() {
                return true;
            }
        }

        @Instrumentable(factory = CustomScopeNodeWrapper.class)
        public static class CustomScopeNode extends Node {

            public CustomScopeNode() {
            }

            @SuppressWarnings("unused")
            public Object execute(VirtualFrame frame) {
                return 1;
            }

            @Override
            public SourceSection getSourceSection() {
                return Source.newBuilder("test").name("unknown").mimeType("x-testCustomVariablesScope").build().createSection(1);
            }

            @Override
            protected boolean isTaggedWith(Class<?> tag) {
                return StandardTags.StatementTag.class.equals(tag);
            }
        }
    }

    private static class CustomScope {

        // Checkstyle: stop
        static CustomScope LAST_INSTANCE;
        static int NUM_INSTANCES = 0;
        // Checkstyle: resume

        private final Node node;

        CustomScope(Node node) {
            this.node = node;
            LAST_INSTANCE = this;
            NUM_INSTANCES++;
        }

        protected String getName() {
            return "CustomScope.getName";
        }

        protected Node getNode() {
            return node;
        }

        protected Object getVariables(Frame f) {
            if (f == null) {
                return JavaInterop.asTruffleObject("V1");
            } else {
                return JavaInterop.asTruffleObject("V1V2V3");
            }
        }

        protected Object getArguments(Frame f) {
            if (f == null) {
                return JavaInterop.asTruffleObject("A1");
            } else {
                return JavaInterop.asTruffleObject("A1A2A3");
            }
        }

        protected CustomScope findParent() {
            Node parent = node.getParent();
            if (parent != null) {
                return new CustomScope(parent);
            } else {
                return null;
            }
        }
    }

    private static class CustomScopeTester implements TestScopeInstrument.Tester {

        @Override
        public void doTestScope(TruffleInstrument.Env env, Node node, VirtualFrame frame) {
            assertNull(CustomScope.LAST_INSTANCE);
            assertEquals(0, CustomScope.NUM_INSTANCES);
            Iterable<Scope> lscopes = env.findLocalScopes(node, null);
            Iterator<Scope> literator = lscopes.iterator();
            assertNotNull(CustomScope.LAST_INSTANCE);
            assertEquals(1, CustomScope.NUM_INSTANCES);
            assertTrue(literator.hasNext());
            Scope lscope = literator.next();
            assertEquals(1, CustomScope.NUM_INSTANCES);
            testScopeContent(lscope, node, null);

            Iterable<Scope> dscopes = env.findLocalScopes(node, frame);
            Iterator<Scope> diterator = dscopes.iterator();
            assertEquals(2, CustomScope.NUM_INSTANCES);
            Scope dscope = diterator.next();
            assertEquals(2, CustomScope.NUM_INSTANCES);
            testScopeContent(dscope, node, frame);

            assertEquals(2, CustomScope.NUM_INSTANCES);
            assertTrue(literator.hasNext());
            assertEquals(3, CustomScope.NUM_INSTANCES);
            lscope = literator.next();
            assertEquals(3, CustomScope.NUM_INSTANCES);
            testScopeContent(lscope, node.getParent(), null);

            assertTrue(diterator.hasNext());
            assertEquals(4, CustomScope.NUM_INSTANCES);
            dscope = diterator.next();
            assertEquals(4, CustomScope.NUM_INSTANCES);
            testScopeContent(dscope, node.getParent(), frame);

            assertEquals(4, CustomScope.NUM_INSTANCES);
            assertTrue(literator.hasNext());
            assertEquals(5, CustomScope.NUM_INSTANCES);
            lscope = literator.next();
            assertEquals(5, CustomScope.NUM_INSTANCES);

            assertFalse(literator.hasNext());
            try {
                literator.next();
                fail();
            } catch (Exception ex) {
                // next should fail
            }
            assertEquals(5, CustomScope.NUM_INSTANCES);
            doTestTopScope(env);
        }

        private static void testScopeContent(Scope scope, Node node, Frame frame) {
            assertEquals("CustomScope.getName", scope.getName());

            assertEquals(node, scope.getNode());

            if (frame == null) {
                assertEquals("V1", JavaInterop.asJavaObject((TruffleObject) scope.getVariables()));
            } else {
                assertEquals("V1V2V3", JavaInterop.asJavaObject((TruffleObject) scope.getVariables()));
            }

            if (frame == null) {
                assertEquals("A1", JavaInterop.asJavaObject((TruffleObject) scope.getArguments()));
            } else {
                assertEquals("A1A2A3", JavaInterop.asJavaObject((TruffleObject) scope.getArguments()));
            }
        }

        private static void doTestTopScope(TruffleInstrument.Env env) {
            Iterable<Scope> topScopes = env.findTopScopes("test-custom-variables-scope-language");
            Iterator<Scope> iterator = topScopes.iterator();
            assertTrue(iterator.hasNext());
            Scope topScope = iterator.next();
            assertFalse(iterator.hasNext());

            assertEquals("TopCustomScope", topScope.getName());
            assertNull(topScope.getNode());
            TruffleObject arguments = (TruffleObject) topScope.getArguments();
            TruffleObject variables = (TruffleObject) topScope.getVariables();
            assertTrue(JavaInterop.isJavaObject(arguments));
            assertTrue(JavaInterop.isJavaObject(variables));
        }

    }

}
