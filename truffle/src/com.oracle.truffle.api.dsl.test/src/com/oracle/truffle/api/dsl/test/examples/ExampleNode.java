/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.dsl.test.examples;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.nodes.UnexpectedResultException;

@TypeSystemReference(ExampleTypes.class)
@NodeChild(value = "args", type = ExampleNode[].class)
public abstract class ExampleNode extends Node {

    public ExampleNode[] getArgs() {
        throw new UnsupportedOperationException();
    }

    public Object execute(@SuppressWarnings("unused") VirtualFrame frame) {
        // will get implemented by the DSL.
        throw new UnsupportedOperationException();
    }

    public int executeInt(VirtualFrame frame) throws UnexpectedResultException {
        return ExampleTypesGen.expectInteger(execute(frame));
    }

    public double executeDouble(VirtualFrame frame) throws UnexpectedResultException {
        return ExampleTypesGen.expectDouble(execute(frame));
    }

    public long executeLong(VirtualFrame frame) throws UnexpectedResultException {
        return ExampleTypesGen.expectLong(execute(frame));
    }

    public static CallTarget createTarget(ExampleNode node) {
        return Truffle.getRuntime().createCallTarget(new ExampleRootNode(node));
    }

    public static ExampleArgumentNode[] getArguments(CallTarget target) {
        return (ExampleArgumentNode[]) ((ExampleRootNode) ((RootCallTarget) target).getRootNode()).child.getArgs();
    }

    @SuppressWarnings("unchecked")
    public static <T> T getNode(CallTarget target) {
        return (T) ((ExampleRootNode) ((RootCallTarget) target).getRootNode()).child;
    }

    public static ExampleArgumentNode[] createArguments(int count) {
        ExampleArgumentNode[] nodes = new ExampleArgumentNode[count];
        for (int i = 0; i < count; i++) {
            nodes[i] = new ExampleArgumentNode(i);
        }
        return nodes;
    }

    private static class ExampleRootNode extends RootNode {

        @Child ExampleNode child;

        ExampleRootNode(ExampleNode child) {
            super(null);
            this.child = child;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            return child.execute(frame);
        }

    }

    public static class ExampleArgumentNode extends ExampleNode {

        private final int index;

        public int genericInvocationCount;
        public int intInvocationCount;
        public int doubleInvocationCount;
        public int longInvocationCount;

        @Override
        public ExampleNode[] getArgs() {
            return new ExampleNode[0];
        }

        ExampleArgumentNode(int index) {
            this.index = index;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            genericInvocationCount++;
            Object[] arguments = frame.getArguments();
            if (index < arguments.length) {
                return arguments[index];
            }
            return null;
        }

        @Override
        public double executeDouble(VirtualFrame frame) throws UnexpectedResultException {
            doubleInvocationCount++;
            return super.executeDouble(frame);
        }

        @Override
        public int executeInt(VirtualFrame frame) throws UnexpectedResultException {
            intInvocationCount++;
            return super.executeInt(frame);
        }

        @Override
        public long executeLong(VirtualFrame frame) throws UnexpectedResultException {
            longInvocationCount++;
            return super.executeLong(frame);
        }
    }

    public static CallTarget createDummyTarget(int argumentIndex) {
        return Truffle.getRuntime().createCallTarget(new DummyCallRootNode(argumentIndex));
    }

    private static class DummyCallRootNode extends RootNode {

        private final int argumentIndex;

        DummyCallRootNode(int argumentIndex) {
            super(null);
            this.argumentIndex = argumentIndex;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            return frame.getArguments()[argumentIndex];
        }

        @Override
        public String toString() {
            return "DummyRootNode[arg = " + argumentIndex + "]";
        }

    }

}
