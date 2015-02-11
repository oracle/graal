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

import static com.oracle.truffle.api.dsl.test.examples.ExampleNode.*;
import static org.junit.Assert.*;

import org.junit.*;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.api.dsl.test.examples.FunctionCallFactory.FunctionCallNodeGen;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.nodes.*;

/**
 * This example illustrates how {@link Cached} can be used to implement function calls that use
 * local state for its guards. If there are always distinct Function objects with distinct
 * CallTargets then we can use the directCallFunctionGuard specialization. If there are two Function
 * instances cached with the same CallTarget then we use the directCall cache. We do this because
 * the directCallFunctionGuard specialization can use a faster guard.
 */
@SuppressWarnings("unused")
public class FunctionCall {

    @Test
    public void testFunctionCall() {
        assertEquals(2, FunctionCallNode.CACHE_SIZE);

        CallTarget dummyTarget1 = createDummyTarget(0);
        CallTarget dummyTarget2 = createDummyTarget(0);
        CallTarget dummyTarget3 = createDummyTarget(0);

        Function dummyFunction1 = new Function(dummyTarget1);
        Function dummyFunction2 = new Function(dummyTarget2);
        Function dummyFunction3 = new Function(dummyTarget2); // same target as dummyFunction2
        Function dummyFunction4 = new Function(dummyTarget3);

        FunctionCallNode node = FunctionCallNodeGen.create(createArguments(2));
        CallTarget target = createTarget(node);
        assertEquals(42, target.call(dummyFunction1, 42));
        assertEquals(43, target.call(dummyFunction2, 43));
        assertEquals(44, target.call(dummyFunction3, 44)); // transition to directCall
        assertEquals(2, node.directCallFunctionGuard);
        assertEquals(1, node.directCall);

        assertEquals(42, target.call(dummyFunction1, 42));
        assertEquals(43, target.call(dummyFunction2, 43));
        assertEquals(2, node.directCallFunctionGuard);
        assertEquals(3, node.directCall);

        assertEquals(44, target.call(dummyFunction4, 44)); // transition to indirectCall
        assertEquals(2, node.directCallFunctionGuard);
        assertEquals(3, node.directCall);
        assertEquals(1, node.indirectCall);

        assertEquals(42, target.call(dummyFunction1, 42));
        assertEquals(43, target.call(dummyFunction2, 43));
        assertEquals(44, target.call(dummyFunction3, 44));
        assertEquals(2, node.directCallFunctionGuard);
        assertEquals(3, node.directCall);
        assertEquals(4, node.indirectCall);
    }

    public static class FunctionCallNode extends ExampleNode {

        public static final int CACHE_SIZE = 2;

        private Function[] cachedFunctions = new Function[CACHE_SIZE];

        private int directCallFunctionGuard;
        private int directCall;
        private int indirectCall;

        @Specialization(limit = "CACHE_SIZE", guards = {"function == cachedFunction", "cacheFunctionTarget(cachedFunction)"})
        public Object directCallFunctionGuard(VirtualFrame frame, Function function, Object argument,  //
                        @Cached("function") Function cachedFunction, //
                        @Cached("create(cachedFunction.getTarget())") DirectCallNode callNode) {
            directCallFunctionGuard++;
            return callNode.call(frame, new Object[]{argument});
        }

        protected final boolean cacheFunctionTarget(Function function) {
            CompilerAsserts.neverPartOfCompilation();
            if (cachedFunctions != null) {
                for (int i = 0; i < cachedFunctions.length; i++) {
                    Function cachedFunction = cachedFunctions[i];
                    if (cachedFunction == null) {
                        cachedFunctions[i] = function;
                        return true;
                    } else if (cachedFunction == function) {
                        return true;
                    } else if (cachedFunction.getTarget() == function.getTarget()) {
                        cachedFunctions = null;
                        return false;
                    }
                }
            }
            return false;
        }

        @Specialization(limit = "CACHE_SIZE", contains = "directCallFunctionGuard", guards = {"function.getTarget() == cachedTarget"})
        protected Object directCall(VirtualFrame frame, Function function, Object argument,  //
                        @Cached("function.getTarget()") CallTarget cachedTarget, //
                        @Cached("create(cachedTarget)") DirectCallNode callNode) {
            directCall++;
            return callNode.call(frame, new Object[]{argument});
        }

        @Specialization(contains = "directCall")
        protected Object indirectCall(VirtualFrame frame, Function function, Object argument, //
                        @Cached("create()") IndirectCallNode callNode) {
            indirectCall++;
            return callNode.call(frame, function.getTarget(), new Object[]{argument});
        }
    }

    public static class Function {

        private final CallTarget target;

        public Function(CallTarget target) {
            this.target = target;
        }

        public CallTarget getTarget() {
            return target;
        }
    }

}
