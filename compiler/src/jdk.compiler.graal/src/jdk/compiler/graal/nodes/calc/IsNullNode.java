/*
 * Copyright (c) 2009, 2021, Oracle and/or its affiliates. All rights reserved.
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
package jdk.compiler.graal.nodes.calc;

import jdk.compiler.graal.core.common.type.AbstractPointerStamp;
import jdk.compiler.graal.core.common.type.ObjectStamp;
import jdk.compiler.graal.core.common.type.Stamp;
import jdk.compiler.graal.core.common.util.CompilationAlarm;
import jdk.compiler.graal.graph.NodeClass;
import jdk.compiler.graal.nodeinfo.NodeCycles;
import jdk.compiler.graal.nodeinfo.NodeInfo;
import jdk.compiler.graal.nodes.CompressionNode;
import jdk.compiler.graal.nodes.LogicConstantNode;
import jdk.compiler.graal.nodes.LogicNode;
import jdk.compiler.graal.nodes.NodeView;
import jdk.compiler.graal.nodes.StructuredGraph;
import jdk.compiler.graal.nodes.UnaryOpLogicNode;
import jdk.compiler.graal.nodes.ValueNode;
import jdk.compiler.graal.nodes.spi.CanonicalizerTool;
import jdk.compiler.graal.nodes.spi.LIRLowerable;
import jdk.compiler.graal.nodes.spi.NodeLIRBuilderTool;
import jdk.compiler.graal.nodes.type.NarrowOopStamp;
import jdk.compiler.graal.nodes.type.StampTool;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.TriState;

/**
 * An IsNullNode will be true if the supplied value is null, and false if it is non-null.
 */
@NodeInfo(cycles = NodeCycles.CYCLES_2)
public final class IsNullNode extends UnaryOpLogicNode implements LIRLowerable {

    public static final NodeClass<IsNullNode> TYPE = NodeClass.create(IsNullNode.class);

    /*
     * When linear pointer compression is enabled, compressed and uncompressed nulls differ.
     */
    private final JavaConstant nullConstant;

    private IsNullNode(ValueNode object, JavaConstant nullConstant) {
        super(TYPE, object);
        this.nullConstant = nullConstant;
        assert object != null;
    }

    public IsNullNode(ValueNode object) {
        this(object, JavaConstant.NULL_POINTER);
        assertNonNarrow(object);
    }

    public JavaConstant nullConstant() {
        return nullConstant;
    }

    public static LogicNode create(ValueNode forValue) {
        assertNonNarrow(forValue);
        return canonicalized(null, forValue, JavaConstant.NULL_POINTER);
    }

    public static LogicNode create(ValueNode forValue, JavaConstant nullConstant) {
        assert nullConstant.isNull() : "Null constant is not null: " + nullConstant;
        return canonicalized(null, forValue, nullConstant);
    }

    private static void assertNonNarrow(ValueNode object) {
        assert !(object.stamp(NodeView.DEFAULT) instanceof NarrowOopStamp) : "Value to compare against null is a NarrowOop" + object;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        // Nothing to do.
    }

    @Override
    public boolean verify() {
        assertTrue(getValue() != null, "is null input must not be null");
        assertTrue(getValue().stamp(NodeView.DEFAULT).isPointerStamp(), "input must be a pointer not %s", getValue().stamp(NodeView.DEFAULT));
        return super.verify();
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool, ValueNode forValue) {
        return canonicalized(this, forValue, nullConstant);
    }

    private static LogicNode canonicalized(IsNullNode node, ValueNode forValue, JavaConstant forNullConstant) {
        JavaConstant nullConstant = forNullConstant;
        ValueNode value = forValue;

        StructuredGraph graph = null;
        if (node != null && node.graph() != null) {
            graph = node.graph();
        } else if (value != null && value.graph() != null) {
            graph = value.graph();
        }

        while (true) { // TERMINATION ARGUMENT: unwrapping compression nodes until exit condition is
                       // met.
            CompilationAlarm.checkProgress(graph);
            if (value != null) {
                if (StampTool.isPointerAlwaysNull(value)) {
                    return LogicConstantNode.tautology();
                } else if (StampTool.isPointerNonNull(value)) {
                    return LogicConstantNode.contradiction();
                }
            }

            if (value instanceof ConvertNode) {
                ConvertNode convertNode = (ConvertNode) value;
                if (convertNode.mayNullCheckSkipConversion()) {
                    value = convertNode.getValue();
                    continue;
                }
                /*
                 * CompressionNode.mayNullCheckSkipConversion returns false when linear pointer
                 * compression is enabled.
                 */
                if (value instanceof CompressionNode) {
                    CompressionNode compressionNode = (CompressionNode) value;
                    nullConstant = compressionNode.nullConstant();
                    value = compressionNode.getValue();
                    continue;
                }
            }
            /*
             * If we are at original node, just return it. Otherwise create a new node.
             */
            return (node != null && value == forValue) ? node : new IsNullNode(value, nullConstant);
        }
    }

    @Override
    public Stamp getSucceedingStampForValue(boolean negated) {
        // Ignore any more precise input stamp since canonicalization will skip through PiNodes
        AbstractPointerStamp pointerStamp = (AbstractPointerStamp) getValue().stamp(NodeView.DEFAULT).unrestricted();
        return negated ? pointerStamp.asNonNull() : pointerStamp.asAlwaysNull();
    }

    @Override
    public TriState tryFold(Stamp valueStamp) {
        if (valueStamp instanceof ObjectStamp) {
            ObjectStamp objectStamp = (ObjectStamp) valueStamp;
            if (objectStamp.alwaysNull()) {
                return TriState.TRUE;
            } else if (objectStamp.nonNull()) {
                return TriState.FALSE;
            }
        }
        return TriState.UNKNOWN;
    }
}
