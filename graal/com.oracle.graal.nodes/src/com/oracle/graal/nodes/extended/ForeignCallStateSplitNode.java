/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.nodes.extended;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.spi.*;

/**
 * A foreign call that is also a state split.
 */
@NodeInfo(nameTemplate = "ForeignCallStateSplit#{p#descriptor/s}")
public class ForeignCallStateSplitNode extends ForeignCallNode implements LIRLowerable, StateSplit, DeoptimizingNode {

    @Input(notDataflow = true) private FrameState stateAfter;
    private MetaAccessProvider runtime;

    public ForeignCallStateSplitNode(MetaAccessProvider runtime, ForeignCallDescriptor descriptor, ValueNode... arguments) {
        super(descriptor, arguments);
        this.runtime = runtime;
    }

    public FrameState stateAfter() {
        return stateAfter;
    }

    public void setStateAfter(FrameState x) {
        assert x == null || x.isAlive() : "frame state must be in a graph";
        updateUsages(stateAfter, x);
        stateAfter = x;
    }

    public boolean hasSideEffect() {
        return runtime.hasSideEffect(getDescriptor());
    }

    @Override
    public FrameState getDeoptimizationState() {
        if (super.getDeoptimizationState() != null) {
            return super.getDeoptimizationState();
        } else if (stateAfter() != null) {
            FrameState stateDuring = stateAfter();
            if ((stateDuring.stackSize() > 0 && stateDuring.stackAt(stateDuring.stackSize() - 1) == this) || (stateDuring.stackSize() > 1 && stateDuring.stackAt(stateDuring.stackSize() - 2) == this)) {
                stateDuring = stateDuring.duplicateModified(stateDuring.bci, stateDuring.rethrowException(), this.kind());
            }
            setDeoptimizationState(stateDuring);
            return stateDuring;
        }
        return null;
    }

    @Override
    public void setDeoptimizationState(FrameState f) {
        if (super.getDeoptimizationState() != null) {
            throw new IllegalStateException();
        }
        super.setDeoptimizationState(f);
    }
}
