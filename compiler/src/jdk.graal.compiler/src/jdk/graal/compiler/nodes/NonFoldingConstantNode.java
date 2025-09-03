/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

package jdk.graal.compiler.nodes;

import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_0;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_1;

import java.util.Map;

import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.calc.FloatingNode;
import jdk.graal.compiler.nodes.spi.LIRLowerable;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.VMConstant;

/**
 * During compilation, a {@code NonFoldingConstantNode} acts like an opaque typed value. The
 * compiler knows nothing about the value of a {@code NonFoldingConstantNode} and assumes that its
 * stamp is the unrestricted stamp of the corresponding type. However, during LIR lowering, this
 * node acts like a constant that can be folded into instructions. The exact value of this node is
 * still unknown during code emission, a dummy value will be emitted and the correct value will be
 * patched in afterward. As a result, it is necessary that the input of this node is a
 * {@link VMConstant}.
 */
@NodeInfo(nameTemplate = "C({p#stampKind})", cycles = CYCLES_0, size = SIZE_1)
public class NonFoldingConstantNode extends FloatingNode implements LIRLowerable {

    public static final NodeClass<NonFoldingConstantNode> TYPE = NodeClass.create(NonFoldingConstantNode.class);

    private final JavaConstant value;

    protected NonFoldingConstantNode(JavaConstant value, Stamp stamp) {
        super(TYPE, stamp);
        GraalError.guarantee(value.getJavaKind() != JavaKind.Object && value instanceof VMConstant, "use ConstantNode instead: %s", value);
        this.value = value;
    }

    public static NonFoldingConstantNode create(JavaConstant value, StructuredGraph graph) {
        Stamp stamp = StampFactory.forConstant(value).unrestricted();
        return graph.addOrUniqueWithInputs(new NonFoldingConstantNode(value, stamp));
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        gen.setResult(this, gen.getLIRGeneratorTool().emitJavaConstant(value));
    }

    @Override
    public Map<Object, Object> getDebugProperties(Map<Object, Object> map) {
        Map<Object, Object> properties = super.getDebugProperties(map);
        properties.put("stampKind", stamp.toString());
        return properties;
    }
}
