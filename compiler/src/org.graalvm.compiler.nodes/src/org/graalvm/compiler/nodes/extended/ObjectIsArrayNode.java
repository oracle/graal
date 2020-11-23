/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.nodes.extended;

import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_UNKNOWN;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_UNKNOWN;

import org.graalvm.compiler.core.common.type.AbstractObjectStamp;
import org.graalvm.compiler.core.common.type.ObjectStamp;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.spi.CanonicalizerTool;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.LogicConstantNode;
import org.graalvm.compiler.nodes.LogicNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.UnaryOpLogicNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.spi.Lowerable;
import org.graalvm.compiler.nodes.type.StampTool;

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
@NodeInfo(cycles = CYCLES_UNKNOWN, size = SIZE_UNKNOWN)
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
            AbstractObjectStamp pointerStamp = (AbstractObjectStamp) getValue().stamp(NodeView.DEFAULT).unrestricted();
            return pointerStamp.asAlwaysArray();
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
            if (type != null && !type.isJavaLangObject()) {
                /*
                 * Also fold the negative case, when the type shows that the value is not an array.
                 */
                assert !type.isArray() : "Positive case already covered by isAlwaysArray check above";
                return TriState.get(type.isArray());
            }
        }
        return TriState.UNKNOWN;
    }
}
