/*
 * Copyright (c) 2013, 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package org.graalvm.compiler.replacements.nodes;

import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_8;

import org.graalvm.compiler.core.common.type.ObjectStamp;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.core.common.type.StampPair;
import org.graalvm.compiler.graph.IterableNodeType;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.NodeCycles;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.FrameState;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.spi.Canonicalizable;
import org.graalvm.compiler.nodes.spi.CanonicalizerTool;

@NodeInfo(cycles = NodeCycles.CYCLES_UNKNOWN, cyclesRationale = "see rationale in MacroNode", size = SIZE_8)
public abstract class BasicObjectCloneNode extends MacroNode implements ObjectClone, IterableNodeType, Canonicalizable {

    public static final NodeClass<BasicObjectCloneNode> TYPE = NodeClass.create(BasicObjectCloneNode.class);

    @SuppressWarnings("this-escape")
    public BasicObjectCloneNode(NodeClass<? extends MacroNode> c, MacroParams p) {
        this(c, p, null);
    }

    protected BasicObjectCloneNode(NodeClass<? extends MacroNode> c, MacroParams p, FrameState stateAfter) {
        /*
         * A macro node's stamp must be in sync with the return stamp in its macro params.
         * Therefore, when we try to infer a better stamp from the argument, we must improve the
         * macro params themselves.
         */
        super(c, maybeImproveReturnStamp(p), stateAfter);
    }

    @Override
    public ValueNode getObject() {
        return arguments.get(0);
    }

    /**
     * If a stamp {@linkplain ObjectClone#computeStamp computed} from the given {@code object} is
     * strictly more precise than the given {@code currentStamp}, return the improved stamp. Return
     * {@code null} otherwise.
     */
    protected static ObjectStamp betterStamp(ValueNode object, Stamp currentStamp) {
        if (ObjectClone.computeStamp(object, currentStamp) instanceof ObjectStamp maybeBetterStamp && !maybeBetterStamp.equals(currentStamp) &&
                        maybeBetterStamp.join(currentStamp).equals(maybeBetterStamp)) {
            return maybeBetterStamp;
        }
        return null;
    }

    /**
     * Return a new copy of the given params {@code p} with a {@linkplain #betterStamp better stamp}
     * if possible; return {@code p} unchanged otherwise.
     */
    protected static MacroParams maybeImproveReturnStamp(MacroParams p) {
        ObjectStamp betterStamp = betterStamp(p.arguments[0], p.returnStamp.getTrustedStamp());
        if (betterStamp != null) {
            return MacroParams.of(p.invokeKind, p.callerMethod, p.targetMethod, p.bci, StampPair.createSingle(betterStamp), p.arguments);
        }
        return p;
    }

    @Override
    public BasicObjectCloneNode canonical(CanonicalizerTool tool) {
        ObjectStamp betterStamp = betterStamp(getObject(), stamp(NodeView.DEFAULT));
        if (betterStamp != null) {
            return duplicateWithNewStamp(betterStamp);
        }
        return this;
    }

    protected abstract BasicObjectCloneNode duplicateWithNewStamp(ObjectStamp newStamp);
}
