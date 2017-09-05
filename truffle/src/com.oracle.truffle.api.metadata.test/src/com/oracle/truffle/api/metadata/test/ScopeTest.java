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
package com.oracle.truffle.api.metadata.test;

import java.util.Iterator;
import java.util.Map;

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
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.EventContext;
import com.oracle.truffle.api.instrumentation.ExecutionEventListener;
import com.oracle.truffle.api.instrumentation.Instrumentable;
import com.oracle.truffle.api.instrumentation.ProvidedTags;
import com.oracle.truffle.api.metadata.Scope;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.instrumentation.test.AbstractInstrumentationTest;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.java.JavaInterop;
import com.oracle.truffle.api.metadata.ScopeProvider;
import com.oracle.truffle.api.metadata.ScopeProvider.AbstractScope;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.SourceSection;
import org.graalvm.polyglot.Source;

/**
 * Test of {@link Scope}.
 */
public class ScopeTest extends AbstractInstrumentationTest {

    @Test
    public void testDefaultScope() throws Throwable {
        assureEnabled(engine.getInstruments().get("testScopeInstrument"));
        TestScopeInstrument.INSTANCE.setTester(new DefaultScopeTester());
        run("ROOT(DEFINE(testFunction,\nROOT(\nVARIABLE(a, 10),\nVARIABLE(b, 20),\nSTATEMENT)),\nCALL(testFunction))");
        TestScopeInstrument.INSTANCE.checkForFailure();
    }

    @TruffleInstrument.Registration(id = "testScopeInstrument", services = Object.class)
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
            void doTestScope(TruffleInstrument.Env env, Node node, VirtualFrame frame);
        }
    }

    private static class DefaultScopeTester implements TestScopeInstrument.Tester {

        @SuppressWarnings("rawtypes")
        public void doTestScope(TruffleInstrument.Env env, Node node, VirtualFrame frame) {
            Iterable<Scope> scopes = Scope.findScopes(env, node, null);
            assertNotNull(scopes);
            Iterator<Scope> iterator = scopes.iterator();
            assertTrue(iterator.hasNext());
            Scope scope = iterator.next();
            assertFalse(iterator.hasNext());
            int line = node.getSourceSection().getStartLine();
            if (line == 1 || line == 6) {
                assertEquals("Line = " + line + ", function name: ", "", scope.getName());
            } else {
                assertEquals("Line = " + line + ", function name: ", "testFunction", scope.getName());

                // Lexical access:
                TruffleObject vars = (TruffleObject) scope.getVariables(null);
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
                vars = (TruffleObject) scope.getVariables(frame);
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
        }
    }

    @Test
    public void testSPIScopeCalls() throws Throwable {
        Source source = Source.create("test-custom-scope-language", "test");
        assureEnabled(engine.getInstruments().get("testScopeInstrument"));
        TestScopeInstrument.INSTANCE.setTester(new CustomScopeTester());
        context.eval(source);
        TestScopeInstrument.INSTANCE.checkForFailure();
    }

    @TruffleLanguage.Registration(id = "test-custom-scope-language", name = "", version = "", mimeType = "x-testCustomScope")
    @ProvidedTags({StandardTags.StatementTag.class})
    public static class CustomScopeLanguage extends TruffleLanguage<Object> implements ScopeProvider<Object> {

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
        public AbstractScope findScope(Object context, Node node, Frame frame) {
            return new CustomScope(node, frame);
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
                return com.oracle.truffle.api.source.Source.newBuilder("test").name("unknown").mimeType("x-testCustomScope").build().createSection(1);
            }

            @Override
            protected boolean isTaggedWith(Class<?> tag) {
                return StandardTags.StatementTag.class.equals(tag);
            }
        }
    }

    private static class CustomScope extends AbstractScope {

        // Checkstyle: stop
        static CustomScope LAST_INSTANCE;
        static int NUM_INSTANCES = 0;
        // Checkstyle: resume

        private final Node node;
        private final Frame frame;

        private int numGetNames = 0;
        private int numGetNodes = 0;
        private int numGetVariables = 0;
        private int numGetArguments = 0;
        private int numFindParents = 0;

        CustomScope(Node node, Frame frame) {
            this.node = node;
            this.frame = frame;
            LAST_INSTANCE = this;
            NUM_INSTANCES++;
        }

        @Override
        protected String getName() {
            numGetNames++;
            return "CustomScope.getName";
        }

        @Override
        protected Node getNode() {
            numGetNodes++;
            return node;
        }

        @Override
        protected Object getVariables(Frame f) {
            numGetVariables++;
            if (f == null) {
                return "V1";
            } else {
                return "V1V2V3";
            }
        }

        @Override
        protected Object getArguments(Frame f) {
            numGetArguments++;
            if (f == null) {
                return "A1";
            } else {
                return "A1A2A3";
            }
        }

        @Override
        protected AbstractScope findParent() {
            numFindParents++;
            Node parent = node.getParent();
            if (parent != null) {
                return new CustomScope(parent, frame);
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
            Iterable<Scope> findScopes = Scope.findScopes(env, node, null);
            assertNotNull(CustomScope.LAST_INSTANCE);
            assertEquals(1, CustomScope.NUM_INSTANCES);
            Iterator<Scope> iterator = findScopes.iterator();
            assertTrue(iterator.hasNext());
            Scope scope = iterator.next();
            assertEquals(1, CustomScope.NUM_INSTANCES);

            testScopeContent(scope, node, frame);

            assertEquals(1, CustomScope.NUM_INSTANCES);
            assertTrue(iterator.hasNext());
            assertEquals(2, CustomScope.NUM_INSTANCES);
            scope = iterator.next();
            assertEquals(2, CustomScope.NUM_INSTANCES);

            testScopeContent(scope, node.getParent(), frame);
            assertEquals(2, CustomScope.NUM_INSTANCES);
            assertTrue(iterator.hasNext());
            assertEquals(3, CustomScope.NUM_INSTANCES);
            scope = iterator.next();
            assertEquals(3, CustomScope.NUM_INSTANCES);

            assertFalse(iterator.hasNext());
            try {
                iterator.next();
                fail();
            } catch (Exception ex) {
                // next should fail
            }
            assertEquals(3, CustomScope.NUM_INSTANCES);
        }

        private static void testScopeContent(Scope scope, Node node, Frame frame) {
            assertEquals(0, CustomScope.LAST_INSTANCE.numGetNames);
            assertEquals("CustomScope.getName", scope.getName());
            assertEquals(1, CustomScope.LAST_INSTANCE.numGetNames);

            assertEquals(0, CustomScope.LAST_INSTANCE.numGetNodes);
            assertEquals(node, scope.getNode());
            assertEquals(1, CustomScope.LAST_INSTANCE.numGetNodes);

            assertEquals(0, CustomScope.LAST_INSTANCE.numGetVariables);
            assertEquals("V1", scope.getVariables(null));
            assertEquals(1, CustomScope.LAST_INSTANCE.numGetVariables);
            assertEquals("V1V2V3", scope.getVariables(frame));
            assertEquals(2, CustomScope.LAST_INSTANCE.numGetVariables);

            assertEquals(0, CustomScope.LAST_INSTANCE.numGetArguments);
            assertEquals("A1", scope.getArguments(null));
            assertEquals(1, CustomScope.LAST_INSTANCE.numGetArguments);
            assertEquals("A1A2A3", scope.getArguments(frame));
            assertEquals(2, CustomScope.LAST_INSTANCE.numGetArguments);
            assertEquals(0, CustomScope.LAST_INSTANCE.numFindParents);
        }
    }

}
