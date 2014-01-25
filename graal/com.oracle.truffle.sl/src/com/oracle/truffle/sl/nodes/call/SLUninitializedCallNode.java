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
import com.oracle.truffle.sl.runtime.*;

final class SLUninitializedCallNode extends SLAbstractDispatchNode {

    @Override
    protected Object executeCall(VirtualFrame frame, SLFunction function, SLArguments arguments) {
        CompilerDirectives.transferToInterpreterAndInvalidate();

        SLAbstractDispatchNode cur = this;
        int depth = 0;
        while (cur.getParent() instanceof SLAbstractDispatchNode) {
            cur = (SLAbstractDispatchNode) cur.getParent();
            depth++;
        }
        SLCallNode callNode = (SLCallNode) cur.getParent();

        SLAbstractDispatchNode specialized;
        if (depth < INLINE_CACHE_SIZE) {
            SLAbstractDispatchNode next = new SLUninitializedCallNode();
            SLAbstractDispatchNode direct = new SLInlinableDirectDispatchNode(next, function);
            specialized = replace(direct);
        } else {
            SLAbstractDispatchNode generic = new SLGenericDispatchNode();
            specialized = callNode.dispatchNode.replace(generic);
        }

        return specialized.executeCall(frame, function, arguments);
    }
}
