/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.vector.replacements.vectorapi;

import org.graalvm.word.LocationIdentity;

import jdk.graal.compiler.core.common.calc.CanonicalCondition;
import jdk.graal.compiler.core.common.memory.BarrierType;
import jdk.graal.compiler.core.common.memory.MemoryOrderMode;
import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.core.common.type.ObjectStamp;
import jdk.graal.compiler.core.common.type.PrimitiveStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.NamedLocationIdentity;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.PiNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.NarrowNode;
import jdk.graal.compiler.nodes.calc.NotNode;
import jdk.graal.compiler.nodes.calc.SignExtendNode;
import jdk.graal.compiler.nodes.extended.MembarNode;
import jdk.graal.compiler.nodes.extended.PublishWritesNode;
import jdk.graal.compiler.nodes.java.LoadFieldNode;
import jdk.graal.compiler.nodes.java.NewArrayNode;
import jdk.graal.compiler.nodes.java.NewInstanceNode;
import jdk.graal.compiler.nodes.java.StoreFieldNode;
import jdk.graal.compiler.nodes.memory.ReadNode;
import jdk.graal.compiler.nodes.memory.WriteNode;
import jdk.graal.compiler.nodes.memory.address.AddressNode;
import jdk.graal.compiler.nodes.memory.address.IndexAddressNode;
import jdk.graal.compiler.nodes.memory.address.OffsetAddressNode;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.nodes.spi.ValueProxy;
import jdk.graal.compiler.vector.architecture.VectorArchitecture;
import jdk.graal.compiler.vector.architecture.VectorLoweringProvider;
import jdk.graal.compiler.vector.nodes.simd.LogicValueStamp;
import jdk.graal.compiler.vector.nodes.simd.SimdBlendWithLogicMaskNode;
import jdk.graal.compiler.vector.nodes.simd.SimdConstant;
import jdk.graal.compiler.vector.nodes.simd.SimdStamp;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Utilities related to unboxing Vector API values from constants or from memory.
 * <p/>
 *
 * All vector types ultimately derive from a common superclass {@code VectorPayload}, defined
 * essentially as:
 *
 * <pre>
 * class VectorPayload {
 *     final Object payload;
 * }
 * </pre>
 *
 * The derived classes don't add any fields, so that the {@code payload} field is the only field in
 * Vector API vector types. Each concrete vector object will instantiate the payload to a primitive
 * array. As Vector API types are explicitly documented as immutable value-based classes, that array
 * will never be modified. The unboxing methods in this class make use of these properties and will
 * therefore assume without adding guards that the payload is non-null, has the expected primitive
 * array type, and is stable.
 * <p/>
 *
 * Vector masks are represented in memory with a {@code boolean[]} payload, while in the graph we
 * represent them as {@link SimdStamp}s with {@link LogicValueStamp} elements. This class also
 * provides utilities to convert between these representations.
 */
public class VectorAPIBoxingUtils {

    /**
     * If the given {@code constant} is an instance of a Vector API vector class, read the contents
     * of its payload array and return it. Return {@code null} if the given constant is not a vector
     * payload constant. The returned value will often be a constant, but not always, because
     * constant folding for mask vectors is currently not implemented.
     */
    public static ValueNode tryReadSimdConstant(JavaConstant constant, CoreProviders providers) {
        MetaAccessProvider metaAccess = providers.getMetaAccess();
        ConstantReflectionProvider constantReflection = providers.getConstantReflection();
        ResolvedJavaType type = metaAccess.lookupJavaType(constant);
        if (type == null) {
            return null;
        }
        VectorAPIType vectorType = VectorAPIType.ofType(type, providers);
        if (vectorType == null) {
            return null;
        }
        ResolvedJavaField[] fields = type.getInstanceFields(true);
        GraalError.guarantee(fields.length == 1 && fields[0].getName().equals("payload"), "expected exactly one payload field in Vector API constant: %s", constant);
        ResolvedJavaField payloadField = fields[0];
        JavaConstant payloadConstant = constantReflection.readFieldValue(payloadField, constant);
        ResolvedJavaType payloadType = metaAccess.lookupJavaType(payloadConstant);
        GraalError.guarantee(payloadType.isArray() && payloadType.getElementalType().isPrimitive(), "expected primitive payload array: %s", payloadType);
        int length = constantReflection.readArrayLength(payloadConstant);
        JavaConstant[] elements = new JavaConstant[length];
        for (int i = 0; i < length; i++) {
            elements[i] = constantReflection.readArrayElement(payloadConstant, i);
        }
        ConstantNode constantVector = SimdConstant.constantNodeForConstants(elements);
        if (vectorType.isMask) {
            PrimitiveStamp elementStamp = VectorAPIUtils.primitiveStampForKind(vectorType.elementKind);
            if (!canConvertBooleansToLogic(vectorType, VectorAPIUtils.vectorArchitecture(providers))) {
                return null;
            }
            return booleansAsLogic(constantVector, elementStamp, VectorAPIUtils.vectorArchitecture(providers));
        }
        return constantVector;
    }

    /**
     * If the given {@code constant} is an instance of a Vector API shuffle class, read the contents
     * of its payload array and return it. Return {@code null} if the given constant is not a vector
     * shuffle constant.
     */
    public static int[] tryReadShuffleConstant(JavaConstant constant, CoreProviders providers) {
        MetaAccessProvider metaAccess = providers.getMetaAccess();
        ConstantReflectionProvider constantReflection = providers.getConstantReflection();
        ResolvedJavaType type = metaAccess.lookupJavaType(constant);
        VectorAPIType vectorType = VectorAPIType.ofType(type, providers);
        if (vectorType == null || !vectorType.isShuffle || vectorType.vectorLength == 1) {
            return null;
        }

        ResolvedJavaField[] fields = type.getInstanceFields(true);
        GraalError.guarantee(fields.length == 1 && fields[0].getName().equals("payload"), "expected exactly one payload field in Vector API constant: %s", constant);
        ResolvedJavaField payloadField = fields[0];
        JavaConstant payloadConstant = constantReflection.readFieldValue(payloadField, constant);
        ResolvedJavaType payloadType = metaAccess.lookupJavaType(payloadConstant);
        GraalError.guarantee(payloadType.isArray(), "must be an array %s", payloadType);
        int length = constantReflection.readArrayLength(payloadConstant);
        GraalError.guarantee(length == vectorType.vectorLength, "must have the same length, %s - %d", vectorType, length);
        GraalError.guarantee(payloadType.getElementalType().getJavaKind() == vectorType.payloadKind, "mismatched shuffle payload %s - %s", vectorType, payloadType);
        int[] elements = new int[length];
        for (int i = 0; i < length; i++) {
            elements[i] = (int) constantReflection.readArrayElement(payloadConstant, i).asLong();
        }
        return elements;
    }

    /**
     * Determine if the given object is a non-null instance of a concrete Vector API vector type
     * that we can unbox to a SIMD value. Such objects can occur as inputs to macro nodes via things
     * like non-final field reads.
     *
     * @return a type describing the unboxable object, or {@code null} if the value is not an
     *         unboxable Vector API object
     */
    static VectorAPIType asUnboxableVectorType(ValueNode value, CoreProviders providers) {
        /*
         * Unboxing an object involves placing fixed read nodes. Therefore the value must be fixed,
         * or it must be a pi with a fixed guard so we have a valid insertion position.
         */
        if (!(value instanceof FixedWithNextNode || (value instanceof ValueProxy pi && pi.getGuard() != null && pi.getGuard() instanceof FixedWithNextNode))) {
            return null;
        }
        /* Now check if this is a Vector API object we can do a SIMD read from. */
        if (value.stamp(NodeView.DEFAULT) instanceof ObjectStamp objectStamp && objectStamp.nonNull() && objectStamp.isExactType()) {
            ResolvedJavaType maybeVectorType = objectStamp.type();
            if (maybeVectorType != null) {
                VectorLoweringProvider vectorLowerer = (VectorLoweringProvider) providers.getLowerer();
                VectorAPIType vectorType = VectorAPIType.ofType(maybeVectorType, providers);
                if (vectorType != null && vectorType.vectorLength > 1) {
                    VectorArchitecture vectorArch = vectorLowerer.getVectorArchitecture();
                    Stamp elementStamp = vectorType.payloadStamp.getComponent(0);
                    if (vectorType.isMask) {
                        /*
                         * The mask is represented as booleans in memory, to unbox it to a logic
                         * vector we must be able to compare against zero.
                         */
                        if (!canConvertBooleansToLogic(vectorType, vectorArch)) {
                            return null;
                        }
                        if (vectorArch.getSupportedVectorComparisonLength(elementStamp, CanonicalCondition.EQ, vectorType.vectorLength) != vectorType.vectorLength) {
                            return null;
                        }
                    }
                    if (vectorArch.getSupportedVectorMoveLength(elementStamp, vectorType.vectorLength) == vectorType.vectorLength) {
                        return vectorType;
                    }
                }
            }
        }
        return null;
    }

    /**
     * Box the given value by allocating an appropriate Java instance and storing the payload of the
     * value into the {@code payload} field of the box instance. The allocation will be inserted
     * before {@code successor}.
     */
    static ValueNode boxVector(ResolvedJavaType boxType, FixedNode successor, ValueNode vector, CoreProviders providers) {
        VectorAPIType typeMeta = VectorAPIType.ofType(boxType, providers);
        GraalError.guarantee(typeMeta != null, "unexpected vector type %s", boxType);
        StructuredGraph graph = vector.graph();

        // Allocate the payload array
        ResolvedJavaType payloadElementType = providers.getMetaAccess().lookupJavaType(typeMeta.payloadKind.toJavaClass());
        NewArrayNode payloadArray = graph.add(new NewArrayNode(payloadElementType, ConstantNode.forInt(typeMeta.vectorLength, graph), false));
        graph.addBeforeFixed(successor, payloadArray);

        // Store the value into the payload array
        ValueNode payload;
        if (typeMeta.isMask) {
            payload = graph.addOrUniqueWithInputs(logicAsBooleans(vector, VectorAPIUtils.vectorArchitecture(providers)));
        } else {
            payload = vector;
        }
        AddressNode payloadArrayAddress = graph.addOrUnique(new IndexAddressNode(payloadArray, ConstantNode.forInt(0, graph), payloadElementType.getJavaKind()));
        WriteNode storeToPayload = graph.add(new WriteNode(payloadArrayAddress, LocationIdentity.INIT_LOCATION, payload, BarrierType.NONE, MemoryOrderMode.PLAIN));
        graph.addBeforeFixed(successor, storeToPayload);

        // Publish the allocated array
        graph.addBeforeFixed(successor, graph.add(MembarNode.forInitialization()));
        PublishWritesNode publishedPayloadArray = graph.add(new PublishWritesNode(payloadArray));
        graph.addBeforeFixed(successor, publishedPayloadArray);

        // Allocate the box instance, fillContents must be true because the field is an oop
        NewInstanceNode box = graph.add(new NewInstanceNode(boxType, true));
        graph.addBeforeFixed(successor, box);

        // Store the allocated payload array into the corresponding field of the box instance
        ResolvedJavaField[] boxFields = boxType.getInstanceFields(true);
        GraalError.guarantee(boxFields.length == 1, "unexpected field count in %s", boxType);
        ResolvedJavaField payloadField = boxFields[0];
        GraalError.guarantee(payloadField.getName().equals("payload"), "unexpected field %s %s in %s", payloadField.getDeclaringClass(), payloadField.getName(), boxType);
        StoreFieldNode storeToBox = graph.add(new StoreFieldNode(box, payloadField, publishedPayloadArray));
        graph.addBeforeFixed(successor, storeToBox);

        // Publish the allocated box instance
        graph.addBeforeFixed(successor, graph.add(MembarNode.forInitialization()));
        PublishWritesNode publishedBox = graph.add(new PublishWritesNode(box));
        graph.addBeforeFixed(successor, publishedBox);
        return publishedBox;
    }

    /**
     * Unbox the given value (which must have been checked to be {@linkplain #asUnboxableVectorType
     * unboxable}. Unboxing it means first loading the vector object's primitive array payload
     * field, then loading a SIMD value from the payload.
     */
    static ValueNode unboxObject(ValueNode value, CoreProviders providers) {
        VectorAPIType vectorType = asUnboxableVectorType(value, providers);
        GraalError.guarantee(vectorType != null, "caller should have ensured a valid vector type for %s", value);

        ObjectStamp objectStamp = (ObjectStamp) value.stamp(NodeView.DEFAULT);
        ResolvedJavaType type = objectStamp.type();
        ResolvedJavaField[] fields = type.getInstanceFields(true);
        GraalError.guarantee(fields.length == 1 && fields[0].getName().equals("payload"), "expected exactly one payload field in Vector API class %s: %s", type, fields);
        ResolvedJavaField payloadField = fields[0];
        FixedWithNextNode insertionPoint;
        if (value instanceof FixedWithNextNode fixed) {
            insertionPoint = fixed;
        } else {
            insertionPoint = (FixedWithNextNode) ((ValueProxy) value).getGuard();
        }
        StructuredGraph graph = value.graph();
        LoadFieldNode loadPayloadArray = graph.add(LoadFieldNode.create(value.graph().getAssumptions(), value, payloadField));
        graph.addAfterFixed(insertionPoint, loadPayloadArray);
        /* A Vector API payload object's payload array is always non-null. */
        ValueNode nonNullPayload = PiNode.create(loadPayloadArray, StampFactory.objectNonNull());
        int offset = providers.getMetaAccess().getArrayBaseOffset(vectorType.payloadKind);
        AddressNode address = graph.addOrUniqueWithInputs(new OffsetAddressNode(nonNullPayload, ConstantNode.forLong(offset)));
        LocationIdentity location = NamedLocationIdentity.getArrayLocation(vectorType.payloadKind);
        ReadNode read = graph.add(new ReadNode(address, location, vectorType.payloadStamp, BarrierType.NONE, MemoryOrderMode.PLAIN));
        graph.addAfterFixed(loadPayloadArray, read);
        ValueNode unboxedValue = read;

        if (vectorType.isMask) {
            PrimitiveStamp elementStamp = VectorAPIUtils.primitiveStampForKind(vectorType.elementKind);
            unboxedValue = graph.addOrUniqueWithInputs(booleansAsLogic(unboxedValue, elementStamp, VectorAPIUtils.vectorArchitecture(providers)));
        }

        return unboxedValue;
    }

    /**
     * Checks the preconditions for calling {@link #booleansAsLogic}.
     */
    public static boolean canConvertBooleansToLogic(VectorAPIType vectorType, VectorArchitecture vectorArchitecture) {
        GraalError.guarantee(vectorType.isMask, "converting booleans to logic only makes sense for mask types: %s", vectorType);
        boolean result = true;
        if (vectorArchitecture.logicVectorsAreBitmasks()) {
            int bitmaskBits = PrimitiveStamp.getBits(vectorType.stamp.getComponent(0));
            if (bitmaskBits > 8) {
                PrimitiveStamp wideMaskStamp = IntegerStamp.create(bitmaskBits);
                result = result &&
                                vectorArchitecture.getSupportedVectorConvertLength(wideMaskStamp, IntegerStamp.create(8), vectorType.vectorLength,
                                                IntegerStamp.OPS.getSignExtend()) == vectorType.vectorLength;
            }
        }
        result = result && vectorArchitecture.getSupportedVectorComparisonLength(vectorType.payloadStamp.getComponent(0), CanonicalCondition.EQ, vectorType.vectorLength) == vectorType.vectorLength;
        return result;
    }

    /**
     * Given a vector {@code booleans} with elements of type {@code i8[0-1]}, produce a
     * corresponding logic vector with elements of type {@code logic(elementStamp)} or a vector of
     * bitmasks. The returned nodes are not added to the graph. Users must call
     * {@link #canConvertBooleansToLogic} first to ensure that it is legal to call this method.
     */
    public static ValueNode booleansAsLogic(ValueNode booleans, PrimitiveStamp elementStamp, VectorArchitecture vectorArchitecture) {
        SimdStamp inputStamp = (SimdStamp) booleans.stamp(NodeView.DEFAULT);
        GraalError.guarantee(PrimitiveStamp.getBits(inputStamp.getComponent(0)) == 8, "expected boolean vector as input, got: %s", inputStamp);

        if (vectorArchitecture.logicVectorsAreBitmasks()) {
            ValueNode booleansAsBitmasks = VectorAPIUtils.isZero(booleans, vectorArchitecture);
            /* Widen to the appropriate integer bitmask, maybe reinterpret as logic(floatType). */
            ValueNode wideMasks;
            if (elementStamp.getBits() > 8) {
                wideMasks = SignExtendNode.create(booleansAsBitmasks, 8, elementStamp.getBits(), NodeView.DEFAULT);
            } else {
                wideMasks = booleansAsBitmasks;
            }
            /*
             * So far we have an "is zero" vector, but we want an "is nonzero" vector. Only negate
             * now, below any sign extension. This should allow more arithmetic optimizations.
             */
            return NotNode.create(wideMasks);
        } else {
            ValueNode booleansAsLogic = VectorAPIUtils.isNonzero(booleans, vectorArchitecture);
            if (elementStamp.getBits() > 8) {
                SimdStamp targetStamp = SimdStamp.broadcast(LogicValueStamp.UNRESTRICTED, inputStamp.getVectorLength());
                return SimdStamp.reinterpretMask(targetStamp, booleansAsLogic, NodeView.DEFAULT);
            } else {
                return booleansAsLogic;
            }
        }
    }

    /**
     * Checks the preconditions for calling {@link #logicAsBooleans}.
     */
    public static boolean canConvertLogicToBooleans(SimdStamp logicStamp, VectorArchitecture vectorArchitecture) {
        int maxLength = logicStamp.getVectorLength();
        if (vectorArchitecture.logicVectorsAreBitmasks()) {
            PrimitiveStamp elementStamp = (PrimitiveStamp) logicStamp.getComponent(0);
            if (elementStamp.getBits() > 8) {
                maxLength = vectorArchitecture.getSupportedVectorConvertLength(IntegerStamp.create(8), elementStamp, maxLength, IntegerStamp.OPS.getNarrow());
            }
        }
        maxLength = vectorArchitecture.getSupportedVectorBlendLength(IntegerStamp.create(8), maxLength);
        return maxLength == logicStamp.getVectorLength();
    }

    /**
     * Given a vector {@code logicVector} of logic values each of type {@code logic(T)} or a vector
     * of bitmasks, produce a corresponding boolean vector with elements of type {@code i8[0-1]}.
     * The returned nodes are not added to the graph. This is the inverse of
     * {@link #booleansAsLogic}. Users must call {@link #canConvertLogicToBooleans} first to ensure
     * that it is legal to call this method.
     */
    public static ValueNode logicAsBooleans(ValueNode logicVector, VectorArchitecture vectorArchitecture) {
        SimdStamp inputStamp = (SimdStamp) logicVector.stamp(NodeView.DEFAULT);

        ValueNode booleanLogic;
        if (vectorArchitecture.logicVectorsAreBitmasks()) {
            PrimitiveStamp elementStamp = (PrimitiveStamp) inputStamp.getComponent(0);
            /* Narrow the bitmask representation of something like i64 to i8. */
            if (elementStamp.getBits() > 8) {
                booleanLogic = NarrowNode.create(logicVector, elementStamp.getBits(), 8, NodeView.DEFAULT);
            } else {
                booleanLogic = logicVector;
            }
        } else {
            booleanLogic = logicVector;
        }
        /* Now given our vector of logic(i8) values, use it to select 0 or 1 booleans. */
        ValueNode zeros = SimdConstant.constantNodeForBroadcast(JavaConstant.forPrimitiveInt(8, 0), inputStamp.getVectorLength());
        ValueNode ones = SimdConstant.constantNodeForBroadcast(JavaConstant.forPrimitiveInt(8, 1), inputStamp.getVectorLength());
        return SimdBlendWithLogicMaskNode.create(zeros, ones, booleanLogic);
    }
}
