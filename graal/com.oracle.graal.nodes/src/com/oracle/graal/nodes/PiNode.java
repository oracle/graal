/*
 * Copyright (c) 2012, 2012, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.api.meta.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.type.*;

/**
 * A node that changes the type of its input, usually narrowing it. For example, a PI node refines
 * the type of a receiver during type-guarded inlining to be the type tested by the guard.
 */
public class PiNode extends FloatingGuardedNode implements LIRLowerable, Virtualizable, Node.IterableNodeType, GuardingNode, Canonicalizable {

    @Input private ValueNode object;

    public ValueNode object() {
        return object;
    }

    public PiNode(ValueNode object, Stamp stamp) {
        super(stamp);
        this.object = object;
    }

    public PiNode(ValueNode object, Stamp stamp, GuardingNode anchor) {
        super(stamp, anchor);
        this.object = object;
    }

    @Override
    public void generate(LIRGeneratorTool generator) {
        if (object.kind() != Kind.Void && object.kind() != Kind.Illegal) {
            generator.setResult(this, generator.operand(object));
        }
    }

    @Override
    public boolean inferStamp() {
        return updateStamp(stamp().join(object().stamp()));
    }

    @Override
    public void virtualize(VirtualizerTool tool) {
        State state = tool.getObjectState(object);
        if (state != null && state.getState() == EscapeState.Virtual) {
            tool.replaceWithVirtual(state.getVirtualObject());
        }
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool) {
        if (getGuard() == graph().start()) {
            inferStamp();
            if (stamp().equals(object().stamp())) {
                return object();
            }
        }
        return this;
    }
}
