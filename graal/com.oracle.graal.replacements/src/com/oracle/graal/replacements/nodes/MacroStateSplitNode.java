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
package com.oracle.graal.replacements.nodes;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.common.*;
import com.oracle.graal.nodeinfo.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.java.*;

/**
 * This is an extension of {@link MacroNode} that is a {@link StateSplit} and a
 * {@link MemoryCheckpoint}.
 */
@NodeInfo
public class MacroStateSplitNode extends MacroNode implements StateSplit, MemoryCheckpoint.Single {

    @OptionalInput(InputType.State) private FrameState stateAfter;

    protected MacroStateSplitNode(Invoke invoke) {
        super(invoke);
        this.stateAfter = invoke.stateAfter();
    }

    @Override
    public FrameState stateAfter() {
        return stateAfter;
    }

    public void setStateAfter(FrameState x) {
        assert x == null || x.isAlive() : "frame state must be in a graph";
        updateUsages(stateAfter, x);
        stateAfter = x;
    }

    public boolean hasSideEffect() {
        return true;
    }

    public LocationIdentity getLocationIdentity() {
        return LocationIdentity.ANY_LOCATION;
    }

    protected void replaceSnippetInvokes(StructuredGraph snippetGraph) {
        for (MethodCallTargetNode call : snippetGraph.getNodes(MethodCallTargetNode.class)) {
            Invoke invoke = call.invoke();
            if (!call.targetMethod().equals(getTargetMethod())) {
                throw new GraalInternalError("unexpected invoke %s in snippet", getClass().getSimpleName());
            }
            assert invoke.stateAfter().bci == BytecodeFrame.AFTER_BCI;
            // Here we need to fix the bci of the invoke
            InvokeNode newInvoke = snippetGraph.add(new InvokeNode(invoke.callTarget(), getBci()));
            newInvoke.setStateAfter(invoke.stateAfter());
            snippetGraph.replaceFixedWithFixed((InvokeNode) invoke.asNode(), newInvoke);
        }
    }
}
