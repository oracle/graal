/*
 * Copyright (c) 2012, 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.codegen.test;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.codegen.*;
import com.oracle.truffle.api.codegen.test.TypeSystemTest.ValueNode;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.nodes.*;

public class ExecuteEvaluatedTest {

    public abstract static class ExtValueNode extends ValueNode {

        public CallTarget executeCallTarget(VirtualFrame frame) throws UnexpectedResultException {
            return SimpleTypesGen.SIMPLETYPES.expectCallTarget(execute(frame));
        }

    }

    /* Represents target[element] */
    @NodeChildren({@NodeChild("targetNode"), @NodeChild("elementNode")})
    abstract static class ReadElementNode extends ExtValueNode {

        @Specialization
        int getInt(Object[] target, int element) {
            return (int) target[element];
        }

        public abstract Object executeWith(VirtualFrame frame, Object targetValue);
    }

    /* Represents target[element]() */
    @NodeChildren({@NodeChild("targetNode"), @NodeChild(value = "elementNode", type = ReadElementNode.class, executeWith = "targetNode")})
    abstract static class ElementCallNode extends ExtValueNode {

        @Specialization
        Object call(Object receiver, CallTarget callTarget) {
            return callTarget.call(new TestArguments(receiver));
        }

    }

    public static class TestArguments extends Arguments {

        private final Object receiver;

        public TestArguments(Object receiver) {
            this.receiver = receiver;
        }

        public Object getReceiver() {
            return receiver;
        }

    }

}
