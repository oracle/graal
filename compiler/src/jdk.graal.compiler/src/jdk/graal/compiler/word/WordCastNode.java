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
package jdk.graal.compiler.word;

import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_1;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_1;

import jdk.graal.compiler.core.common.LIRKind;
import jdk.graal.compiler.core.common.type.AbstractPointerStamp;
import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.core.common.type.ObjectStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.lir.ConstantValue;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.spi.Canonicalizable;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.graal.compiler.nodes.spi.LIRLowerable;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;
import jdk.graal.compiler.nodes.type.NarrowOopStamp;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.Value;
import jdk.vm.ci.meta.ValueKind;

/**
 * Casts between Word and Object exposed by the {@link Word#fromAddress},
 * {@link Word#objectToTrackedPointer}, {@link Word#objectToUntrackedPointer} and
 * {@link Word#toObject()} operations. It has an impact on the pointer maps for the GC, so it must
 * not be scheduled or optimized away.
 */
@NodeInfo(cycles = CYCLES_1, size = SIZE_1)
public final class WordCastNode extends FixedWithNextNode implements LIRLowerable, Canonicalizable {

    public static final NodeClass<WordCastNode> TYPE = NodeClass.create(WordCastNode.class);

    @Input ValueNode input;
    private final boolean trackedPointer;

    public static WordCastNode wordToObject(ValueNode input, JavaKind wordKind) {
        assert input.getStackKind() == wordKind : Assertions.errorMessageContext("input", input, "inputKind", input.getStackKind());
        return new WordCastNode(objectStampFor(input), input);
    }

    public static WordCastNode wordToObjectNonNull(ValueNode input, JavaKind wordKind) {
        assert input.getStackKind() == wordKind : Assertions.errorMessageContext("input", input, "inputKind", input.getStackKind());
        return new WordCastNode(StampFactory.objectNonNull(), input);
    }

    public static WordCastNode wordToNarrowObject(ValueNode input, NarrowOopStamp stamp) {
        return new WordCastNode(stamp, input);
    }

    public static WordCastNode wordToTypedObject(ValueNode input, Stamp stamp) {
        return new WordCastNode(stamp, input);
    }

    public static WordCastNode addressToWord(ValueNode input, JavaKind wordKind) {
        Stamp stamp = input.stamp(NodeView.DEFAULT);
        assert stamp instanceof AbstractPointerStamp : stamp;
        return new WordCastNode(StampFactory.forKind(wordKind), input);
    }

    public static WordCastNode objectToTrackedPointer(ValueNode input, JavaKind wordKind) {
        Stamp stamp = input.stamp(NodeView.DEFAULT);
        assert stamp instanceof ObjectStamp : stamp;
        return new WordCastNode(StampFactory.forKind(wordKind), input);
    }

    public static WordCastNode objectToUntrackedPointer(ValueNode input, JavaKind wordKind) {
        Stamp stamp = input.stamp(NodeView.DEFAULT);
        assert stamp instanceof ObjectStamp : stamp;
        return new WordCastNode(StampFactory.forKind(wordKind), input, false);
    }

    public static WordCastNode narrowOopToUntrackedWord(ValueNode input, JavaKind wordKind) {
        Stamp stamp = input.stamp(NodeView.DEFAULT);
        assert stamp instanceof NarrowOopStamp : stamp;
        return new WordCastNode(StampFactory.forKind(wordKind), input, false);
    }

    private WordCastNode(Stamp stamp, ValueNode input) {
        this(stamp, input, true);
    }

    protected WordCastNode(Stamp stamp, ValueNode input, boolean trackedPointer) {
        super(TYPE, stamp);
        this.input = input;
        this.trackedPointer = trackedPointer;
    }

    public ValueNode getInput() {
        return input;
    }

    private static boolean isZeroConstant(ValueNode value) {
        JavaConstant constant = value.asJavaConstant();
        return constant.getJavaKind().isNumericInteger() && constant.asLong() == 0;
    }

    private static Stamp objectStampFor(ValueNode input) {
        Stamp inputStamp = input.stamp(NodeView.DEFAULT);
        if (inputStamp instanceof AbstractPointerStamp) {
            AbstractPointerStamp pointerStamp = (AbstractPointerStamp) inputStamp;
            if (pointerStamp.alwaysNull()) {
                return StampFactory.alwaysNull();
            } else if (pointerStamp.nonNull()) {
                return StampFactory.objectNonNull();
            }
        } else if (inputStamp instanceof IntegerStamp && !((IntegerStamp) inputStamp).contains(0)) {
            return StampFactory.objectNonNull();
        } else if (input.isConstant() && isZeroConstant(input)) {
            return StampFactory.alwaysNull();
        }
        return StampFactory.object();
    }

    @Override
    public boolean inferStamp() {
        if (stamp instanceof AbstractPointerStamp) {
            AbstractPointerStamp objectStamp = (AbstractPointerStamp) stamp;
            if (!objectStamp.alwaysNull() && !objectStamp.nonNull()) {
                Stamp newStamp = stamp;
                Stamp inputStamp = input.stamp(NodeView.DEFAULT);
                if (inputStamp instanceof AbstractPointerStamp) {
                    AbstractPointerStamp pointerStamp = (AbstractPointerStamp) inputStamp;
                    if (pointerStamp.alwaysNull()) {
                        newStamp = objectStamp.asAlwaysNull();
                    } else if (pointerStamp.nonNull()) {
                        newStamp = objectStamp.asNonNull();
                    }
                } else if (inputStamp instanceof IntegerStamp && !((IntegerStamp) inputStamp).contains(0)) {
                    newStamp = objectStamp.asNonNull();
                } else if (input.isConstant() && isZeroConstant(input)) {
                    newStamp = objectStamp.asAlwaysNull();
                }
                return updateStamp(newStamp);
            }
        }
        return false;
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        if (tool.allUsagesAvailable() && hasNoUsages()) {
            /* If the cast is unused, it can be eliminated. */
            return input;
        }

        assert !stamp.isCompatible(input.stamp(NodeView.DEFAULT));
        if (input.isJavaConstant()) {
            /* Null pointers are uncritical for GC, so they can be constant folded. */
            if (input.asJavaConstant().isNull()) {
                return ConstantNode.forIntegerStamp(stamp, 0);
            } else if (isZeroConstant(input)) {
                return ConstantNode.forConstant(stamp, ((AbstractPointerStamp) stamp).nullConstant(), tool.getMetaAccess());
            }
        }

        return this;
    }

    @Override
    public void generate(NodeLIRBuilderTool generator) {
        Value value = generator.operand(input);
        ValueKind<?> resultKind = generator.getLIRGeneratorTool().getLIRKind(stamp(NodeView.DEFAULT));
        assert resultKind.getPlatformKind().getSizeInBytes() == value.getPlatformKind().getSizeInBytes() : Assertions.errorMessageContext("resultKind", resultKind, "valueKind", value);

        if (trackedPointer && LIRKind.isValue(resultKind) && !LIRKind.isValue(value)) {
            // just change the PlatformKind, but don't drop reference information
            resultKind = value.getValueKind().changeType(resultKind.getPlatformKind());
        }

        if (resultKind.equals(value.getValueKind()) && !(value instanceof ConstantValue)) {
            generator.setResult(this, value);
        } else {
            AllocatableValue result = generator.getLIRGeneratorTool().newVariable(resultKind);
            if (stamp.equals(StampFactory.object())) {
                generator.getLIRGeneratorTool().emitConvertZeroToNull(result, value);
            } else if (!trackedPointer && !((AbstractPointerStamp) input.stamp(NodeView.DEFAULT)).nonNull() && !(input.stamp(NodeView.DEFAULT) instanceof NarrowOopStamp)) {
                generator.getLIRGeneratorTool().emitConvertNullToZero(result, (AllocatableValue) value);
            } else {
                result = generator.getLIRGeneratorTool().emitMove(resultKind, value);
            }
            generator.setResult(this, result);
        }
    }
}
