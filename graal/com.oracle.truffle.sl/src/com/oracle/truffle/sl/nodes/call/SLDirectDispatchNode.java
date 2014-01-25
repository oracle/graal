/*
 * Copyright (c) 2013, 2013, Oracle and/or its affiliates. All rights reserved.
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

abstract class SLDirectDispatchNode extends SLAbstractDispatchNode {

    protected final SLFunction cachedFunction;
    protected final RootCallTarget cachedCallTarget;
    protected final Assumption cachedCallTargetStable;

    @Child protected SLAbstractDispatchNode nextNode;

    protected SLDirectDispatchNode(SLAbstractDispatchNode next, SLFunction cachedFunction) {
        this.cachedFunction = cachedFunction;
        this.cachedCallTarget = cachedFunction.getCallTarget();
        this.cachedCallTargetStable = cachedFunction.getCallTargetStable();
        this.nextNode = adoptChild(next);
    }

    @Override
    protected final Object executeCall(VirtualFrame frame, SLFunction function, SLArguments arguments) {
        if (this.cachedFunction == function) {
            try {
                cachedCallTargetStable.check();
                return executeCurrent(frame, arguments);
            } catch (InvalidAssumptionException ex) {
                /*
                 * Remove ourselfs from the polymorphic inline cache, so that we fail the check only
                 * once.
                 */
                replace(nextNode);
                /*
                 * Execute the next node in the chain by falling out of the if block.
                 */
            }
        }
        return nextNode.executeCall(frame, function, arguments);
    }

    protected abstract Object executeCurrent(VirtualFrame frame, SLArguments arguments);
}
