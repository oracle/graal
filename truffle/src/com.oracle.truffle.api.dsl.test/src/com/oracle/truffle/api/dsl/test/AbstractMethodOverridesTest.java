/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.dsl.test;

import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;

/*
 * In this test the named interface specifies two methods to implement.
 * getName is implemented implicitly by RootNode but notImplementedMethod is not.
 * Therefore we expect a single error for this method in DSLTestNode.
 */
public class AbstractMethodOverridesTest {

    interface Named {
        String getName();

        String notImplementedMethod();
    }

    abstract static class TestNode extends RootNode {

        protected TestNode() {
            super(null);
        }

        @Override
        public String getName() {
            return "foobar";
        }

    }

    abstract static class SubNode extends TestNode implements Named {

        @Override
        public Object execute(VirtualFrame frame) {
            return null;
        }

    }

    @NodeChild
    @ExpectError("The type DSLTestNode must implement the inherited abstract method notImplementedMethod().")
    abstract static class DSLTestNode extends SubNode {

        @Specialization
        static Object doFoo(Object child) {
            return child;
        }

    }

}
