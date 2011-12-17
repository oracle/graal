/*
 * Copyright (c) 2009, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.max.graal.nodes;

import com.oracle.max.graal.graph.*;
import com.oracle.max.graal.nodes.spi.*;
import com.oracle.max.graal.nodes.type.*;

/**
 * The {@code AnchorNode} can be used a lower bound for a Guard. It can also be used as an upper bound if no other FixedNode can be used for that purpose.
 */
public final class AnchorNode extends FixedWithNextNode implements LIRLowerable, Canonicalizable {

    @Input(notDataflow = true) private final NodeInputList<GuardNode> guards = new NodeInputList<GuardNode>(this);

    public AnchorNode() {
        super(StampFactory.illegal());
    }

    public void addGuard(GuardNode x) {
        guards.add(x);
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        if (this.usages().size() == 0 && guards.size() == 0) {
            return next();
        }
        return this;
    }

    @Override
    public void generate(LIRGeneratorTool gen) {
        // TODO is it necessary to do something for the "guards" list here?
        // Currently, there is nothing to emit since anchors are only a structural element with no execution semantics.
    }
}
