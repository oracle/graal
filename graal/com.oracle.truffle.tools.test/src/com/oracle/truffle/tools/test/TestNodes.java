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

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.instrument.*;
import com.oracle.truffle.api.instrument.ProbeNode.WrapperNode;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.api.source.*;

/**
 * Nodes and an {@linkplain CallTarget executable ASTs} for testing.
 */
class TestNodes {

    /**
     * A fake source used for testing: empty line 1, expression on line 2.
     */
    public static final Source expr13Source = Source.fromText("\n6+7\n", "Test Source: expression on line 2 that evaluates to 13");
    public static final LineLocation expr13Line1 = expr13Source.createLineLocation(1);
    public static final LineLocation expr13Line2 = expr13Source.createLineLocation(2);

    /**
     * An executable addition expression that evaluates to 13.
     */
    static CallTarget createExpr13TestCallTarget() {
        final RootNode rootNode = createExpr13TestRootNode();
        return Truffle.getRuntime().createCallTarget(rootNode);
    }

    /**
     * Root holding an addition expression that evaluates to 13.
     */
    static RootNode createExpr13TestRootNode() {
        final TestLanguageNode ast = createExpr13AST();
        final TestRootNode rootNode = new TestRootNode(ast);
        rootNode.adoptChildren();
        return rootNode;
    }

    /**
     * Addition expression that evaluates to 13, with faked source attribution.
     */
    static TestLanguageNode createExpr13AST() {
        final SourceSection leftSourceSection = expr13Source.createSection("left", 1, 1);
        final TestValueNode leftValueNode = new TestValueNode(6, leftSourceSection);
        final SourceSection rightSourceSection = expr13Source.createSection("right", 3, 1);
        final TestValueNode rightValueNode = new TestValueNode(7, rightSourceSection);
        final SourceSection exprSourceSection = expr13Source.createSection("expr", 1, 3);
        return new TestAddNode(leftValueNode, rightValueNode, exprSourceSection);
    }

    abstract static class TestLanguageNode extends Node {
        public abstract Object execute(VirtualFrame frame);

        public TestLanguageNode() {
        }

        public TestLanguageNode(SourceSection srcSection) {
            super(srcSection);
        }

        @Override
        public boolean isInstrumentable() {
            return true;
        }

        @Override
        public WrapperNode createWrapperNode() {
            return new TestWrapperNode(this);
        }
    }

    @NodeInfo(cost = NodeCost.NONE)
    static class TestWrapperNode extends TestLanguageNode implements WrapperNode {
        @Child private TestLanguageNode child;
        @Child private ProbeNode probeNode;

        public TestWrapperNode(TestLanguageNode child) {
            assert !(child instanceof TestWrapperNode);
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
        public Object execute(VirtualFrame frame) {
            probeNode.enter(child, frame);
            Object result;

            try {
                result = child.execute(frame);
                probeNode.returnValue(child, frame, result);
            } catch (KillException e) {
                throw (e);
            } catch (Exception e) {
                probeNode.returnExceptional(child, frame, e);
                throw (e);
            }

            return result;
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
        public Object execute(VirtualFrame frame) {
            return body.execute(frame);
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

    static class TestValueNode extends TestLanguageNode {
        private final int value;

        public TestValueNode(int value) {
            this.value = value;
        }

        public TestValueNode(int value, SourceSection srcSection) {
            super(srcSection);
            this.value = value;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            return new Integer(this.value);
        }
    }

    static class TestAddNode extends TestLanguageNode {
        @Child private TestLanguageNode leftChild;
        @Child private TestLanguageNode rightChild;

        public TestAddNode(TestValueNode leftChild, TestValueNode rightChild, SourceSection sourceSection) {
            super(sourceSection);
            this.leftChild = insert(leftChild);
            this.rightChild = insert(rightChild);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            return new Integer(((Integer) leftChild.execute(frame)).intValue() + ((Integer) rightChild.execute(frame)).intValue());
        }
    }

}
