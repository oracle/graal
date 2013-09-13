/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.nodes;

import com.oracle.graal.api.code.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.type.*;

/**
 * Nodes of this type are inserted into the graph to denote points of interest to debugging.
 */
public class InfopointNode extends AbstractStateSplit implements LIRLowerable, IterableNodeType {

    public final InfopointReason reason;

    public InfopointNode(InfopointReason reason) {
        super(StampFactory.forVoid());
        this.reason = reason;
    }

    @Override
    public void generate(LIRGeneratorTool generator) {
        generator.visitInfopointNode(this);
    }

    @Override
    public boolean hasSideEffect() {
        return false;
    }

    @Override
    public void setStateAfter(FrameState state) {
        // shield this node from frame state removal
        // TODO turn InfopointNode into a FixedWithNextNode subclass with a self-maintained
        // FrameState that is correctly dealt with by scheduling and partial escape analysis
        if (state != null) {
            super.setStateAfter(state);
        }
    }

    @Override
    public boolean verify() {
        return stateAfter() != null && super.verify();
    }

}
