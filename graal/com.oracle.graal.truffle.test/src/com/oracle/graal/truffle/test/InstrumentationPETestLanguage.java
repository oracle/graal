/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.graal.truffle.test;

import java.io.IOException;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleRuntime;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrument.ASTProber;
import com.oracle.truffle.api.instrument.EventHandlerNode;
import com.oracle.truffle.api.instrument.Instrumenter;
import com.oracle.truffle.api.instrument.Probe;
import com.oracle.truffle.api.instrument.SyntaxTag;
import com.oracle.truffle.api.instrument.Visualizer;
import com.oracle.truffle.api.instrument.WrapperNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeCost;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.nodes.NodeVisitor;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;

@TruffleLanguage.Registration(name = "instrumentationPETestLanguage", version = "0", mimeType = "text/x-instPETest")
public final class InstrumentationPETestLanguage extends TruffleLanguage<Object> {

    public static final InstrumentationPETestLanguage INSTANCE = new InstrumentationPETestLanguage();

    enum InstrumentTestTag implements SyntaxTag {

        ADD_TAG("addition", "test language addition node"),

        VALUE_TAG("value", "test language value node");

        private final String name;
        private final String description;

        InstrumentTestTag(String name, String description) {
            this.name = name;
            this.description = description;
        }

        public String getName() {
            return name;
        }

        public String getDescription() {
            return description;
        }
    }

    private InstrumentationPETestLanguage() {
    }

    @Override
    protected CallTarget parse(Source code, Node context, String... argumentNames) throws IOException {
        final TestValueNode valueNode = new TestValueNode(42);
        final TestRootNode rootNode = new TestRootNode(null, valueNode);
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

    @SuppressWarnings("deprecation")
    @Override
    protected Visualizer getVisualizer() {
        return null;
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

    @Override
    protected Object evalInContext(Source source, Node node, MaterializedFrame mFrame) throws IOException {
        return null;
    }

    @Override
    protected Object createContext(Env env) {
        return null;
    }

    static final class TestASTProber implements ASTProber {

        public void probeAST(final Instrumenter instrumenter, RootNode startNode) {
            startNode.accept(new NodeVisitor() {

                @Override
                public boolean visit(Node node) {
                    if (node instanceof TestLanguageNode) {

                        final TestLanguageNode testNode = (TestLanguageNode) node;

                        if (node instanceof TestValueNode) {
                            instrumenter.probe(testNode).tagAs(InstrumentTestTag.VALUE_TAG, null);

                        } else if (node instanceof TestAdditionNode) {
                            instrumenter.probe(testNode).tagAs(InstrumentTestTag.ADD_TAG, null);

                        }
                    }
                    return true;
                }
            });
        }
    }

    abstract static class TestLanguageNode extends Node {
        public abstract int execute(VirtualFrame vFrame);

    }

    @NodeInfo(cost = NodeCost.NONE)
    static class TestLanguageWrapperNode extends TestLanguageNode implements WrapperNode {
        @Child private TestLanguageNode child;
        @Child private EventHandlerNode eventHandlerNode;

        TestLanguageWrapperNode(TestLanguageNode child) {
            assert !(child instanceof TestLanguageWrapperNode);
            this.child = child;
        }

        @Override
        public String instrumentationInfo() {
            return "Wrapper node for testing";
        }

        @Override
        public void insertEventHandlerNode(EventHandlerNode eventHandler) {
            this.eventHandlerNode = eventHandler;
        }

        @Override
        public Probe getProbe() {
            return eventHandlerNode.getProbe();
        }

        @Override
        public Node getChild() {
            return child;
        }

        @Override
        public int execute(VirtualFrame vFrame) {
            eventHandlerNode.enter(child, vFrame);
            int result;
            try {
                result = child.execute(vFrame);
                eventHandlerNode.returnValue(child, vFrame, result);
            } catch (Exception e) {
                eventHandlerNode.returnExceptional(child, vFrame, e);
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

        TestValueNode(int value) {
            this.value = value;
        }

        @Override
        public int execute(VirtualFrame vFrame) {
            return value;
        }
    }

    /**
     * A node for our test language that adds up two {@link TestValueNode}s.
     */
    static class TestAdditionNode extends TestLanguageNode {
        @Child private TestLanguageNode leftChild;
        @Child private TestLanguageNode rightChild;

        TestAdditionNode(TestValueNode leftChild, TestValueNode rightChild) {
            this.leftChild = insert(leftChild);
            this.rightChild = insert(rightChild);
        }

        @Override
        public int execute(VirtualFrame vFrame) {
            return leftChild.execute(vFrame) + rightChild.execute(vFrame);
        }
    }

    /**
     * Truffle requires that all guest languages to have a {@link RootNode} which sits atop any AST
     * of the guest language. This is necessary since creating a {@link CallTarget} is how Truffle
     * completes an AST. The root nodes serves as our entry point into a program.
     */
    static class TestRootNode extends RootNode {
        private final String name;
        @Child private TestLanguageNode body;

        /**
         * This constructor emulates the global machinery that applies registered probers to every
         * newly created AST. Global registry is not used, since that would interfere with other
         * tests run in the same environment.
         */
        TestRootNode(String name, TestLanguageNode body) {
            super(InstrumentationPETestLanguage.class, null, null);
            this.name = name;
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
        public String toString() {
            return name;
        }

        /** for testing. */
        public TestLanguageNode getBody() {
            return body;
        }
    }

}
