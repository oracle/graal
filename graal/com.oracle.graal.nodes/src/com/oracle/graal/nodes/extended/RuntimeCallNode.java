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

import com.oracle.graal.api.code.RuntimeCallTarget.Descriptor;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.extended.LocationNode.LocationIdentity;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.type.*;

@NodeInfo(nameTemplate = "RuntimeCall#{p#descriptor/s}")
public final class RuntimeCallNode extends AbstractCallNode implements LIRLowerable, DeoptimizingNode {

    private final Descriptor descriptor;
    @Input private FrameState deoptState;

    public RuntimeCallNode(Descriptor descriptor, ValueNode... arguments) {
        super(StampFactory.forKind(Kind.fromJavaClass(descriptor.getResultType())), arguments);
        this.descriptor = descriptor;
    }

    public Descriptor getDescriptor() {
        return descriptor;
    }

    @Override
    public boolean hasSideEffect() {
        return descriptor.hasSideEffect();
    }

    @Override
    public LocationIdentity[] getLocationIdentities() {
        return new LocationIdentity[]{LocationNode.ANY_LOCATION};
    }

    @Override
    public void generate(LIRGeneratorTool gen) {
        gen.visitRuntimeCall(this);
    }

    @Override
    public String toString(Verbosity verbosity) {
        if (verbosity == Verbosity.Name) {
            return super.toString(verbosity) + "#" + descriptor;
        }
        return super.toString(verbosity);
    }

    @Override
    public boolean canDeoptimize() {
        return true;
    }

    @Override
    public FrameState getDeoptimizationState() {
        if (deoptState != null) {
            return deoptState;
        } else if (stateAfter() != null) {
            FrameState stateDuring = stateAfter();
            if ((stateDuring.stackSize() > 0 && stateDuring.stackAt(stateDuring.stackSize() - 1) == this) || (stateDuring.stackSize() > 1 && stateDuring.stackAt(stateDuring.stackSize() - 2) == this)) {
                stateDuring = stateDuring.duplicateModified(stateDuring.bci, stateDuring.rethrowException(), this.kind());
            }
            updateUsages(deoptState, stateDuring);
            return deoptState = stateDuring;
        }
        return null;
    }

    @Override
    public void setDeoptimizationState(FrameState f) {
        if (deoptState != null) {
            throw new IllegalStateException();
        }
        updateUsages(deoptState, f);
        deoptState = f;
    }

    @Override
    public DeoptimizationReason getDeoptimizationReason() {
        return null;
    }

    @Override
    public boolean isCallSiteDeoptimization() {
        return stateAfter() != null;
    }
}
