/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.tools.test;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleRuntime;
import com.oracle.truffle.api.debug.DebugSupportProvider;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrument.ASTProber;
import com.oracle.truffle.api.instrument.AdvancedInstrumentResultListener;
import com.oracle.truffle.api.instrument.AdvancedInstrumentRootFactory;
import com.oracle.truffle.api.instrument.Instrumenter;
import com.oracle.truffle.api.instrument.KillException;
import com.oracle.truffle.api.instrument.Probe;
import com.oracle.truffle.api.instrument.ProbeNode;
import com.oracle.truffle.api.instrument.ProbeNode.WrapperNode;
import com.oracle.truffle.api.instrument.SyntaxTag;
import com.oracle.truffle.api.instrument.ToolSupportProvider;
import com.oracle.truffle.api.instrument.Visualizer;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeCost;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.nodes.NodeVisitor;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;

@TruffleLanguage.Registration(name = "toolTestLanguage", version = "0", mimeType = "text/x-toolTest")
public final class ToolTestingLanguage extends TruffleLanguage<Object> {

    public static final ToolTestingLanguage INSTANCE = new ToolTestingLanguage();

    static final SyntaxTag ADD_TAG = new SyntaxTag() {

        @Override
        public String name() {
            return "Addition";
        }

        @Override
        public String getDescription() {
            return "Test Language Addition Node";
        }
    };

    static final SyntaxTag VALUE_TAG = new SyntaxTag() {

        @Override
        public String name() {
            return "Value";
        }

        @Override
        public String getDescription() {
            return "Test Language Value Node";
        }
    };

    private final ASTProber prober = new TestASTProber();

    private ToolTestingLanguage() {
    }

    @Override
    protected CallTarget parse(Source code, Node context, String... argumentNames) throws IOException {
        final TestValueNode leftValueNode = new TestValueNode(6);
        final TestValueNode rightValueNode = new TestValueNode(7);
        final TestAdditionNode addNode = new TestAdditionNode(leftValueNode, rightValueNode);
        final InstrumentationTestRootNode rootNode = new InstrumentationTestRootNode(addNode);
        final TruffleRuntime runtime = Truffle.getRuntime();
        final CallTarget callTarget = runtime.createCallTarget(rootNode);
        return callTarget;
    }

    @Override
    protected Object findExportedSymbol(Object context, String globalName, boolean onlyExplicit) {
        return null;
    }

    @Override
    protected Object getLanguageGlobal(Object context) {
        return null;
    }

    @Override
    protected boolean isObjectOfLanguage(Object object) {
        return false;
    }

    @Override
    protected Visualizer getVisualizer() {
        return null;
    }

    @Override
    protected ASTProber getDefaultASTProber() {
        return prober;
    }

    @Override
    protected boolean isInstrumentable(Node node) {
        return node instanceof TestAdditionNode || node instanceof TestValueNode;
    }

    @Override
    protected WrapperNode createWrapperNode(Node node) {
        if (isInstrumentable(node)) {
            return new TestLanguageWrapperNode((TestLanguageNode) node);
        }
        return null;
    }

    @SuppressWarnings("deprecation")
    @Override
    protected void enableASTProbing(ASTProber astProber) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected Object evalInContext(Source source, Node node, MaterializedFrame mFrame) throws IOException {
        return null;
    }

    @Override
    protected AdvancedInstrumentRootFactory createAdvancedInstrumentRootFactory(String expr, AdvancedInstrumentResultListener resultListener) throws IOException {
        return null;
    }

    @SuppressWarnings("deprecation")
    @Override
    protected ToolSupportProvider getToolSupport() {
        throw new UnsupportedOperationException();
    }

    @SuppressWarnings("deprecation")
    @Override
    protected DebugSupportProvider getDebugSupport() {
        throw new UnsupportedOperationException();
    }

    @Override
    protected Object createContext(Env env) {
        return null;
    }

    static final class TestASTProber implements ASTProber {

        public void probeAST(final Instrumenter instrumenter, Node startNode) {
            startNode.accept(new NodeVisitor() {

                @Override
                public boolean visit(Node node) {
                    if (node instanceof TestLanguageNode) {

                        final TestLanguageNode testNode = (TestLanguageNode) node;

                        if (node instanceof TestValueNode) {
                            instrumenter.probe(testNode).tagAs(VALUE_TAG, null);

                        } else if (node instanceof TestAdditionNode) {
                            instrumenter.probe(testNode).tagAs(ADD_TAG, null);

                        }
                    }
                    return true;
                }
            });
        }
    }

    abstract static class TestLanguageNode extends Node {
        public abstract Object execute(VirtualFrame vFrame);

        @SuppressWarnings("deprecation")
        @Override
        public boolean isInstrumentable() {
            throw new UnsupportedOperationException();
        }

        @SuppressWarnings("deprecation")
        @Override
        public WrapperNode createWrapperNode() {
            throw new UnsupportedOperationException();
        }
    }

    @NodeInfo(cost = NodeCost.NONE)
    static class TestLanguageWrapperNode extends TestLanguageNode implements WrapperNode {
        @Child private TestLanguageNode child;
        @Child private ProbeNode probeNode;

        public TestLanguageWrapperNode(TestLanguageNode child) {
            assert !(child instanceof TestLanguageWrapperNode);
            this.child = child;
        }

        @Override
        public String instrumentationInfo() {
            return "Wrapper node for testing";
        }

        @Override
        public boolean isInstrumentable() {
            return false;
        }

        @Override
        public void insertProbe(ProbeNode newProbeNode) {
            this.probeNode = newProbeNode;
        }

        @Override
        public Probe getProbe() {
            return probeNode.getProbe();
        }

        @Override
        public Node getChild() {
            return child;
        }

        @Override
        public Object execute(VirtualFrame vFrame) {
            probeNode.enter(child, vFrame);
            Object result;
            try {
                result = child.execute(vFrame);
                probeNode.returnValue(child, vFrame, result);
            } catch (KillException e) {
                throw (e);
            } catch (Exception e) {
                probeNode.returnExceptional(child, vFrame, e);
                throw (e);
            }
            return result;
        }
    }

    /**
     * A simple node for our test language to store a value.
     */
    static class TestValueNode extends TestLanguageNode {
        private final int value;

        public TestValueNode(int value) {
            this.value = value;
        }

        @Override
        public Object execute(VirtualFrame vFrame) {
            return new Integer(this.value);
        }
    }

    /**
     * A node for our test language that adds up two {@link TestValueNode}s.
     */
    static class TestAdditionNode extends TestLanguageNode {
        @Child private TestLanguageNode leftChild;
        @Child private TestLanguageNode rightChild;

        public TestAdditionNode(TestValueNode leftChild, TestValueNode rightChild) {
            this.leftChild = insert(leftChild);
            this.rightChild = insert(rightChild);
        }

        @Override
        public Object execute(VirtualFrame vFrame) {
            return new Integer(((Integer) leftChild.execute(vFrame)).intValue() + ((Integer) rightChild.execute(vFrame)).intValue());
        }
    }

    /**
     * Truffle requires that all guest languages to have a {@link RootNode} which sits atop any AST
     * of the guest language. This is necessary since creating a {@link CallTarget} is how Truffle
     * completes an AST. The root nodes serves as our entry point into a program.
     */
    static class InstrumentationTestRootNode extends RootNode {
        @Child private TestLanguageNode body;

        /**
         * This constructor emulates the global machinery that applies registered probers to every
         * newly created AST. Global registry is not used, since that would interfere with other
         * tests run in the same environment.
         */
        public InstrumentationTestRootNode(TestLanguageNode body) {
            super(ToolTestingLanguage.class, null, null);
            this.body = body;
        }

        @Override
        public Object execute(VirtualFrame vFrame) {
            return body.execute(vFrame);
        }

        @Override
        public boolean isCloningAllowed() {
            return true;
        }

        @Override
        public void applyInstrumentation() {
            super.applyInstrumentation(body);
        }
    }

    /**
     * Truffle requires that all guest languages to have a {@link RootNode} which sits atop any AST
     * of the guest language. This is necessary since creating a {@link CallTarget} is how Truffle
     * completes an AST. The root nodes serves as our entry point into a program.
     */
    static class TestRootNode extends RootNode {
        @Child private TestLanguageNode body;

        final Instrumenter instrumenter;

        /**
         * This constructor emulates the global machinery that applies registered probers to every
         * newly created AST. Global registry is not used, since that would interfere with other
         * tests run in the same environment.
         */
        public TestRootNode(TestLanguageNode body, Instrumenter instrumenter) {
            super(ToolTestingLanguage.class, null, null);
            this.instrumenter = instrumenter;
            this.body = body;
        }

        @Override
        public Object execute(VirtualFrame vFrame) {
            return body.execute(vFrame);
        }

        @Override
        public boolean isCloningAllowed() {
            return true;
        }

        @Override
        public void applyInstrumentation() {
            Method method;
            try {
                method = Instrumenter.class.getDeclaredMethod("applyInstrumentation", Node.class);
                method.setAccessible(true);
                method.invoke(instrumenter, body);
            } catch (NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
                throw new RuntimeException("InstrumentationTestNodes");
            }
        }
    }

}
