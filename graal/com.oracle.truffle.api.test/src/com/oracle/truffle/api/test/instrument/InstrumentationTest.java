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
package com.oracle.truffle.api.test.instrument;

import static org.junit.Assert.*;

import java.util.*;

import org.junit.*;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.instrument.*;
import com.oracle.truffle.api.instrument.ProbeFailure.Reason;
import com.oracle.truffle.api.instrument.ProbeNode.WrapperNode;
import com.oracle.truffle.api.instrument.impl.*;
import com.oracle.truffle.api.nodes.*;

/**
 * <h3>AST Instrumentation</h3>
 *
 * Instrumentation allows the insertion into Truffle ASTs language-specific instances of
 * {@link WrapperNode} that propagate execution events through a {@link Probe} to any instances of
 * {@link Instrument} that might be attached to the particular probe by tools.
 * <ol>
 * <li>Creates a simple add AST</li>
 * <li>Verifies its structure</li>
 * <li>"Probes" the add node by adding a {@link WrapperNode} and associated {@link Probe}</li>
 * <li>Attaches a simple {@link Instrument} to the node via the Probe's {@link ProbeNode}</li>
 * <li>Verifies the structure of the probed AST</li>
 * <li>Verifies the execution of the probed AST</li>
 * <li>Verifies the results observed by the instrument.</li>
 * </ol>
 * To do these tests, several required classes have been implemented in their most basic form, only
 * implementing the methods necessary for the tests to pass, with stubs elsewhere.
 */
public class InstrumentationTest {

    private static final SyntaxTag ADD_TAG = new SyntaxTag() {

        @Override
        public String name() {
            return "Addition";
        }

        @Override
        public String getDescription() {
            return "Test Language Addition Node";
        }
    };

    private static final SyntaxTag VALUE_TAG = new SyntaxTag() {

        @Override
        public String name() {
            return "Value";
        }

        @Override
        public String getDescription() {
            return "Test Language Value Node";
        }
    };

    @Test
    public void testInstrumentationStructure() {
        // Create a simple addition AST
        final TruffleRuntime runtime = Truffle.getRuntime();
        final TestValueNode leftValueNode = new TestValueNode(6);
        final TestValueNode rightValueNode = new TestValueNode(7);
        final TestAdditionNode addNode = new TestAdditionNode(leftValueNode, rightValueNode);

        try {
            addNode.probe();
        } catch (ProbeException e) {
            assertEquals(e.getFailure().getReason(), Reason.NO_PARENT);
        }
        final TestRootNode rootNode = new TestRootNode(addNode);

        // Creating a call target sets the parent pointers in this tree and is necessary prior to
        // checking any parent/child relationships
        final CallTarget callTarget1 = runtime.createCallTarget(rootNode);

        // Check the tree structure
        assertEquals(addNode, leftValueNode.getParent());
        assertEquals(addNode, rightValueNode.getParent());
        Iterator<Node> iterator = addNode.getChildren().iterator();
        assertEquals(leftValueNode, iterator.next());
        assertEquals(rightValueNode, iterator.next());
        assertFalse(iterator.hasNext());
        assertEquals(rootNode, addNode.getParent());
        iterator = rootNode.getChildren().iterator();
        assertEquals(addNode, iterator.next());
        assertFalse(iterator.hasNext());

        // Ensure it executes correctly
        assertEquals(13, callTarget1.call());

        // Probe the addition node
        addNode.probe();

        // Check the modified tree structure
        assertEquals(addNode, leftValueNode.getParent());
        assertEquals(addNode, rightValueNode.getParent());
        iterator = addNode.getChildren().iterator();
        assertEquals(leftValueNode, iterator.next());
        assertEquals(rightValueNode, iterator.next());
        assertFalse(iterator.hasNext());

        // Ensure there's a WrapperNode correctly inserted into the AST
        iterator = rootNode.getChildren().iterator();
        Node wrapperNode = iterator.next();
        assertTrue(wrapperNode instanceof TestLanguageWrapperNode);
        assertFalse(iterator.hasNext());
        assertEquals(rootNode, wrapperNode.getParent());

        // Check that the WrapperNode has both the probe and the wrapped node as children
        iterator = wrapperNode.getChildren().iterator();
        assertEquals(addNode, iterator.next());
        ProbeNode probeNode = (ProbeNode) iterator.next();
        assertTrue(probeNode.getProbe() != null);
        assertFalse(iterator.hasNext());

        // Check that you can't probe the WrapperNodes
        TestLanguageWrapperNode wrapper = (TestLanguageWrapperNode) wrapperNode;
        try {
            wrapper.probe();
            fail();
        } catch (ProbeException e) {
            assertEquals(e.getFailure().getReason(), Reason.WRAPPER_NODE);
        }

        // Check that the "probed" AST still executes correctly
        assertEquals(13, callTarget1.call());

    }

    @Test
    public void testListeners() {

        // Create a simple addition AST
        final TruffleRuntime runtime = Truffle.getRuntime();
        final TestValueNode leftValueNode = new TestValueNode(6);
        final TestValueNode rightValueNode = new TestValueNode(7);
        final TestAdditionNode addNode = new TestAdditionNode(leftValueNode, rightValueNode);
        final TestRootNode rootNode = new TestRootNode(addNode);

        // Creating a call target sets the parent pointers in this tree and is necessary prior to
        // checking any parent/child relationships
        final CallTarget callTarget = runtime.createCallTarget(rootNode);
        // Probe the addition node
        final Probe probe = addNode.probe();

        // Check instrumentation with the simplest kind of counters.
        // They should all be removed when the check is finished.
        checkCounters(probe, callTarget, rootNode, new TestInstrumentCounter(), new TestInstrumentCounter(), new TestInstrumentCounter());

        // Now try with the more complex flavor of listener
        checkCounters(probe, callTarget, rootNode, new TestASTInstrumentCounter(), new TestASTInstrumentCounter(), new TestASTInstrumentCounter());

    }

    private static void checkCounters(Probe probe, CallTarget callTarget, RootNode rootNode, TestCounter counterA, TestCounter counterB, TestCounter counterC) {

        // Attach a counting instrument to the probe
        counterA.attach(probe);

        // Attach a second counting instrument to the probe
        counterB.attach(probe);

        // Run it again and check that the two instruments are working
        assertEquals(13, callTarget.call());
        assertEquals(counterA.enterCount(), 1);
        assertEquals(counterA.leaveCount(), 1);
        assertEquals(counterB.enterCount(), 1);
        assertEquals(counterB.leaveCount(), 1);

        // Remove counterA
        counterA.dispose();

        // Run it again and check that instrument B is still working but not A
        assertEquals(13, callTarget.call());
        assertEquals(counterA.enterCount(), 1);
        assertEquals(counterA.leaveCount(), 1);
        assertEquals(counterB.enterCount(), 2);
        assertEquals(counterB.leaveCount(), 2);

        // Simulate a split by cloning the AST
        final CallTarget callTarget2 = Truffle.getRuntime().createCallTarget((TestRootNode) rootNode.copy());
        // Run the clone and check that instrument B is still working but not A
        assertEquals(13, callTarget2.call());
        assertEquals(counterA.enterCount(), 1);
        assertEquals(counterA.leaveCount(), 1);
        assertEquals(counterB.enterCount(), 3);
        assertEquals(counterB.leaveCount(), 3);

        // Run the original and check that instrument B is still working but not A
        assertEquals(13, callTarget2.call());
        assertEquals(counterA.enterCount(), 1);
        assertEquals(counterA.leaveCount(), 1);
        assertEquals(counterB.enterCount(), 4);
        assertEquals(counterB.leaveCount(), 4);

        // Attach a second instrument to the probe
        counterC.attach(probe);

        // Run the original and check that instruments B,C working but not A
        assertEquals(13, callTarget.call());
        assertEquals(counterA.enterCount(), 1);
        assertEquals(counterA.leaveCount(), 1);
        assertEquals(counterB.enterCount(), 5);
        assertEquals(counterB.leaveCount(), 5);
        assertEquals(counterC.enterCount(), 1);
        assertEquals(counterC.leaveCount(), 1);

        // Run the clone and check that instruments B,C working but not A
        assertEquals(13, callTarget2.call());
        assertEquals(counterA.enterCount(), 1);
        assertEquals(counterA.leaveCount(), 1);
        assertEquals(counterB.enterCount(), 6);
        assertEquals(counterB.leaveCount(), 6);
        assertEquals(counterC.enterCount(), 2);
        assertEquals(counterC.leaveCount(), 2);

        // Remove instrumentC
        counterC.dispose();

        // Run the original and check that instrument B working but not A,C
        assertEquals(13, callTarget.call());
        assertEquals(counterA.enterCount(), 1);
        assertEquals(counterA.leaveCount(), 1);
        assertEquals(counterB.enterCount(), 7);
        assertEquals(counterB.leaveCount(), 7);
        assertEquals(counterC.enterCount(), 2);
        assertEquals(counterC.leaveCount(), 2);

        // Run the clone and check that instrument B working but not A,C
        assertEquals(13, callTarget2.call());
        assertEquals(counterA.enterCount(), 1);
        assertEquals(counterA.leaveCount(), 1);
        assertEquals(counterB.enterCount(), 8);
        assertEquals(counterB.leaveCount(), 8);
        assertEquals(counterC.enterCount(), 2);
        assertEquals(counterC.leaveCount(), 2);

        // Remove instrumentB
        counterB.dispose();

        // Run both the original and clone, check that no instruments working
        assertEquals(13, callTarget.call());
        assertEquals(13, callTarget2.call());
        assertEquals(counterA.enterCount(), 1);
        assertEquals(counterA.leaveCount(), 1);
        assertEquals(counterB.enterCount(), 8);
        assertEquals(counterB.leaveCount(), 8);
        assertEquals(counterC.enterCount(), 2);
        assertEquals(counterC.leaveCount(), 2);

    }

    @Test
    public void testTagging() {

        // Applies appropriate tags
        final TestASTProber astProber = new TestASTProber();
        Probe.registerASTProber(astProber);

        // Listens for probes and tags being added
        final TestProbeListener probeListener = new TestProbeListener();
        Probe.addProbeListener(probeListener);

        // Counts all entries to all instances of addition nodes
        final TestMultiCounter additionCounter = new TestMultiCounter();

        // Counts all entries to all instances of value nodes
        final TestMultiCounter valueCounter = new TestMultiCounter();

        // Create a simple addition AST
        final TruffleRuntime runtime = Truffle.getRuntime();
        final TestValueNode leftValueNode = new TestValueNode(6);
        final TestValueNode rightValueNode = new TestValueNode(7);
        final TestAdditionNode addNode = new TestAdditionNode(leftValueNode, rightValueNode);

        final TestRootNode rootNode = new TestRootNode(addNode);

        final CallTarget callTarget = runtime.createCallTarget(rootNode);

        // Check that the prober added probes to the tree
        assertEquals(probeListener.probeCount, 3);
        assertEquals(probeListener.tagCount, 3);

        assertEquals(Probe.findProbesTaggedAs(ADD_TAG).size(), 1);
        assertEquals(Probe.findProbesTaggedAs(VALUE_TAG).size(), 2);

        // Check that it executes correctly
        assertEquals(13, callTarget.call());

        // Dynamically attach a counter for all executions of all Addition nodes
        for (Probe probe : Probe.findProbesTaggedAs(ADD_TAG)) {
            additionCounter.attachCounter(probe);
        }
        // Dynamically attach a counter for all executions of all Value nodes
        for (Probe probe : Probe.findProbesTaggedAs(VALUE_TAG)) {
            valueCounter.attachCounter(probe);
        }

        // Counters initialized at 0
        assertEquals(additionCounter.count, 0);
        assertEquals(valueCounter.count, 0);

        // Execute again
        assertEquals(13, callTarget.call());

        // There are two value nodes in the AST, but only one addition node
        assertEquals(additionCounter.count, 1);
        assertEquals(valueCounter.count, 2);

        Probe.unregisterASTProber(astProber);

    }

    private abstract class TestLanguageNode extends Node {
        public abstract Object execute(VirtualFrame vFrame);

        @Override
        public boolean isInstrumentable() {
            return true;
        }

        @Override
        public WrapperNode createWrapperNode() {
            return new TestLanguageWrapperNode(this);
        }
    }

    @NodeInfo(cost = NodeCost.NONE)
    private class TestLanguageWrapperNode extends TestLanguageNode implements WrapperNode {
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
            try {
                return probeNode.getProbe();
            } catch (IllegalStateException e) {
                throw new IllegalStateException("Cannot call getProbe() on a wrapper that has no probe");
            }
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
    private class TestValueNode extends TestLanguageNode {
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
    private class TestAdditionNode extends TestLanguageNode {
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
    private class TestRootNode extends RootNode {
        @Child private TestLanguageNode body;

        /**
         * This constructor emulates the global machinery that applies registered probers to every
         * newly created AST. Global registry is not used, since that would interfere with other
         * tests run in the same environment.
         */
        public TestRootNode(TestLanguageNode body) {
            super(null);
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
            Probe.applyASTProbers(body);
        }
    }

    private interface TestCounter {

        int enterCount();

        int leaveCount();

        void attach(Probe probe);

        void dispose();

    }

    /**
     * A counter for the number of times execution enters and leaves a probed AST node, using the
     * simplest kind of listener.
     */
    private class TestInstrumentCounter implements TestCounter {

        public int enterCount = 0;
        public int leaveCount = 0;
        public final Instrument instrument;

        public TestInstrumentCounter() {
            this.instrument = Instrument.create(new SimpleInstrumentListener() {

                @Override
                public void enter(Probe probe) {
                    enterCount++;
                }

                @Override
                public void returnAny(Probe probe) {
                    leaveCount++;
                }

            }, "Instrumentation Test Counter");

        }

        public int enterCount() {
            return enterCount;
        }

        public int leaveCount() {
            return leaveCount;
        }

        public void attach(Probe probe) {
            probe.attach(instrument);
        }

        public void dispose() {
            instrument.dispose();
        }
    }

    /**
     * A counter for the number of times execution enters and leaves a probed AST node, using the
     * simplest kind of listener.
     */
    private class TestASTInstrumentCounter implements TestCounter {

        public int enterCount = 0;
        public int leaveCount = 0;
        public final Instrument instrument;

        public TestASTInstrumentCounter() {
            this.instrument = Instrument.create(new SimpleASTInstrumentListener() {

                @Override
                public void enter(Probe probe, Node node, VirtualFrame vFrame) {
                    enterCount++;
                }

                @Override
                public void returnAny(Probe probe, Node node, VirtualFrame vFrame) {
                    leaveCount++;
                }

            }, "Instrumentation Test Counter");

        }

        public int enterCount() {
            return enterCount;
        }

        public int leaveCount() {
            return leaveCount;
        }

        public void attach(Probe probe) {
            probe.attach(instrument);
        }

        public void dispose() {
            instrument.dispose();
        }
    }

    /**
     * Tags selected nodes on newly constructed ASTs.
     */
    private static final class TestASTProber implements NodeVisitor, ASTProber {

        @Override
        public boolean visit(Node node) {
            if (node instanceof TestLanguageNode) {

                final TestLanguageNode testNode = (TestLanguageNode) node;

                if (node instanceof TestValueNode) {
                    testNode.probe().tagAs(VALUE_TAG, null);

                } else if (node instanceof TestAdditionNode) {
                    testNode.probe().tagAs(ADD_TAG, null);

                }
            }
            return true;
        }

        @Override
        public void probeAST(Node node) {
            node.accept(this);
        }
    }

    /**
     * Counts the number of "enter" events at probed nodes using the simplest AST listener.
     */
    static final class TestInstrumentListener extends DefaultInstrumentListener {

        public int counter = 0;

        @Override
        public void enter(Probe probe) {
            counter++;
        }

    }

    /**
     * Counts the number of "enter" events at probed nodes using the AST listener.
     */
    static final class TestASTInstrumentListener extends DefaultASTInstrumentListener {

        public int counter = 0;

        @Override
        public void enter(Probe probe, Node node, VirtualFrame vFrame) {
            counter++;
        }

    }

    /**
     * A counter that can count executions at multiple nodes; it attaches a separate instrument at
     * each Probe, but keeps a total count.
     */
    private static final class TestMultiCounter {

        public int count = 0;

        public void attachCounter(Probe probe) {

            // Attach a new instrument for every Probe
            // where we want to count executions.
            // it will get copied when ASTs cloned, so
            // keep the count in this outer class.
            probe.attach(Instrument.create(new DefaultInstrumentListener() {

                @Override
                public void enter(Probe p) {
                    count++;
                }
            }, "Instrumentation Test MultiCounter"));
        }
    }

    private static final class TestProbeListener extends DefaultProbeListener {

        public int probeCount = 0;
        public int tagCount = 0;

        @Override
        public void newProbeInserted(Probe probe) {
            probeCount++;
        }

        @Override
        public void probeTaggedAs(Probe probe, SyntaxTag tag, Object tagValue) {
            tagCount++;
        }
    }

}
