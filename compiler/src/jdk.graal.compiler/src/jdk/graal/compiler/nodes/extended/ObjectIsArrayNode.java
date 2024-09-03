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

import jdk.graal.compiler.core.common.type.AbstractObjectStamp;
import jdk.graal.compiler.core.common.type.ObjectStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.LogicConstantNode;
import jdk.graal.compiler.nodes.LogicNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.UnaryOpLogicNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.spi.Lowerable;
import jdk.graal.compiler.nodes.type.StampTool;

import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.TriState;

/**
 * Checks if the provided object is an array.
 *
 * Combining a {@link GetClassNode} and {@link ClassIsArrayNode} into a single
 * {@link ObjectIsArrayNode} allows better optimization of values that depend on the condition,
 * because {@link ObjectIsArrayNode} improves the stamp of its input value during conditional
 * elimination phases.
 */
@NodeInfo(cycles = CYCLES_4, size = SIZE_4)
public final class ObjectIsArrayNode extends UnaryOpLogicNode implements Lowerable {
    public static final NodeClass<ObjectIsArrayNode> TYPE = NodeClass.create(ObjectIsArrayNode.class);

    protected ObjectIsArrayNode(ValueNode object) {
        super(TYPE, object);
    }

    public static LogicNode create(ValueNode forValue) {
        return canonicalized(null, forValue);
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool, ValueNode forValue) {
        return canonicalized(this, forValue);
    }

    private static LogicNode canonicalized(ObjectIsArrayNode node, ValueNode forValue) {
        TriState triState = doTryFold(forValue.stamp(NodeView.DEFAULT));
        if (triState.isKnown()) {
            return LogicConstantNode.forBoolean(triState.toBoolean());
        }
        return node != null ? node : new ObjectIsArrayNode(forValue);
    }

    @Override
    public Stamp getSucceedingStampForValue(boolean negated) {
        if (negated) {
            return null;
        } else {
            // Ignore any more precise input stamp since canonicalization will skip through PiNodes
            // and also this node does not express non-nullness, the input has to provide that.
            return ((AbstractObjectStamp) StampFactory.object()).asAlwaysArray();
        }
    }

    @Override
    public TriState tryFold(Stamp valueStamp) {
        return doTryFold(valueStamp);
    }

    private static TriState doTryFold(Stamp valueStamp) {
        if (valueStamp instanceof ObjectStamp) {
            ObjectStamp objectStamp = (ObjectStamp) valueStamp;
            if (objectStamp.isAlwaysArray()) {
                return TriState.TRUE;
            }

            ResolvedJavaType type = StampTool.typeOrNull(objectStamp);
            if (type != null && !type.isJavaLangObject() && !type.isInterface()) {
                /*
                 * Also fold the negative case, when the type shows that the value is not an array.
                 * Note that arrays implement some interfaces, like Serializable. For simplicity, we
                 * exclude all interface types.
                 */
                assert !type.isArray() : "Positive case already covered by isAlwaysArray check above";
                return TriState.get(type.isArray());
            }
        }
        return TriState.UNKNOWN;
    }
}
