/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.nodes.extended;

import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_4;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_4;

import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodes.spi.Canonicalizable;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.LogicConstantNode;
import jdk.graal.compiler.nodes.LogicNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.spi.Lowerable;

import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Checks if the provided class is an array. This represents the operation {@link Class#isArray}.
 *
 * @see ObjectIsArrayNode
 */
@NodeInfo(cycles = CYCLES_4, size = SIZE_4)
public final class ClassIsArrayNode extends LogicNode implements Canonicalizable.Unary<ValueNode>, Lowerable {
    public static final NodeClass<ClassIsArrayNode> TYPE = NodeClass.create(ClassIsArrayNode.class);

    @Input protected ValueNode value;

    protected ClassIsArrayNode(ValueNode value) {
        super(TYPE);
        this.value = value;
    }

    public static LogicNode create(ConstantReflectionProvider constantReflection, ValueNode forValue) {
        return canonicalized(null, forValue, constantReflection);
    }

    @Override
    public ValueNode getValue() {
        return value;
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool, ValueNode forValue) {
        return canonicalized(this, forValue, tool.getConstantReflection());
    }

    private static LogicNode canonicalized(ClassIsArrayNode node, ValueNode forValue, ConstantReflectionProvider constantReflection) {
        if (forValue.isConstant()) {
            ResolvedJavaType type = constantReflection.asJavaType(forValue.asConstant());
            if (type != null) {
                return LogicConstantNode.forBoolean(type.isArray());
            }
        } else if (forValue instanceof GetClassNode) {
            return ObjectIsArrayNode.create(((GetClassNode) forValue).getObject());
        }
        return node != null ? node : new ClassIsArrayNode(forValue);
    }
}
