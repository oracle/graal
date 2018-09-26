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

import static com.oracle.truffle.api.dsl.test.examples.ExampleNode.createArguments;
import static com.oracle.truffle.api.dsl.test.examples.ExampleNode.createDummyTarget;
import static com.oracle.truffle.api.dsl.test.examples.ExampleNode.createTarget;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.test.examples.FunctionCallFactory.FunctionCallNodeGen;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.IndirectCallNode;

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
        public Object directCallFunctionGuard(Function function, Object argument,
                        @Cached("function") Function cachedFunction,
                        @Cached("create(cachedFunction.getTarget())") DirectCallNode callNode) {
            directCallFunctionGuard++;
            return callNode.call(new Object[]{argument});
        }

        protected final boolean cacheFunctionTarget(Function function) {
            CompilerAsserts.neverPartOfCompilation("do not cache function target in compiled code");
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

        @Specialization(limit = "CACHE_SIZE", replaces = "directCallFunctionGuard", guards = {"function.getTarget() == cachedTarget"})
        protected Object directCall(Function function, Object argument,
                        @Cached("function.getTarget()") CallTarget cachedTarget,
                        @Cached("create(cachedTarget)") DirectCallNode callNode) {
            directCall++;
            return callNode.call(new Object[]{argument});
        }

        @Specialization(replaces = "directCall")
        protected Object indirectCall(Function function, Object argument,
                        @Cached("create()") IndirectCallNode callNode) {
            indirectCall++;
            return callNode.call(function.getTarget(), new Object[]{argument});
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
