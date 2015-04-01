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

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.instrument.*;
import com.oracle.truffle.api.instrument.ProbeNode.WrapperNode;
import com.oracle.truffle.api.nodes.*;

/**
 * Tests instrumentation where a client can attach a node that gets attached into the AST.
 */
class InstrumentationTestNodes {

    abstract static class TestLanguageNode extends Node {
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
    static class TestRootNode extends RootNode {
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

}
