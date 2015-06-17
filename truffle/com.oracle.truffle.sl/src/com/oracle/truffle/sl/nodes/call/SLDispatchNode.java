/*
 * Copyright (c) 2013, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.sl.nodes.call;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.sl.runtime.*;

public abstract class SLDispatchNode extends Node {

    protected static final int INLINE_CACHE_SIZE = 2;

    public abstract Object executeDispatch(VirtualFrame frame, SLFunction function, Object[] arguments);

    @Specialization(guards = "function.getCallTarget() == null")
    protected Object doUndefinedFunction(SLFunction function, @SuppressWarnings("unused") Object[] arguments) {
        throw new SLUndefinedFunctionException(function.getName());
    }

    /**
     * Inline cached specialization of the dispatch.
     *
     * <p>
     * Since SL is a quite simple language, the benefit of the inline cache is quite small: after
     * checking that the actual function to be executed is the same as the cachedFuntion, we can
     * safely execute the cached call target. You can reasonably argue that caching the call target
     * is overkill, since we could just retrieve it via {@code function.getCallTarget()}. However,
     * in a more complex language the lookup of the call target is usually much more complicated
     * than in SL. In addition, caching the call target allows method inlining.
     * </p>
     *
     * <p>
     * {@code limit = "INLINE_CACHE_SIZE"} Specifies the limit number of inline cache specialization
     * instantiations.
     * </p>
     * <p>
     * {@code guards = "function == cachedFunction"} The inline cache check. Note that
     * cachedFunction is a final field so that the compiler can optimize the check.
     * </p>
     * <p>
     * {@code assumptions = "cachedFunction.getCallTargetStable()"} Support for function
     * redefinition: When a function is redefined, the call target maintained by the SLFunction
     * object is change. To avoid a check for that, we use an Assumption that is invalidated by the
     * SLFunction when the change is performed. Since checking an assumption is a no-op in compiled
     * code, the assumption check performed by the DSL does not add any overhead during optimized
     * execution.
     * </p>
     *
     * @see Cached
     * @see Specialization
     *
     * @param function the dynamically provided function
     * @param cachedFunction the cached function of the specialization instance
     * @param callNode the {@link DirectCallNode} specifically created for the {@link CallTarget} in
     *            cachedFunction.
     */
    @Specialization(limit = "INLINE_CACHE_SIZE", guards = "function == cachedFunction", assumptions = "cachedFunction.getCallTargetStable()")
    protected static Object doDirect(VirtualFrame frame, SLFunction function, Object[] arguments, //
                    @Cached("function") SLFunction cachedFunction, //
                    @Cached("create(cachedFunction.getCallTarget())") DirectCallNode callNode) {
        /* Inline cache hit, we are safe to execute the cached call target. */
        return callNode.call(frame, arguments);
    }

    /**
     * Slow-path code for a call, used when the polymorphic inline cache exceeded its maximum size
     * specified in <code>INLINE_CACHE_SIZE</code>. Such calls are not optimized any further, e.g.,
     * no method inlining is performed.
     */
    @Specialization(contains = "doDirect")
    protected static Object doIndirect(VirtualFrame frame, SLFunction function, Object[] arguments, //
                    @Cached("create()") IndirectCallNode callNode) {
        /*
         * SL has a quite simple call lookup: just ask the function for the current call target, and
         * call it.
         */
        return callNode.call(frame, function.getCallTarget(), arguments);
    }

}
