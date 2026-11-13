/*
 * Copyright (c) 2021, 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.phases.constantblinding;

import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_UNKNOWN;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_UNKNOWN;

import java.util.Map;

import jdk.graal.compiler.core.common.LIRKind;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.graph.NodeMap;
import jdk.graal.compiler.lir.ConstantValue;
import jdk.graal.compiler.lir.gen.ArithmeticLIRGeneratorTool;
import jdk.graal.compiler.lir.gen.LIRGeneratorTool;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodeinfo.Verbosity;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.calc.FloatingNode;
import jdk.graal.compiler.nodes.cfg.HIRBlock;
import jdk.graal.compiler.nodes.spi.ArithmeticLIRLowerable;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;
import jdk.graal.compiler.vector.nodes.simd.SimdConstant;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.PrimitiveConstant;
import jdk.vm.ci.meta.Value;

/**
 * Represents a constant encrypted with a secret key.
 */
@NodeInfo(nameTemplate = "BC({p#rawvalue}/{p#key}) {p#stampKind}", cycles = CYCLES_UNKNOWN, cyclesRationale = "cycles depend on the exact kind of blinded constant", size = SIZE_UNKNOWN)
public final class BlindedConstantNode extends FloatingNode implements ArithmeticLIRLowerable {

    public static final NodeClass<BlindedConstantNode> TYPE = NodeClass.create(BlindedConstantNode.class);

    /**
     * The unblinded (not encrypted) constant. This constant will be encrypted with the blinding
     * key.
     */
    private final Constant constantValue;

    /**
     * The blinding (encryption) key that will be used to blind the original constant value.
     */
    private final Constant blindingKey;

    /**
     * Creates a constant nodes representing the blinded version of {@code constantValue}.
     *
     * @param constantValue The constant value to be blinded. The constant must be either a
     *            {@link PrimitiveConstant} or a {@link SimdConstant} containing
     *            {@link PrimitiveConstant}s.
     * @param blindingKey The blinding key used to blind (encrypt) the constant. The blinding key
     *            must be of the same type as the {@code constantValue}. That is, if the
     *            {@code constantValue} is a {@link PrimitiveConstant}, the {@code blindingKey} must
     *            be a {@link PrimitiveConstant} too. If the {@code constantValue} is a
     *            {@link SimdConstant}, the {@code blindingKey} must contain
     *            {@link PrimitiveConstant} at exactly the same positions as the
     *            {@code constantValue}. Note that the {@code blindingKey} (or its component keys if
     *            it is a {@link SimdConstant}) should not have more bytes set (!= zero) than the
     *            {@code constantValue}. Otherwise, additional bytes might be introduced into the
     *            unencrypted value during the XOR operation.
     * @param stamp The stamp of the {@link BlindedConstantNode}. The {@link BlindedConstantNode}
     *            should have the same stamp as the original {@link ConstantNode}.
     */
    public BlindedConstantNode(Constant constantValue, Constant blindingKey, Stamp stamp) {
        super(TYPE, stamp);
        assert verifyConstant(constantValue);
        assert verifyBlindingKey(constantValue, blindingKey) : "Constant " + constantValue + " blindingKey " + blindingKey + " must verify";
        this.constantValue = constantValue;
        this.blindingKey = blindingKey;
    }

    public Constant getValue() {
        return constantValue;
    }

    @Override
    public Map<Object, Object> getDebugProperties(Map<Object, Object> map) {
        Map<Object, Object> properties = super.getDebugProperties(map);
        properties.put("rawvalue", constantValue.toValueString());
        properties.put("key", blindingKey);

        // Use the unrestricted stamp because the full stamp takes up too much space in graph
        // visualizer.
        properties.put("stampKind", stamp.unrestricted().toString());
        return properties;
    }

    @Override
    public String toString(Verbosity verbosity) {
        if (verbosity == Verbosity.Name) {
            return super.toString(Verbosity.Name) + "(" + constantValue.toValueString() + "/" + blindingKey + ", " + stamp(NodeView.DEFAULT).unrestricted().toString() + ")";
        } else {
            return super.toString(verbosity);
        }
    }

    private static boolean verifyConstant(Constant constant) {
        if (constant instanceof PrimitiveConstant) {
            return true;
        }

        assert constant instanceof SimdConstant : constant;
        SimdConstant simdConstant = (SimdConstant) constant;
        for (int i = 0; i < simdConstant.getVectorLength(); i++) {
            assert simdConstant.getValue(i) instanceof PrimitiveConstant : "The " + i + "th component of the constant value is not a primitive constant";
        }
        return true;
    }

    private static boolean verifyPrimitiveBlindingKey(Constant constant, Constant key) {
        assert key instanceof PrimitiveConstant : key;
        PrimitiveConstant primitiveConstant = (PrimitiveConstant) constant;
        PrimitiveConstant primitiveBlindingKey = (PrimitiveConstant) key;
        assert getNumberOfBytesInValue(primitiveConstant) >= getNumberOfBytesInValue(primitiveBlindingKey) : "The blinding key must not have more bytes set than the blinded constant";
        return true;
    }

    private static boolean verifyBlindingKey(Constant constant, Constant key) {
        if (constant instanceof PrimitiveConstant) {
            return verifyPrimitiveBlindingKey(constant, key);
        }

        assert constant instanceof SimdConstant && key instanceof SimdConstant : constant + " " + key;
        SimdConstant simdConstant = (SimdConstant) constant;
        SimdConstant simdBlindingKey = (SimdConstant) key;
        for (int i = 0; i < simdConstant.getVectorLength(); i++) {
            Constant keyComponent = simdBlindingKey.getValue(i);
            assert verifyPrimitiveBlindingKey(simdConstant.getValue(i), keyComponent);
        }
        return true;
    }

    @Override
    public void generate(NodeLIRBuilderTool builder, ArithmeticLIRGeneratorTool gen) {
        LIRGeneratorTool lirTool = builder.getLIRGeneratorTool();
        LIRKind kind = lirTool.getLIRKind(stamp(NodeView.DEFAULT));
        Constant blindedConstant = getBlindedConstant(constantValue, blindingKey);

        Value blindedConstantValue;
        Value keyConstantValue;

        if (lirTool.canInlineConstant(constantValue) || (lirTool.mayEmbedConstantLoad(constantValue) && hasExactlyOneUsage() && onlyUsedInCurrentBlock())) {
            blindedConstantValue = new ConstantValue(lirTool.toRegisterKind(kind), blindedConstant);
            keyConstantValue = new ConstantValue(lirTool.toRegisterKind(kind), blindingKey);
        } else {
            blindedConstantValue = builder.getLIRGeneratorTool().emitConstant(kind, blindedConstant);
            keyConstantValue = builder.getLIRGeneratorTool().emitConstant(kind, blindingKey);
        }

        Value result;
        if (stamp.getStackKind().isNumericFloat()) {
            result = gen.emitXorFP(blindedConstantValue, keyConstantValue);
        } else {
            result = gen.emitXor(blindedConstantValue, keyConstantValue);
        }

        builder.setResult(this, result);
    }

    private static Constant getBlindedConstant(Constant constantValue, Constant blindingKey) {
        Constant blindedConstant;
        if (constantValue instanceof PrimitiveConstant) {
            PrimitiveConstant primitiveConstantValue = (PrimitiveConstant) constantValue;
            PrimitiveConstant primitiveBlindingKey = (PrimitiveConstant) blindingKey;
            blindedConstant = xorConstants(primitiveConstantValue, primitiveBlindingKey);
        } else {
            assert constantValue instanceof SimdConstant : constantValue;
            SimdConstant simdConstantValue = (SimdConstant) constantValue;
            SimdConstant simdBlindingKey = (SimdConstant) blindingKey;
            Constant[] blindedValues = new Constant[simdConstantValue.getVectorLength()];
            for (int i = 0; i < simdConstantValue.getVectorLength(); i++) {
                Constant value = simdConstantValue.getValue(i);
                Constant valueBlindingKey = simdBlindingKey.getValue(i);
                assert value instanceof PrimitiveConstant : value;
                PrimitiveConstant primitiveBlindingKey = (PrimitiveConstant) valueBlindingKey;
                blindedValues[i] = xorConstants((PrimitiveConstant) value, primitiveBlindingKey);
            }
            blindedConstant = new SimdConstant(blindedValues);
        }
        return blindedConstant;
    }

    private static JavaConstant xorConstants(PrimitiveConstant constantValue, PrimitiveConstant blindingKey) {
        return switch (constantValue.getJavaKind()) {
            case Boolean, Char, Byte, Short, Int -> JavaConstant.forIntegerKind(constantValue.getJavaKind(), rawXorIntSizedConstant(constantValue, blindingKey));
            case Float -> JavaConstant.forFloat(Float.intBitsToFloat(rawXorIntSizedConstant(constantValue, blindingKey)));
            case Long -> JavaConstant.forLong(rawXorLongSizedConstant(constantValue, blindingKey));
            case Double -> JavaConstant.forDouble(Double.longBitsToDouble(rawXorLongSizedConstant(constantValue, blindingKey)));
            default -> throw GraalError.shouldNotReachHereUnexpectedValue(constantValue.getJavaKind()); // ExcludeFromJacocoGeneratedReport
        };
    }

    private static int rawXorIntSizedConstant(PrimitiveConstant constant, PrimitiveConstant blindingKey) {
        return asUnsignedInteger(constant).asInt() ^ (int) asUnsignedInteger(blindingKey).asLong();
    }

    private static long rawXorLongSizedConstant(PrimitiveConstant constant, PrimitiveConstant blindingKey) {
        return asUnsignedInteger(constant).asLong() ^ asUnsignedInteger(blindingKey).asLong();
    }

    /**
     * Create a JavaConstant with an integer kind from the supplied {@code constant}. If the
     * supplied constant is a floating point number, the resulting integer constant represents the
     * bitwise representation of the floating point value.
     *
     * @param constant The constant to convert
     * @return An integer kind constant representing the supplied constant value
     */
    public static JavaConstant asUnsignedInteger(PrimitiveConstant constant) {
        return switch (constant.getJavaKind()) {
            case Boolean -> JavaConstant.forInt(Byte.toUnsignedInt(constant.asBoolean() ? (byte) 1 : 0));
            case Byte -> JavaConstant.forInt(Byte.toUnsignedInt((byte) constant.asBoxedPrimitive()));
            case Char -> JavaConstant.forInt(constant.asInt());
            case Short -> JavaConstant.forInt(Short.toUnsignedInt((short) constant.asBoxedPrimitive()));
            case Int -> JavaConstant.forInt(constant.asInt());
            case Float -> JavaConstant.forInt(Float.floatToRawIntBits(constant.asFloat()));
            case Double -> JavaConstant.forLong(Double.doubleToRawLongBits(constant.asDouble()));
            default -> JavaConstant.forLong(constant.asLong());
        };
    }

    /**
     * Determines the actual number of bytes used by the value in {@code constant}. For example, an
     * Integer containing the value 0xA would use 1 byte.
     *
     * @param constant The constant to check
     * @return The number of bytes used by the value of {@code constant}
     */
    public static int getNumberOfBytesInValue(PrimitiveConstant constant) {
        JavaConstant unsignedConstant = BlindedConstantNode.asUnsignedInteger(constant);
        int numBits;
        if (unsignedConstant.getJavaKind() == JavaKind.Int) {
            numBits = Integer.SIZE - Integer.numberOfLeadingZeros(unsignedConstant.asInt());
        } else {
            numBits = Long.SIZE - Long.numberOfLeadingZeros(unsignedConstant.asLong());
        }
        return numBits / 8 + ((numBits % 8 == 0) ? 0 : 1);
    }

    /**
     * Expecting false for loop invariant.
     */
    private boolean onlyUsedInCurrentBlock() {
        assert graph().getLastSchedule() != null;
        NodeMap<HIRBlock> nodeBlockMap = graph().getLastSchedule().getNodeToBlockMap();
        HIRBlock currentBlock = nodeBlockMap.get(this);
        for (Node usage : usages()) {
            if (currentBlock != nodeBlockMap.get(usage)) {
                return false;
            }
        }
        return true;
    }
}
