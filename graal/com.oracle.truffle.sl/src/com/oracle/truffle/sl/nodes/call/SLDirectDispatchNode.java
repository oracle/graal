/*
 * Copyright (c) 2013, 2014, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.sl.runtime.*;

/**
 * An entry in the polymorphic inline cache.
 */
final class SLDirectDispatchNode extends SLAbstractDispatchNode {

    /** The cached function. */
    private final SLFunction cachedFunction;

    /**
     * {@link CallNode} is part of the Truffle API and handles all the steps necessary for method
     * inlining: if the call is executed frequently and the callee is small, then the call is
     * inlined, i.e., the call node is replaced with a copy of the callee's AST.
     */
    @Child private CallNode callCachedTargetNode;

    /** Assumption that the {@link #callCachedTargetNode} is still valid. */
    private final Assumption cachedTargetStable;

    /**
     * The next entry of the polymorphic inline cache, either another {@link SLDirectDispatchNode}
     * or a {@link SLUninitializedDispatchNode}.
     */
    @Child private SLAbstractDispatchNode nextNode;

    protected SLDirectDispatchNode(SLAbstractDispatchNode next, SLFunction cachedFunction) {
        this.cachedFunction = cachedFunction;
        this.callCachedTargetNode = adoptChild(CallNode.create(cachedFunction.getCallTarget()));
        this.cachedTargetStable = cachedFunction.getCallTargetStable();
        this.nextNode = adoptChild(next);
    }

    /**
     * Perform the inline cache check. If it succeeds, execute the cached
     * {@link #cachedTargetStable call target}; if it fails, defer to the next element in the chain.
     * <p>
     * Since SL is a quite simple language, the benefit of the inline cache is quite small: after
     * checking that the actual function to be executed is the same as the
     * {@link SLDirectDispatchNode#cachedFunction}, we can safely execute the cached call target.
     * You can reasonably argue that caching the call target is overkill, since we could just
     * retrieve it via {@code function.getCallTarget()}. However, in a more complex language the
     * lookup of the call target is usually much more complicated than in SL. In addition, caching
     * the call target allows method inlining.
     */
    @Override
    protected Object executeDispatch(VirtualFrame frame, SLFunction function, SLArguments arguments) {
        /*
         * The inline cache check. Note that cachedFunction must be a final field so that the
         * compiler can optimize the check.
         */
        if (this.cachedFunction == function) {
            /* Inline cache hit, we are safe to execute the cached call target. */
            try {
                /*
                 * Support for function redefinition: When a function is redefined, the call target
                 * maintained by the SLFunction object is change. To avoid a check for that, we use
                 * an Assumption that is invalidated by the SLFunction when the change is performed.
                 * Since checking an assumption is a no-op in compiled code, the line below does not
                 * add any overhead during optimized execution.
                 */
                cachedTargetStable.check();

                /*
                 * Now we are really ready to perform the call. We use a Truffle CallNode for that,
                 * because it does all the work for method inlining.
                 */
                return callCachedTargetNode.call(frame.pack(), arguments);

            } catch (InvalidAssumptionException ex) {
                /*
                 * The function has been redefined. Remove ourself from the polymorphic inline
                 * cache, so that we fail the check only once. Note that this replacement has subtle
                 * semantics: we are changing a node in the tree that is currently executed. This is
                 * only safe because we know that after the call to replace(), there is no more code
                 * that requires that this node is part of the tree.
                 */
                replace(nextNode);
                /* Execute the next node in the chain by falling out of the if block. */
            }
        }
        /* Inline cache miss, defer to the next element in the chain. */
        return nextNode.executeDispatch(frame, function, arguments);
    }
}
