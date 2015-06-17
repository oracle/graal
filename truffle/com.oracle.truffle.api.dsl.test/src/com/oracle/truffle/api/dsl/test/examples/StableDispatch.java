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
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.api.utilities.*;

/**
 * This example is based on the SLDispatchNode of SimpleLanguage. It shows how to implement a simple
 * inline cache with an assumption that needs to be checked.
 *
 * Note that if an assumption is invalidated the specialization instantiation is removed.
 */
@SuppressWarnings("unused")
@NodeChildren({@NodeChild("function"), @NodeChild("arguments")})
public class StableDispatch {

    public static class StableDispatchNode extends ExampleNode {

        @Specialization(guards = "function == cachedFunction", assumptions = "cachedFunction.getCallTargetStable()")
        protected static Object directDispatch(VirtualFrame frame, SLFunction function, Object[] arguments, //
                        @Cached("function") SLFunction cachedFunction, //
                        @Cached("create(cachedFunction.getCallTarget())") DirectCallNode callNode) {
            return callNode.call(frame, arguments);
        }

        @Specialization(contains = "directDispatch")
        protected static Object indirectDispatch(VirtualFrame frame, SLFunction function, Object[] arguments, //
                        @Cached("create()") IndirectCallNode callNode) {
            return callNode.call(frame, function.getCallTarget(), arguments);
        }
    }

    public static final class SLFunction {

        private CallTarget callTarget;
        private final CyclicAssumption callTargetStable;

        protected SLFunction(String name) {
            this.callTargetStable = new CyclicAssumption(name);
        }

        protected void setCallTarget(CallTarget callTarget) {
            this.callTarget = callTarget;
            this.callTargetStable.invalidate();
        }

        public CallTarget getCallTarget() {
            return callTarget;
        }

        public Assumption getCallTargetStable() {
            return callTargetStable.getAssumption();
        }
    }

}
