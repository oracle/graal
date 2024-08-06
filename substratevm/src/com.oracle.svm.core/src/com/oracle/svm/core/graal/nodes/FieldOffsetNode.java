/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.graal.nodes;

import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_0;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_1;

import com.oracle.svm.core.meta.SharedField;
import com.oracle.svm.core.util.VMError;

import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.FloatingNode;
import jdk.graal.compiler.nodes.calc.NarrowNode;
import jdk.graal.compiler.nodes.extended.FieldOffsetProvider;
import jdk.graal.compiler.nodes.spi.Canonicalizable;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.graal.compiler.nodes.spi.Lowerable;
import jdk.graal.compiler.nodes.spi.LoweringTool;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaField;

/**
 * A node that eventually replaces itself with a {@link ConstantNode} when the actual field offset
 * is available.
 */
@NodeInfo(cycles = CYCLES_0, size = SIZE_1)
public final class FieldOffsetNode extends FloatingNode implements FieldOffsetProvider, Canonicalizable, Lowerable {
    public static final NodeClass<FieldOffsetNode> TYPE = NodeClass.create(FieldOffsetNode.class);

    /*
     * Not marked as final because transplanting from the analysis to the hosted universe will
     * change the value, so we want to make it clear in the field declaration already that the value
     * changes over time.
     */
    private ResolvedJavaField field;

    public static ValueNode create(JavaKind kind, ResolvedJavaField field) {
        var fieldOffset = new FieldOffsetNode(field);
        switch (kind) {
            case Long:
                return fieldOffset;
            case Int:
                /*
                 * The Unsafe access nodes that consume the field offset always require the offset
                 * as a long value, so there must be SignExtendNode in between. Emitting a
                 * NarrowNode ensures that the SignExtendNode is canonicalized away.
                 */
                return NarrowNode.create(fieldOffset, kind.getBitCount(), NodeView.DEFAULT);
            default:
                throw GraalError.shouldNotReachHere("Unsupported kind: " + kind);
        }
    }

    protected FieldOffsetNode(ResolvedJavaField field) {
        super(TYPE, StampFactory.forInteger(JavaKind.Long, 0, Integer.MAX_VALUE));
        this.field = field;
    }

    @Override
    public ResolvedJavaField getField() {
        return field;
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        if (field instanceof SharedField sharedField) {
            long fieldOffset = sharedField.getLocation();
            if (fieldOffset <= 0) {
                throw VMError.shouldNotReachHere("No offset for field " + field);
            }
            return ConstantNode.forIntegerKind(stamp.getStackKind(), fieldOffset);
        }
        return this;
    }

    @Override
    public void lower(LoweringTool tool) {
        throw GraalError.shouldNotReachHere("Field offset must be available before first lowering: " + field); // ExcludeFromJacocoGeneratedReport
    }
}
