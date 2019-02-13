/*
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
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
