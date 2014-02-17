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
import com.oracle.truffle.sl.*;
import com.oracle.truffle.sl.runtime.*;

/**
 * The last entry of a polymorphic inline cache.
 */
final class SLUninitializedDispatchNode extends SLAbstractDispatchNode {

    /**
     * When we reach this method, all the previous cache entries did not match the function. If the
     * cache is still small enough, we extend it by adding another {@link SLDirectDispatchNode}. If
     * the cache reached its maximum size, we replace the whole dispatch chain with a
     * {@link SLGenericDispatchNode}.
     */
    @Override
    protected Object executeDispatch(VirtualFrame frame, SLFunction function, SLArguments arguments) {
        /* The following code modifies the AST, so compiled code must be invalidated. */
        CompilerDirectives.transferToInterpreterAndInvalidate();

        /*
         * Count the number of SLDirectDispatchNodes we already have in the cache. We walk the chain
         * of parent nodes until we hit the SLCallNode. We know that a SLCallNode is always present.
         */
        Node cur = this;
        int depth = 0;
        while (cur.getParent() instanceof SLAbstractDispatchNode) {
            cur = cur.getParent();
            depth++;
        }
        SLInvokeNode invokeNode = (SLInvokeNode) cur.getParent();

        SLAbstractDispatchNode replacement;
        if (function.getCallTarget() == null) {
            /* Corner case: the function is not defined, so report an error to the user. */
            throw new SLException("Call of undefined function: " + function.getName());

        } else if (depth < INLINE_CACHE_SIZE) {
            /* Extend the inline cache. Allocate the new cache entry, and the new end of the cache. */
            SLAbstractDispatchNode next = new SLUninitializedDispatchNode();
            replacement = new SLDirectDispatchNode(next, function);
            /* Replace ourself with the new cache entry. */
            replace(replacement);

        } else {
            /* Cache size exceeded, fall back to a single generic dispatch node. */
            replacement = new SLGenericDispatchNode();
            /* Replace the whole chain, not just ourself, with the new generic node. */
            invokeNode.dispatchNode.replace(replacement);
        }

        /*
         * Execute the newly created node perform the actual dispatch. That saves us from
         * duplicating the actual call logic here.
         */
        return replacement.executeDispatch(frame, function, arguments);
    }
}
