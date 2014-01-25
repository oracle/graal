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
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.sl.nodes.*;
import com.oracle.truffle.sl.runtime.*;

final class SLInlinableDirectDispatchNode extends SLDirectDispatchNode implements InlinableCallSite {

    @CompilationFinal private int callCount;

    protected SLInlinableDirectDispatchNode(SLAbstractDispatchNode next, SLFunction cachedFunction) {
        super(next, cachedFunction);
    }

    @Override
    protected Object executeCurrent(VirtualFrame frame, SLArguments arguments) {
        if (CompilerDirectives.inInterpreter()) {
            callCount++;
        }
        return cachedCallTarget.call(frame.pack(), arguments);
    }

    @Override
    public boolean inline(FrameFactory factory) {
        CompilerAsserts.neverPartOfCompilation();
        RootNode root = cachedCallTarget.getRootNode();
        SLExpressionNode inlinedNode = ((SLRootNode) root).inline();
        assert inlinedNode != null;
        replace(new SLInlinedDirectDispatchNode(this, inlinedNode), "Inlined " + root);
        /* We are always able to inline if required. */
        return true;
    }

    @Override
    public int getCallCount() {
        return callCount;
    }

    @Override
    public void resetCallCount() {
        callCount = 0;
    }

    @Override
    public Node getInlineTree() {
        RootNode root = cachedCallTarget.getRootNode();
        if (root instanceof SLRootNode) {
            return ((SLRootNode) root).getUninitializedBody();
        }
        return null;
    }

    @Override
    public CallTarget getCallTarget() {
        return cachedCallTarget;
    }
}
