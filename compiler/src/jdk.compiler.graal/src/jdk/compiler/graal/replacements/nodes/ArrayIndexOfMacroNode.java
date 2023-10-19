/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package jdk.compiler.graal.replacements.nodes;

import jdk.compiler.graal.core.common.Stride;
import jdk.compiler.graal.graph.NodeClass;
import jdk.compiler.graal.lir.gen.LIRGeneratorTool;
import jdk.compiler.graal.nodeinfo.NodeCycles;
import jdk.compiler.graal.nodeinfo.NodeInfo;
import jdk.compiler.graal.nodeinfo.NodeSize;
import jdk.compiler.graal.nodes.ComputeObjectAddressNode;
import jdk.compiler.graal.nodes.ConstantNode;
import jdk.compiler.graal.nodes.ValueNode;
import jdk.compiler.graal.nodes.spi.LoweringTool;
import org.graalvm.word.LocationIdentity;

import jdk.vm.ci.code.Architecture;
import jdk.vm.ci.meta.JavaKind;

// JaCoCo Exclude

/**
 * {@link MacroNode} for SVM image graphs to allow using {@link ArrayIndexOfNode} even when the
 * image baseline CPU feature set does not meet its requirements.
 */
@NodeInfo(cycles = NodeCycles.CYCLES_UNKNOWN, size = NodeSize.SIZE_128)
public final class ArrayIndexOfMacroNode extends MacroNode {

    public static final NodeClass<ArrayIndexOfMacroNode> TYPE = NodeClass.create(ArrayIndexOfMacroNode.class);

    private final Stride stride;
    private final LIRGeneratorTool.ArrayIndexOfVariant variant;

    private final LocationIdentity locationIdentity;

    public ArrayIndexOfMacroNode(MacroParams p, Stride stride, LIRGeneratorTool.ArrayIndexOfVariant variant, LocationIdentity locationIdentity) {
        this(TYPE, p, stride, variant, locationIdentity);
    }

    public ArrayIndexOfMacroNode(NodeClass<? extends MacroNode> c, MacroParams p, Stride stride, LIRGeneratorTool.ArrayIndexOfVariant variant,
                    LocationIdentity locationIdentity) {
        super(c, p);
        this.stride = stride;
        this.variant = variant;
        this.locationIdentity = locationIdentity;
    }

    @Override
    public void lower(LoweringTool tool) {
        Architecture arch = tool.getLowerer().getTarget().arch;
        if (ArrayIndexOfNode.isSupported(arch, stride, variant)) {
            // some arguments of the original method are unused in the intrinsic. original args:
            // 0: Node location
            // 1: array
            // 2: offset
            // 3: length
            // 4: stride
            // 5: isNative
            // 6: fromIndex
            // 7-11: values
            ValueNode[] searchValues = getArguments().subList(7).toArray(ValueNode[]::new);
            if (variant == LIRGeneratorTool.ArrayIndexOfVariant.Table) {
                ValueNode array = searchValues[0];
                ConstantNode offset = ConstantNode.forLong(tool.getMetaAccess().getArrayBaseOffset(JavaKind.Byte));
                assert offset.getStackKind() == tool.getReplacements().getWordKind();
                searchValues[0] = graph().add(new ComputeObjectAddressNode(array, graph().addOrUnique(offset)));
            }
            ArrayIndexOfNode replacement = graph().addOrUnique(new ArrayIndexOfNode(stride, variant, null, locationIdentity,
                            getArgument(1), // array
                            getArgument(2), // offset
                            getArgument(3), // length
                            getArgument(6), // fromIndex
                            searchValues // values
            ));
            graph().replaceFixedWithFixed(this, replacement);
            if (variant == LIRGeneratorTool.ArrayIndexOfVariant.Table) {
                graph().addBeforeFixed(replacement, (ComputeObjectAddressNode) searchValues[0]);
            }
        } else {
            super.lower(tool);
        }
    }

}
