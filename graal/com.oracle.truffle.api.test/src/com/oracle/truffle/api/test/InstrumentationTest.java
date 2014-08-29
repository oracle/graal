/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.test;

import java.util.*;

import org.junit.*;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.instrument.*;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.api.source.*;

/**
 * <h3>AST Instrumentation</h3>
 *
 * Instrumentation allows the insertion into Truffle ASTs language-specific instances of
 * {@link Wrapper} that propagate {@link ExecutionEvents} through a {@link Probe} to any instances
 * of {@link Instrument} that might be attached to the particular probe by tools.
 * <ol>
 * <li>Creates a simple add AST</li>
 * <li>Verifies its structure</li>
 * <li>"Probes" the add node by adding a {@link Wrapper} and associated {@link Probe}</li>
 * <li>Attaches a simple {@link Instrument} to the node via its {@link Probe}</li>
 * <li>Verifies the structure of the probed AST</li>
 * <li>Verifies the execution of the probed AST</li>
 * <li>Verifies the results observed by the instrument.</li>
 * </ol>
 * To do these tests, several required classes have been implemented in their most basic form, only
 * implementing the methods necessary for the tests to pass, with stubs elsewhere.
 */
public class InstrumentationTest {

    @Test
    public void test() {
        // Build a tree
        TruffleRuntime runtime = Truffle.getRuntime();
        TestChildNode leftChild = new TestChildNode();
        TestChildNode rightChild = new TestChildNode();
        TestSourceSection sourceSection = new TestSourceSection();
        TestAddNode addNode = new TestAddNode(leftChild, rightChild, sourceSection);
        TestRootNode rootNode = new TestRootNode(addNode);

        // Have to create a call target before checking parent/child relationships
        CallTarget target = runtime.createCallTarget(rootNode);

        // Check tree structure
        Assert.assertEquals(addNode, leftChild.getParent());
        Assert.assertEquals(addNode, rightChild.getParent());
        Iterator<Node> iterator = addNode.getChildren().iterator();
        Assert.assertEquals(leftChild, iterator.next());
        Assert.assertEquals(rightChild, iterator.next());
        Assert.assertFalse(iterator.hasNext());
        Assert.assertEquals(rootNode, addNode.getParent());
        iterator = rootNode.getChildren().iterator();
        Assert.assertEquals(addNode, iterator.next());
        Assert.assertFalse(iterator.hasNext());
        Object result = target.call();
        Assert.assertEquals(42, result);

        // Create another call target, this time with the "probed" add node
        TestExecutionContext context = new TestExecutionContext();
        TestWrapper wrapper = new TestWrapper(addNode, context);
        rootNode = new TestRootNode(wrapper);
        target = runtime.createCallTarget(rootNode);

        // Check the new tree structure
        Assert.assertEquals(addNode, leftChild.getParent());
        Assert.assertEquals(addNode, rightChild.getParent());
        iterator = addNode.getChildren().iterator();
        Assert.assertEquals(leftChild, iterator.next());
        Assert.assertEquals(rightChild, iterator.next());
        Assert.assertFalse(iterator.hasNext());
        Assert.assertEquals(wrapper, addNode.getParent());
        iterator = wrapper.getChildren().iterator();
        Assert.assertEquals(addNode, iterator.next());
        Assert.assertFalse(iterator.hasNext());
        Assert.assertEquals(rootNode, wrapper.getParent());
        iterator = rootNode.getChildren().iterator();
        Assert.assertEquals(wrapper, iterator.next());
        Assert.assertFalse(iterator.hasNext());
        result = target.call();
        Assert.assertEquals(42, result);

        // Create some instruments
        final TestInstrument instrumentA = new TestInstrument();
        final TestInstrument instrumentB = new TestInstrument();

        wrapper.getProbe().addInstrument(instrumentA);

        result = target.call();
        Assert.assertEquals(instrumentA.numInstrumentEnter, 1);
        Assert.assertEquals(instrumentA.numInstrumentLeave, 1);
        Assert.assertEquals(instrumentB.numInstrumentEnter, 0);
        Assert.assertEquals(instrumentB.numInstrumentLeave, 0);
        Assert.assertEquals(42, result);

        wrapper.getProbe().addInstrument(instrumentB);

        result = target.call();
        Assert.assertEquals(instrumentA.numInstrumentEnter, 2);
        Assert.assertEquals(instrumentA.numInstrumentLeave, 2);
        Assert.assertEquals(instrumentB.numInstrumentEnter, 1);
        Assert.assertEquals(instrumentB.numInstrumentLeave, 1);
        Assert.assertEquals(42, result);

        wrapper.getProbe().removeInstrument(instrumentA);

        result = target.call();
        Assert.assertEquals(instrumentA.numInstrumentEnter, 2);
        Assert.assertEquals(instrumentA.numInstrumentLeave, 2);
        Assert.assertEquals(instrumentB.numInstrumentEnter, 2);
        Assert.assertEquals(instrumentB.numInstrumentLeave, 2);
        Assert.assertEquals(42, result);

    }

    private class TestRootNode extends RootNode {
        @Child private RootNode child;

        public TestRootNode(RootNode child) {
            super(null);
            this.child = child;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            return child.execute(frame);
        }
    }

    private class TestAddNode extends RootNode {

        @Child private TestChildNode left;
        @Child private TestChildNode right;

        public TestAddNode(TestChildNode left, TestChildNode right, TestSourceSection sourceSection) {
            super(sourceSection);
            this.left = left;
            this.right = right;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            return left.execute() + right.execute();
        }
    }

    private class TestChildNode extends Node {

        public TestChildNode() {
            super(null);
        }

        public int execute() {
            return 21;
        }
    }

    /**
     * The wrapper node class is usually language-specific and inherits from the language-specific
     * subclass of {@link Node}, not {@RootNode}.
     */
    private class TestWrapper extends RootNode implements Wrapper {
        @Child private RootNode child;
        private Probe probe;

        public TestWrapper(RootNode child, ExecutionContext context) {
            this.child = insert(child);
            this.probe = context.createProbe(child.getSourceSection());
        }

        public Node getChild() {
            return child;
        }

        public Probe getProbe() {
            return probe;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            probe.enter(child, frame);
            Object result;

            try {
                result = child.execute(frame);
                probe.leave(child, frame, result);
            } catch (Exception e) {
                probe.leaveExceptional(child, frame, e);
                throw (e);
            }
            return result;
        }
    }

    /**
     * An "empty" description of the source code that might correspond to a particular AST node. The
     * instrumentation framework tracks probes that have been inserted by their source location,
     * using this as a key.
     */
    private class TestSourceSection implements SourceSection {

        public Source getSource() {
            return null;
        }

        public int getStartLine() {
            return 0;
        }

        public LineLocation getLineLocation() {
            return null;
        }

        public int getStartColumn() {
            return 0;
        }

        public int getCharIndex() {
            return 0;
        }

        public int getCharLength() {
            return 0;
        }

        public int getCharEndIndex() {
            return 0;
        }

        public String getIdentifier() {
            return null;
        }

        public String getCode() {
            return null;
        }

        public String getShortDescription() {
            return null;
        }

    }

    private class TestExecutionContext extends ExecutionContext {

        @Override
        public String getLanguageShortName() {
            return "test";
        }

        @Override
        protected void setSourceCallback(SourceCallback sourceCallback) {
        }

    }

    private class TestInstrument extends Instrument {

        public int numInstrumentEnter = 0;
        public int numInstrumentLeave = 0;

        @Override
        public void enter(Node astNode, VirtualFrame frame) {
            numInstrumentEnter++;
        }

        @Override
        public void leave(Node astNode, VirtualFrame frame, Object result) {
            numInstrumentLeave++;
        }
    }

}
