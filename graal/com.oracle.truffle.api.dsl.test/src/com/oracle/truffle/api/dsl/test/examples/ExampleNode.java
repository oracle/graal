/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.dsl.test.examples;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.api.dsl.internal.*;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.nodes.*;

@TypeSystemReference(ExampleTypes.class)
@NodeChild(value = "args", type = ExampleNode[].class)
public abstract class ExampleNode extends Node {

    public Object execute(@SuppressWarnings("unused") VirtualFrame frame) {
        // will get implemented by the DSL.
        throw new UnsupportedOperationException();
    }

    @Override
    public String toString() {
        if (this instanceof SpecializedNode) {
            return ((SpecializedNode) this).getSpecializationNode().toString();
        } else {
            return super.toString();
        }
    }

    public static CallTarget createTarget(ExampleNode node) {
        return Truffle.getRuntime().createCallTarget(new ExampleRootNode(node));
    }

    @SuppressWarnings("unchecked")
    public static <T> T getNode(CallTarget target) {
        return (T) ((ExampleRootNode) ((RootCallTarget) target).getRootNode()).child;
    }

    public static ExampleNode[] createArguments(int count) {
        ExampleNode[] nodes = new ExampleNode[count];
        for (int i = 0; i < count; i++) {
            nodes[i] = new ExampleArgumentNode(i);
        }
        return nodes;
    }

    private static class ExampleRootNode extends RootNode {

        @Child ExampleNode child;

        public ExampleRootNode(ExampleNode child) {
            this.child = child;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            return child.execute(frame);
        }

    }

    private static class ExampleArgumentNode extends ExampleNode {

        private final int index;

        public ExampleArgumentNode(int index) {
            this.index = index;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            Object[] arguments = frame.getArguments();
            if (index < arguments.length) {
                return arguments[index];
            }
            return null;
        }
    }

    public static CallTarget createDummyTarget(int argumentIndex) {
        return Truffle.getRuntime().createCallTarget(new DummyCallRootNode(argumentIndex));
    }

    private static class DummyCallRootNode extends RootNode {

        private final int argumentIndex;

        public DummyCallRootNode(int argumentIndex) {
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
