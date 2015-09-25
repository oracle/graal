/*
 * Copyright (c) 2015, 2015, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleRuntime;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrument.ASTProber;
import com.oracle.truffle.api.instrument.AdvancedInstrumentResultListener;
import com.oracle.truffle.api.instrument.AdvancedInstrumentRootFactory;
import com.oracle.truffle.api.instrument.EventHandlerNode;
import com.oracle.truffle.api.instrument.Instrumenter;
import com.oracle.truffle.api.instrument.KillException;
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
import com.oracle.truffle.api.source.SourceSection;

public class ToolTestUtil {

    static final String MIME_TYPE = "text/x-toolTest";

    static enum ToolTestTag implements SyntaxTag {

        ADD_TAG("addition", "test language addition node"),

        VALUE_TAG("value", "test language value node");

        private final String name;
        private final String description;

        private ToolTestTag(String name, String description) {
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

    static Source createTestSource(String description) {
        return Source.fromText("6\n+\n7\n" + description, description).withMimeType(MIME_TYPE);
    }

    @TruffleLanguage.Registration(name = "toolTestLanguage", version = "0", mimeType = MIME_TYPE)
    public static final class ToolTestLang extends TruffleLanguage<Object> {

        public static final ToolTestLang INSTANCE = new ToolTestLang();

        private final ASTProber prober = new TestASTProber();

        private ToolTestLang() {
        }

        @Override
        protected CallTarget parse(Source source, Node context, String... argumentNames) throws IOException {
            final TestValueNode leftValueNode = new TestValueNode(6, source.createSection("6", 0, 1));
            final TestValueNode rightValueNode = new TestValueNode(7, source.createSection("7", 4, 1));
            final TestAdditionNode addNode = new TestAdditionNode(leftValueNode, rightValueNode, source.createSection("+", 2, 1));
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
                return new ToolTestWrapperNode((ToolTestLangNode) node);
            }
            return null;
        }

        @Override
        protected Object evalInContext(Source source, Node node, MaterializedFrame mFrame) throws IOException {
            return null;
        }

        @Override
        protected AdvancedInstrumentRootFactory createAdvancedInstrumentRootFactory(String expr, AdvancedInstrumentResultListener resultListener) throws IOException {
            return null;
        }

        @Override
        protected Object createContext(Env env) {
            return null;
        }
    }

    static final class TestASTProber implements ASTProber {

        public void probeAST(final Instrumenter instrumenter, RootNode startNode) {
            startNode.accept(new NodeVisitor() {

                @Override
                public boolean visit(Node node) {
                    if (node instanceof ToolTestLangNode) {

                        final ToolTestLangNode testNode = (ToolTestLangNode) node;

                        if (node instanceof TestValueNode) {
                            instrumenter.probe(testNode).tagAs(ToolTestTag.VALUE_TAG, null);

                        } else if (node instanceof TestAdditionNode) {
                            instrumenter.probe(testNode).tagAs(ToolTestTag.ADD_TAG, null);

                        }
                    }
                    return true;
                }
            });
        }
    }

    abstract static class ToolTestLangNode extends Node {
        public abstract Object execute(VirtualFrame vFrame);

        protected ToolTestLangNode(SourceSection ss) {
            super(ss);
        }
    }

    @NodeInfo(cost = NodeCost.NONE)
    static class ToolTestWrapperNode extends ToolTestLangNode implements WrapperNode {
        @Child private ToolTestLangNode child;
        @Child private EventHandlerNode eventHandlerNode;

        public ToolTestWrapperNode(ToolTestLangNode child) {
            super(null);
            assert !(child instanceof ToolTestWrapperNode);
            this.child = child;
        }

        @Override
        public String instrumentationInfo() {
            return "Wrapper node for testing";
        }

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
        public Object execute(VirtualFrame vFrame) {
            eventHandlerNode.enter(child, vFrame);
            Object result;
            try {
                result = child.execute(vFrame);
                eventHandlerNode.returnValue(child, vFrame, result);
            } catch (KillException e) {
                throw (e);
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
    static class TestValueNode extends ToolTestLangNode {
        private final int value;

        public TestValueNode(int value, SourceSection s) {
            super(s);
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
    static class TestAdditionNode extends ToolTestLangNode {
        @Child private ToolTestLangNode leftChild;
        @Child private ToolTestLangNode rightChild;

        public TestAdditionNode(TestValueNode leftChild, TestValueNode rightChild, SourceSection s) {
            super(s);
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
        @Child private ToolTestLangNode body;

        /**
         * This constructor emulates the global machinery that applies registered probers to every
         * newly created AST. Global registry is not used, since that would interfere with other
         * tests run in the same environment.
         */
        public InstrumentationTestRootNode(ToolTestLangNode body) {
            super(ToolTestLang.class, null, null);
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

    }

    /**
     * Truffle requires that all guest languages to have a {@link RootNode} which sits atop any AST
     * of the guest language. This is necessary since creating a {@link CallTarget} is how Truffle
     * completes an AST. The root nodes serves as our entry point into a program.
     */
    static class TestRootNode extends RootNode {
        @Child private ToolTestLangNode body;

        final Instrumenter instrumenter;

        /**
         * This constructor emulates the global machinery that applies registered probers to every
         * newly created AST. Global registry is not used, since that would interfere with other
         * tests run in the same environment.
         */
        public TestRootNode(ToolTestLangNode body, Instrumenter instrumenter) {
            super(ToolTestLang.class, null, null);
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
    }
}
