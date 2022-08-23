/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.replacements.amd64;

import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.lir.amd64.AMD64CalcStringAttributesOp;
import org.graalvm.compiler.nodeinfo.NodeCycles;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodeinfo.NodeSize;
import org.graalvm.compiler.nodes.spi.LoweringTool;
import org.graalvm.compiler.replacements.nodes.MacroNode;
import org.graalvm.word.LocationIdentity;

import jdk.vm.ci.amd64.AMD64;

// JaCoCo Exclude

/**
 * This intrinsic calculates properties of string contents in various encodings, see
 * {@link AMD64CalcStringAttributesOp} for details.
 *
 * @see AMD64CalcStringAttributesOp
 */
@NodeInfo(cycles = NodeCycles.CYCLES_UNKNOWN, size = NodeSize.SIZE_128)
public final class AMD64CalcStringAttributesMacroNode extends MacroNode {

    public static final NodeClass<AMD64CalcStringAttributesMacroNode> TYPE = NodeClass.create(AMD64CalcStringAttributesMacroNode.class);

    private final AMD64CalcStringAttributesOp.Op op;
    private final boolean assumeValid;
    private final LocationIdentity locationIdentity;

    public AMD64CalcStringAttributesMacroNode(MacroParams p, AMD64CalcStringAttributesOp.Op op, boolean assumeValid, LocationIdentity locationIdentity) {
        this(TYPE, p, op, assumeValid, locationIdentity);
    }

    public AMD64CalcStringAttributesMacroNode(NodeClass<? extends MacroNode> c, MacroParams p, AMD64CalcStringAttributesOp.Op op, boolean assumeValid, LocationIdentity locationIdentity) {
        super(c, p);
        this.op = op;
        this.assumeValid = assumeValid;
        this.locationIdentity = locationIdentity;
    }

    @Override
    public void lower(LoweringTool tool) {
        if (!((AMD64) tool.getLowerer().getTarget().arch).getFeatures().containsAll(AMD64CalcStringAttributesNode.minFeaturesAMD64())) {
            super.lower(tool);
        } else {
            AMD64CalcStringAttributesNode replacement = graph().addOrUnique(new AMD64CalcStringAttributesNode(getArgument(1), getArgument(2), getArgument(3), op, assumeValid, locationIdentity));
            graph().replaceFixedWithFixed(this, replacement);
        }
    }
}
