/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.test;

import java.util.function.Supplier;

import org.graalvm.compiler.truffle.test.nodes.AbstractTestNode;
import org.graalvm.compiler.truffle.test.nodes.RootTestNode;
import org.junit.Test;

import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;

public class PhiStampInferencePartialEvaluationTest extends PartialEvaluationTest {

    @Test
    public void ifPhiStamp() {
        /*
         * The stamp of a phi should be inferred during partial evaluation so that its type
         * information can be used to devirtualize method calls.
         */
        FrameDescriptor fd = new FrameDescriptor();
        AbstractTestNode result = new IfPhiStampTestNode();
        RootNode rootNode = new RootTestNode(fd, "ifPhiStamp", result);
        RootCallTarget callTarget = Truffle.getRuntime().createCallTarget(rootNode);
        callTarget.call(new Object[]{true});
        callTarget.call(new Object[]{false});
        new D().get(); // ensure method cannot be statically bound without receiver type info
        assertPartialEvalNoInvokes(callTarget, new Object[]{true});
    }

    static class IfPhiStampTestNode extends AbstractTestNode {
        @Child private ANode b;
        @Child private ANode c;

        IfPhiStampTestNode() {
            this.b = new ANode(() -> new B(42));
            this.c = new ANode(() -> new C(666));
        }

        int getA(Object[] args) {
            A a;
            if (args[0] == Boolean.TRUE) {
                a = b.execute();
            } else {
                a = c.execute();
            }
            return a.get();
        }

        @Override
        public int execute(VirtualFrame frame) {
            return getA(frame.getArguments());
        }
    }

    static class A {
        final int value;

        A(int value) {
            this.value = value;
        }

        public int get() {
            return value;
        }
    }

    static class B extends A {
        B(int value) {
            super(value);
        }
    }

    static class C extends B {
        C(int value) {
            super(value);
        }
    }

    static class D extends A {
        D() {
            super(666);
        }

        @Override
        public int get() {
            return 666;
        }
    }

    static class ANode extends Node {
        private final Supplier<A> supplier;

        ANode(Supplier<A> supplier) {
            this.supplier = supplier;
        }

        public A execute() {
            return supplier.get();
        }
    }
}
